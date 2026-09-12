"""Unified chatbot prompt, retrieval and technical error tests."""

import asyncio
import threading
from datetime import datetime, timezone

import pytest

from app.prompts import chatbot_prompt
from app.providers.llm_provider import LlmProviderError
from app.providers.web_search_provider import NullWebSearchProvider, WebSearchResult
from app.rag.settings_chatbot import ChatbotSettings, WebSearchSettings
from app.schemas.chatbot import ChatbotAnswerRequest, INSUFFICIENT_EVIDENCE_ANSWER
from app.services.chatbot_answer_service import ChatbotAnswerError, ChatbotAnswerService


SOURCE_AT = datetime(2026, 7, 25, 7, 0, tzinfo=timezone.utc)
PROFILE_ROW = ("래미안 옥수 리버젠", "서울 성동구 독서당로 175", "성동구", "옥수동", 1976, "2012-12", 2500)


class RecordingLlm:
    def __init__(self, payload=None, error=None):
        self.payload = payload or {"answer": "옥수역 도보 8분입니다.", "usedSources": [1]}
        self.error = error
        self.call_count = 0
        self.last_system = ""
        self.last_user = ""

    async def complete_json(self, system_prompt, user_prompt):
        self.call_count += 1
        self.last_system = system_prompt
        self.last_user = user_prompt
        if self.error:
            raise self.error
        return self.payload


class FakeEmbedder:
    def encode_query(self, question):
        return [0.1]


class FakeCursor:
    def __init__(self, report_rows, profile_row):
        self.report_rows = report_rows
        self.profile_row = profile_row
        self.sql = ""
        self.params = {}

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return None

    def execute(self, sql, params):
        self.sql = sql
        self.params = params

    def fetchall(self):
        return self.report_rows

    def fetchone(self):
        return self.profile_row


class FakeConnection:
    def __init__(self, report_rows, profile_row=PROFILE_ROW):
        self.report_rows = report_rows
        self.profile_row = profile_row
        self.cursors = []
        self.closed = False

    def cursor(self):
        cursor = FakeCursor(self.report_rows, self.profile_row)
        self.cursors.append(cursor)
        return cursor

    def close(self):
        self.closed = True


def _request(**overrides):
    payload = {"apartmentId": 15, "question": "교통 어때요?", "apartmentName": "래미안 옥수 리버젠"}
    payload.update(overrides)
    return ChatbotAnswerRequest.model_validate(payload)


def _service(rows, llm=None, profile_row=PROFILE_ROW):
    llm = llm or RecordingLlm()
    connections = []

    def connection_factory():
        connection = FakeConnection(rows, profile_row)
        connections.append(connection)
        return connection

    service = ChatbotAnswerService(
        provider=llm,
        embedder=FakeEmbedder(),
        connection_factory=connection_factory,
        settings=ChatbotSettings(),
        web_search_provider=NullWebSearchProvider(),
        web_settings=WebSearchSettings(),
    )
    return service, llm, connections


@pytest.mark.asyncio
async def test_profile_exists_and_report_search_use_closed_connections() -> None:
    row = (48, "교통 근거", SOURCE_AT, 0.72)
    service, _, connections = _service([row])
    response = await service.answer(_request())
    assert response.basisType == "REPORT"
    assert len(connections) == 2
    assert all(connection.closed for connection in connections)
    assert [len(connection.cursors) for connection in connections] == [2, 1]
    assert connections[1].cursors[0].params["top_k"] == 60


@pytest.mark.asyncio
async def test_web_query_uses_profile_region_and_name() -> None:
    queries = []

    class WebProvider:
        async def search(self, query, top_k):
            queries.append(query)
            return []

    service, _, _ = _service([])
    service.web_search_provider = WebProvider()
    await service.answer(_request(question="가장 가까운 학교"))
    assert queries == ["래미안 옥수 리버젠 성동구 옥수동 가장 가까운 학교"]


@pytest.mark.asyncio
async def test_profile_finishes_before_web_search_starts() -> None:
    calls = []
    service, _, _ = _service([])

    def fetch_profile(request):
        calls.append("profile")
        return None, False

    async def search_web(query):
        calls.append("web")
        return []

    service._fetch_profile = fetch_profile
    service._retrieve = lambda request: []
    service._search_web = search_web
    await service.answer(_request())
    assert calls == ["profile", "web"]


