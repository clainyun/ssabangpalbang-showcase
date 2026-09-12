"""Lazy web-search orchestration tests for chatbot slice C."""

import asyncio
from datetime import datetime, timezone

import pytest

from app.providers.llm_provider import LlmProviderError
from app.providers.web_search_provider import WebSearchResult
from app.rag.search import ApartmentProfile, ReportChunkHit
from app.rag.settings_chatbot import ChatbotSettings, WebSearchSettings
from app.schemas.chatbot import ChatbotAnswerRequest, INSUFFICIENT_EVIDENCE_ANSWER
from app.services.chatbot_answer_service import ChatbotAnswerError, ChatbotAnswerService


SOURCE_AT = datetime(2026, 8, 12, 3, 0, tzinfo=timezone.utc)
PROFILE = ApartmentProfile(
    name="Tower Palace 1",
    address="Seoul Gangnam-gu Dogok-dong",
    district_name="Gangnam-gu",
    dong_name="Dogok-dong",
    household_count=1297,
    completion_year_month="2002-10",
    parking_space_count=2535,
)
PROFILE_ROW = (
    PROFILE.name,
    PROFILE.address,
    PROFILE.district_name,
    PROFILE.dong_name,
    PROFILE.household_count,
    PROFILE.completion_year_month,
    PROFILE.parking_space_count,
)
HIT = ReportChunkHit(41, "report evidence", SOURCE_AT, 0.81)
WEB = WebSearchResult(
    "Tower Palace redevelopment",
    "Gangnam-gu Dogok-dong redevelopment information",
    "https://example.com/redevelopment",
    "example.com",
    SOURCE_AT,
)


class SequenceAnswerProvider:
    def __init__(self, *outcomes: dict | Exception) -> None:
        self.outcomes = list(outcomes)
        self.call_count = 0

    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict:
        outcome = self.outcomes[self.call_count]
        self.call_count += 1
        if isinstance(outcome, Exception):
            raise outcome
        return outcome


class RecordingRewriteProvider:
    def __init__(self) -> None:
        self.call_count = 0

    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict:
        self.call_count += 1
        return {"query": "Gangnam-gu Dogok-dong redevelopment"}


class RecordingWebProvider:
    def __init__(self, results: list[WebSearchResult]) -> None:
        self.results = results
        self.call_count = 0

    async def search(self, query: str, top_k: int) -> list[WebSearchResult]:
        self.call_count += 1
        return self.results


class CountingEmbedder:
    def __init__(self) -> None:
        self.call_count = 0

    def encode_query(self, question: str) -> list[float]:
        self.call_count += 1
        return [0.1]


def _request() -> ChatbotAnswerRequest:
    return ChatbotAnswerRequest(
        apartmentId=221,
        apartmentName=PROFILE.name,
        question="What redevelopment plans are there?",
    )


def _service(
    outcomes: tuple[dict | Exception, ...],
    *,
    has_reports: bool,
    hits: list[ReportChunkHit] | None = None,
    web_results: list[WebSearchResult] | None = None,
    profile: ApartmentProfile | None = PROFILE,
) -> tuple[
    ChatbotAnswerService,
    SequenceAnswerProvider,
    RecordingRewriteProvider,
    RecordingWebProvider,
    CountingEmbedder,
    dict[str, int],
]:
    answer_provider = SequenceAnswerProvider(*outcomes)
    rewrite_provider = RecordingRewriteProvider()
    web_provider = RecordingWebProvider(web_results or [])
    embedder = CountingEmbedder()
    calls = {"retrieve": 0}
    service = ChatbotAnswerService(
        provider=answer_provider,
        rewrite_provider=rewrite_provider,
        embedder=embedder,
        connection_factory=lambda: None,
        settings=ChatbotSettings(web_result_filter_enabled=False),
        web_search_provider=web_provider,
        web_settings=WebSearchSettings(),
    )

    def fetch_profile(
        request: ChatbotAnswerRequest,
    ) -> tuple[ApartmentProfile | None, bool]:
        return profile, has_reports

    def retrieve(request: ChatbotAnswerRequest) -> list[ReportChunkHit]:
        calls["retrieve"] += 1
        embedder.encode_query(request.question)
        return list(hits or [])

    service._fetch_profile = fetch_profile
    service._retrieve = retrieve
    return (
        service,
        answer_provider,
        rewrite_provider,
        web_provider,
        embedder,
        calls,
    )