@pytest.mark.asyncio
async def test_report_and_web_search_remain_concurrent() -> None:
    report_started = threading.Event()
    web_started = threading.Event()
    service, _, _ = _service([])
    service.settings = ChatbotSettings(lazy_web_enabled=False)
    service._fetch_profile = lambda request: (None, False)

    def retrieve(request):
        report_started.set()
        assert web_started.wait(timeout=1)
        return []

    async def search_web(query):
        assert await asyncio.to_thread(report_started.wait, 1)
        web_started.set()
        return []

    service._retrieve = retrieve
    service._search_web = search_web
    await asyncio.wait_for(service.answer(_request()), timeout=2)
    assert report_started.is_set() and web_started.is_set()


@pytest.mark.asyncio
async def test_missing_profile_uses_request_name_without_region() -> None:
    queries = []

    class WebProvider:
        async def search(self, query, top_k):
            queries.append(query)
            return []

    service, _, _ = _service([], profile_row=None)
    service.web_search_provider = WebProvider()
    await service.answer(_request(question="학교"))
    assert queries == ["래미안 옥수 리버젠 학교"]


@pytest.mark.asyncio
async def test_missing_profile_and_evidence_keeps_insufficient_response() -> None:
    service, llm, _ = _service([], profile_row=None)
    response = await service.answer(_request())
    assert response.answer == INSUFFICIENT_EVIDENCE_ANSWER
    assert response.basisType == "NONE"
    assert llm.call_count == 0


@pytest.mark.asyncio
async def test_web_search_failure_is_absorbed() -> None:
    class BrokenWebProvider:
        async def search(self, query, top_k):
            raise RuntimeError("quota")

    service, _, _ = _service([(48, "근거", SOURCE_AT, 0.72)])
    service.web_search_provider = BrokenWebProvider()
    response = await service.answer(_request())
    assert response.basisType == "REPORT"


@pytest.mark.asyncio
async def test_both_disabled_matches_context_slice_behavior() -> None:
    queries = []

    class RewriteProvider:
        def __init__(self):
            self.call_count = 0

        async def complete_json(self, system_prompt, user_prompt):
            self.call_count += 1
            return {"query": "rewritten"}

    class WebProvider:
        async def search(self, query, top_k):
            queries.append(query)
            return []

    rewrite = RewriteProvider()
    service, _, _ = _service([])
    service.rewrite_provider = rewrite
    service.web_search_provider = WebProvider()
    service.settings = ChatbotSettings(
        search_rewrite_enabled=False,
        web_result_filter_enabled=False,
    )
    await service.answer(_request(question="학교"))
    assert rewrite.call_count == 0
    assert queries == ["래미안 옥수 리버젠 성동구 옥수동 학교"]


@pytest.mark.asyncio
async def test_vector_search_and_rewrite_pipeline_run_concurrently() -> None:
    report_started = threading.Event()
    rewrite_started = threading.Event()
    service, _, _ = _service([])

    class RewriteProvider:
        async def complete_json(self, system_prompt, user_prompt):
            assert await asyncio.to_thread(report_started.wait, 1)
            rewrite_started.set()
            return {"query": "성동구 옥수동 학교"}

    def retrieve(request):
        report_started.set()
        assert rewrite_started.wait(timeout=1)
        return []

    service.rewrite_provider = RewriteProvider()
    service._retrieve = retrieve
    service.settings = ChatbotSettings(lazy_web_enabled=False)
    await asyncio.wait_for(service.answer(_request()), timeout=2)
    assert report_started.is_set() and rewrite_started.is_set()


def test_lazy_web_environment_toggle_can_restore_eager_mode(monkeypatch) -> None:
    monkeypatch.setenv("CHATBOT_LAZY_WEB_ENABLED", "false")

    assert ChatbotSettings.from_env().lazy_web_enabled is False


@pytest.mark.asyncio
async def test_lazy_toggle_keeps_response_fields_identical() -> None:
    row = (48, "report evidence", SOURCE_AT, 0.72)
    eager_service, _, _ = _service([row])
    eager_service.settings = ChatbotSettings(lazy_web_enabled=False)
    lazy_service, _, _ = _service([row])

    eager = await eager_service.answer(_request())
    lazy = await lazy_service.answer(_request())

    assert eager.model_dump().keys() == lazy.model_dump().keys()
    assert eager.model_dump() == lazy.model_dump()


@pytest.mark.asyncio
async def test_filtered_web_source_metadata_matches_used_result() -> None:
    relevant = WebSearchResult(
        "옥수동 학교",
        "성동구 학군",
        "https://example.com/relevant",
        "example.com",
        SOURCE_AT,
    )
    unrelated = WebSearchResult(
        "무관한 문서",
        "다른 지역",
        "https://example.com/unrelated",
        "example.com",
        SOURCE_AT,
    )

    class RewriteProvider:
        async def complete_json(self, system_prompt, user_prompt):
            return {"query": "성동구 옥수동 학교"}

    class WebProvider:
        async def search(self, query, top_k):
            return [unrelated, relevant]

    llm = RecordingLlm({"answer": "학교 답변", "usedSources": [1]})
    service, _, _ = _service([], llm=llm)
    service.rewrite_provider = RewriteProvider()
    service.web_search_provider = WebProvider()
    response = await service.answer(_request(question="가장 가까운 학교"))
    assert response.basisType == "WEB"
    assert response.sources[0].title == relevant.title
    assert response.sources[0].url == relevant.url
    assert response.sources[0].sourceAt == SOURCE_AT.isoformat()


@pytest.mark.asyncio
async def test_prompt_uses_single_system_prompt_and_numbered_types() -> None:
    web = WebSearchResult("<b>웹 제목</b>", "<b>웹</b> 요약", "https://example.com", "example.com", SOURCE_AT)

    class WebProvider:
        async def search(self, query, top_k):
            return [web]

    llm = RecordingLlm({"answer": "답변", "usedSources": [1]})
    service, _, _ = _service([(48, "리포트 근거", SOURCE_AT, 0.72)], llm=llm)
    service.web_search_provider = WebProvider()
    service.settings = ChatbotSettings(lazy_web_enabled=False)
    await service.answer(_request())
    assert llm.last_system == chatbot_prompt.SYSTEM_PROMPT
    assert "(임장 리포트)" in llm.last_user
    assert "[근거 2] (웹)" in llm.last_user
    assert "<b>" not in llm.last_user


def test_prompt_has_no_sentence_lower_bound_or_web_prompt() -> None:
    assert "3~5문장" not in chatbot_prompt.SYSTEM_PROMPT
    assert "함께 인용하지 않습니다" in chatbot_prompt.SYSTEM_PROMPT
    assert not hasattr(chatbot_prompt, "WEB_SYSTEM_PROMPT")


@pytest.mark.asyncio
async def test_provider_failure_becomes_provider_failed() -> None:
    llm = RecordingLlm(error=LlmProviderError("timeout"))
    service, _, _ = _service([(48, "근거", SOURCE_AT, 0.72)], llm=llm)
    with pytest.raises(ChatbotAnswerError) as exc:
        await service.answer(_request())
    assert exc.value.code == "PROVIDER_FAILED"


@pytest.mark.asyncio
async def test_offschema_or_blank_output_is_schema_validation_failed() -> None:
    for payload in ({"reply": "wrong"}, {"answer": "   ", "usedSources": []}):
        service, _, _ = _service([(48, "근거", SOURCE_AT, 0.72)], RecordingLlm(payload))
        with pytest.raises(ChatbotAnswerError) as exc:
            await service.answer(_request())
        assert exc.value.code == "SCHEMA_VALIDATION_FAILED"


@pytest.mark.asyncio
async def test_embedding_failure_becomes_embedding_failed() -> None:
    class BrokenEmbedder:
        def encode_query(self, question):
            raise RuntimeError("model unavailable")

    service, _, _ = _service([])
    service.embedder = BrokenEmbedder()
    with pytest.raises(ChatbotAnswerError) as exc:
        await service.answer(_request())
    assert exc.value.code == "EMBEDDING_FAILED"


@pytest.mark.asyncio
async def test_database_failure_becomes_rag_db_failed() -> None:
    service, _, _ = _service([])
    service.connection_factory = lambda: (_ for _ in ()).throw(RuntimeError("refused"))
    with pytest.raises(ChatbotAnswerError) as exc:
        await service.answer(_request())
    assert exc.value.code == "RAG_DB_FAILED"