@pytest.mark.asyncio
async def test_report_citation_skips_rewrite_web_and_second_answer() -> None:
    service, answers, rewrite, web, _, _ = _service(
        (
            {
                "answer": "Parking is sufficient.",
                "usedSources": [1],
                "usedProfile": [],
            },
        ),
        has_reports=True,
        hits=[HIT],
        web_results=[WEB],
    )

    response = await service.answer(_request())

    assert response.basisType == "REPORT"
    assert answers.call_count == 1
    assert rewrite.call_count == 0
    assert web.call_count == 0


@pytest.mark.asyncio
async def test_empty_first_citation_runs_rewrite_web_and_second_answer() -> None:
    service, answers, rewrite, web, _, _ = _service(
        (
            {
                "answer": "The report does not say.",
                "usedSources": [],
                "usedProfile": [],
            },
            {
                "answer": "A web result explains it.",
                "usedSources": [2],
                "usedProfile": [],
            },
        ),
        has_reports=True,
        hits=[HIT],
        web_results=[WEB],
    )

    response = await service.answer(_request())

    assert answers.call_count == 2
    assert rewrite.call_count == 1
    assert web.call_count == 1
    assert response.basisType == "WEB"
    assert response.sources[0].title == WEB.title
    assert response.sources[0].url == WEB.url


@pytest.mark.asyncio
async def test_empty_web_results_return_first_answer_without_second_call() -> None:
    first = {
        "answer": "The report does not say.",
        "usedSources": [],
        "usedProfile": [],
    }
    service, answers, rewrite, web, _, _ = _service(
        (first,), has_reports=True, hits=[HIT]
    )

    response = await service.answer(_request())

    assert response.answer == first["answer"]
    assert response.basisType == "NONE"
    assert answers.call_count == 1
    assert rewrite.call_count == 1
    assert web.call_count == 1


@pytest.mark.asyncio
async def test_empty_second_citation_returns_first_answer() -> None:
    first = {
        "answer": "The report does not say.",
        "usedSources": [],
        "usedProfile": [],
    }
    service, answers, _, _, _, _ = _service(
        (
            first,
            {
                "answer": "Uncited web answer.",
                "usedSources": [],
                "usedProfile": [],
            },
        ),
        has_reports=True,
        hits=[HIT],
        web_results=[WEB],
    )

    response = await service.answer(_request())

    assert answers.call_count == 2
    assert response.answer == first["answer"]
    assert response.basisType == "NONE"


@pytest.mark.asyncio
async def test_second_answer_failure_returns_first_answer() -> None:
    first = {
        "answer": "The report does not say.",
        "usedSources": [],
        "usedProfile": [],
    }
    service, answers, _, _, _, _ = _service(
        (first, LlmProviderError("timeout")),
        has_reports=True,
        hits=[HIT],
        web_results=[WEB],
    )

    response = await service.answer(_request())

    assert answers.call_count == 2
    assert response.answer == first["answer"]
    assert response.basisType == "NONE"


@pytest.mark.asyncio
async def test_no_report_documents_skip_vector_and_generate_once_from_web() -> None:
    service, answers, rewrite, web, embedder, calls = _service(
        (
            {
                "answer": "Web-grounded answer.",
                "usedSources": [1],
                "usedProfile": [],
            },
        ),
        has_reports=False,
        web_results=[WEB],
    )

    response = await service.answer(_request())

    assert response.basisType == "WEB"
    assert calls["retrieve"] == 0
    assert embedder.call_count == 0
    assert rewrite.call_count == 1
    assert web.call_count == 1
    assert answers.call_count == 1


@pytest.mark.asyncio
async def test_no_report_profile_or_web_returns_insufficient_evidence() -> None:
    service, answers, _, _, embedder, calls = _service(
        (), has_reports=False, profile=None
    )

    response = await service.answer(_request())

    assert response.answer == INSUFFICIENT_EVIDENCE_ANSWER
    assert response.basisType == "NONE"
    assert answers.call_count == 0
    assert calls["retrieve"] == 0
    assert embedder.call_count == 0


@pytest.mark.asyncio
async def test_concurrent_requests_do_not_share_report_presence() -> None:
    service, answers, rewrite, web, _, calls = _service(
        (
            {
                "answer": "Report-grounded answer.",
                "usedSources": [1],
                "usedProfile": [],
            },
            {
                "answer": "Web-grounded answer.",
                "usedSources": [1],
                "usedProfile": [],
            },
        ),
        has_reports=True,
        hits=[HIT],
        web_results=[WEB],
    )

    def fetch_profile(
        request: ChatbotAnswerRequest,
    ) -> tuple[ApartmentProfile, bool]:
        return PROFILE, request.apartmentId == 221

    service._fetch_profile = fetch_profile
    report_request = _request()
    web_request = report_request.model_copy(update={"apartmentId": 222})

    report_response, web_response = await asyncio.gather(
        service.answer(report_request),
        service.answer(web_request),
    )

    assert {report_response.basisType, web_response.basisType} == {
        "REPORT",
        "WEB",
    }
    assert calls["retrieve"] == 1
    assert rewrite.call_count == 1
    assert web.call_count == 1
    assert answers.call_count == 2
    assert not hasattr(service, "_has_report_documents")


class DatabaseCursor:
    def __init__(self, connection: "DatabaseConnection") -> None:
        self.connection = connection
        self.sql = ""

    def __enter__(self) -> "DatabaseCursor":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def execute(self, sql: str, params: dict) -> None:
        self.sql = sql
        self.connection.statements.append(sql)
        if "SELECT EXISTS" in sql and self.connection.fail_exists:
            raise RuntimeError("exists failed")

    def fetchone(self) -> tuple | None:
        if "SELECT EXISTS" in self.sql:
            return (self.connection.has_reports,)
        return PROFILE_ROW

    def fetchall(self) -> list[tuple]:
        return [(HIT.source_id, HIT.content, HIT.source_at, HIT.similarity)]


class DatabaseConnection:
    def __init__(self, *, has_reports: bool, fail_exists: bool = False) -> None:
        self.has_reports = has_reports
        self.fail_exists = fail_exists
        self.statements: list[str] = []
        self.closed = False

    def cursor(self) -> DatabaseCursor:
        return DatabaseCursor(self)

    def close(self) -> None:
        self.closed = True


@pytest.mark.asyncio
async def test_profile_and_exists_share_connection_and_all_connections_close() -> None:
    connections: list[DatabaseConnection] = []

    def connection_factory() -> DatabaseConnection:
        connection = DatabaseConnection(has_reports=True)
        connections.append(connection)
        return connection

    service = ChatbotAnswerService(
        provider=SequenceAnswerProvider(
            {
                "answer": "Report-grounded answer.",
                "usedSources": [1],
                "usedProfile": [],
            }
        ),
        embedder=CountingEmbedder(),
        connection_factory=connection_factory,
        settings=ChatbotSettings(),
        web_search_provider=RecordingWebProvider([]),
        web_settings=WebSearchSettings(),
    )

    response = await service.answer(_request())

    assert response.basisType == "REPORT"
    assert len(connections) == 2
    assert "FROM apartment\nWHERE" in connections[0].statements[0]
    assert "SELECT EXISTS" in connections[0].statements[1]
    assert all(connection.closed for connection in connections)


@pytest.mark.asyncio
async def test_exists_failure_is_rag_db_failed_and_closes_connection() -> None:
    connection = DatabaseConnection(has_reports=False, fail_exists=True)
    service = ChatbotAnswerService(
        provider=SequenceAnswerProvider(),
        embedder=CountingEmbedder(),
        connection_factory=lambda: connection,
        settings=ChatbotSettings(),
        web_search_provider=RecordingWebProvider([]),
        web_settings=WebSearchSettings(),
    )

    with pytest.raises(ChatbotAnswerError) as exc:
        await service.answer(_request())

    assert exc.value.code == "RAG_DB_FAILED"
    assert connection.closed
