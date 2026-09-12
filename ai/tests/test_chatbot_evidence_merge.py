"""Unified evidence selection, citation normalization and profile tests."""

import asyncio
import threading
from datetime import datetime, timezone

import pytest

from app.providers.web_search_provider import WebSearchResult
from app.rag.search import ApartmentProfile, ReportChunkHit
from app.rag.settings_chatbot import ChatbotSettings, WebSearchSettings
from app.schemas.chatbot import ChatbotAnswerRequest, INSUFFICIENT_EVIDENCE_ANSWER
from app.services.chatbot_answer_service import ChatbotAnswerService


NOW = datetime(2026, 8, 4, tzinfo=timezone.utc)
PROFILE = ApartmentProfile(
    name="반포자이",
    address="서울 서초구 신반포로 275",
    district_name="서초구",
    dong_name="반포동",
    household_count=3410,
    completion_year_month="2009-12",
    parking_space_count=5000,
)


def _hit(source_id: int, similarity: float = 0.8, content: str = "리포트 근거") -> ReportChunkHit:
    return ReportChunkHit(source_id, content, NOW, similarity)


def _web(index: int) -> WebSearchResult:
    return WebSearchResult(
        title=f"웹 문서 {index}",
        snippet=f"웹 근거 {index}",
        url=f"https://example.com/{index}",
        domain="example.com",
        retrieved_at=NOW,
    )


class FakeLlm:
    def __init__(self, used_sources: list[int], answer: str = "모델 답변") -> None:
        self.payload = {"answer": answer, "usedSources": used_sources}
        self.call_count = 0
        self.last_user = ""

    async def complete_json(self, system_prompt: str, user_prompt: str) -> dict:
        self.call_count += 1
        self.last_user = user_prompt
        return self.payload


class FakeSearch:
    def __init__(self, results: list[WebSearchResult], error: Exception | None = None) -> None:
        self.results = results
        self.error = error
        self.call_count = 0

    async def search(self, query: str, top_k: int) -> list[WebSearchResult]:
        self.call_count += 1
        if self.error:
            raise self.error
        return self.results


class FixedEvidenceService(ChatbotAnswerService):
    def __init__(self, hits, profile, web, llm) -> None:
        self.fixed_hits = hits
        self.fixed_profile = profile
        super().__init__(
            provider=llm,
            embedder=object(),
            connection_factory=lambda: None,
            settings=ChatbotSettings(lazy_web_enabled=False),
            web_search_provider=web,
            web_settings=WebSearchSettings(top_k=5),
        )

    def _fetch_profile(self, request):
        return self.fixed_profile, bool(self.fixed_hits)

    def _retrieve(self, request):
        return self.fixed_hits


def _request() -> ChatbotAnswerRequest:
    return ChatbotAnswerRequest(
        apartmentId=1, apartmentName="반포자이", question="어디야?"
    )


def _service(hits, web_results, used_sources, profile=PROFILE, search_error=None):
    llm = FakeLlm(used_sources)
    search = FakeSearch(web_results, search_error)
    return FixedEvidenceService(hits, profile, search, llm), search, llm


@pytest.mark.asyncio
async def test_m1_report_number_selects_only_that_report() -> None:
    service, _, _ = _service([_hit(1), _hit(2), _hit(3)], [_web(1), _web(2)], [1])
    response = await service.answer(_request())
    assert response.basisType == "REPORT"
    assert [source.sourceId for source in response.sources] == [1]
    assert response.fallbackToWeb is False


@pytest.mark.asyncio
async def test_m2_web_number_after_reports_selects_web() -> None:
    service, _, _ = _service([_hit(1), _hit(2), _hit(3)], [_web(1), _web(2)], [4])
    response = await service.answer(_request())
    assert response.basisType == "WEB"
    assert response.basisLabel == "웹 기반"
    assert response.sources[0].url == "https://example.com/1"


@pytest.mark.asyncio
async def test_m3_empty_used_sources_keeps_model_answer_as_none() -> None:
    service, _, _ = _service([_hit(1)], [_web(1)], [], profile=PROFILE)
    response = await service.answer(_request())
    assert response.basisType == "NONE"
    assert response.basisLabel is None
    assert response.sources == []
    assert response.answer == "모델 답변"


@pytest.mark.asyncio
async def test_m4_mixed_citations_prefer_report_and_warn(caplog) -> None:
    service, _, _ = _service([_hit(1), _hit(2), _hit(3)], [_web(1)], [1, 4])
    with caplog.at_level("WARNING"):
        response = await service.answer(_request())
    assert response.basisType == "REPORT"
    assert all(source.sourceType == "REPORT" for source in response.sources)
    assert "report=1 web=1" in caplog.text


@pytest.mark.asyncio
async def test_m5_web_works_without_report_hits() -> None:
    service, _, _ = _service([], [_web(1), _web(2)], [1])
    response = await service.answer(_request())
    assert response.basisType == "WEB"


@pytest.mark.asyncio
async def test_m6_web_failure_does_not_escape_when_report_exists() -> None:
    service, _, _ = _service([_hit(1)], [], [1], search_error=RuntimeError("quota"))
    response = await service.answer(_request())
    assert response.basisType == "REPORT"


@pytest.mark.asyncio
async def test_m7_profile_only_still_calls_model() -> None:
    service, _, llm = _service([], [], [], profile=PROFILE)
    response = await service.answer(_request())
    assert llm.call_count == 1
    assert response.basisType == "NONE"


@pytest.mark.asyncio
async def test_m8_no_evidence_or_profile_skips_model() -> None:
    service, _, llm = _service([], [], [], profile=None)
    response = await service.answer(_request())
    assert llm.call_count == 0
    assert response.answer == INSUFFICIENT_EVIDENCE_ANSWER
    assert response.basisType == "NONE"


@pytest.mark.asyncio
async def test_m9_low_similarity_report_is_still_available() -> None:
    service, _, _ = _service([_hit(1, similarity=0.2)], [_web(1)], [1])
    response = await service.answer(_request())
    assert response.basisType == "REPORT"
    assert response.topSimilarity == 0.2


@pytest.mark.asyncio
async def test_m10_web_search_always_runs_regardless_of_similarity() -> None:
    service, search, _ = _service([_hit(1, similarity=0.99)], [], [1])
    await service.answer(_request())
    assert search.call_count == 1


@pytest.mark.asyncio
async def test_m11_report_and_web_retrieval_run_concurrently() -> None:
    report_started = threading.Event()
    web_started = threading.Event()
    llm = FakeLlm([])

    class ConcurrentService(FixedEvidenceService):
        def _retrieve(self, request):
            report_started.set()
            assert web_started.wait(timeout=1)
            return []

        async def _search_web(self, query):
            assert await asyncio.to_thread(report_started.wait, 1)
            web_started.set()
            return []

    service = ConcurrentService([], PROFILE, FakeSearch([]), llm)
    await asyncio.wait_for(service.answer(_request()), timeout=2)
    assert report_started.is_set() and web_started.is_set()


@pytest.mark.asyncio
async def test_invalid_and_duplicate_numbers_are_discarded() -> None:
    service, _, _ = _service([_hit(1), _hit(2)], [], [0, 1, 1, 99])
    response = await service.answer(_request())
    assert [source.sourceId for source in response.sources] == [1]


@pytest.mark.asyncio
async def test_profile_fields_render_without_none_or_evidence_numbers() -> None:
    partial = ApartmentProfile("반포자이", None, "서초구", "반포동", None, "2009-12", 5000)
    service, _, llm = _service([], [], [], profile=partial)
    await service.answer(_request())
    assert "[아파트 기본 정보]" in llm.last_user
    assert "지역: 서초구 반포동" in llm.last_user
    assert "준공: 2009-12" in llm.last_user
    assert "주차: 5000대" in llm.last_user
    assert "주소:" not in llm.last_user
    assert "세대수:" not in llm.last_user
    assert "None" not in llm.last_user
    assert "[근거 1]" not in llm.last_user


@pytest.mark.asyncio
async def test_context_truncation_controls_prompt_numbering() -> None:
    hits = [_hit(i, content="가" * 1000) for i in range(50)]
    llm = FakeLlm([30])
    search = FakeSearch([])
    service = FixedEvidenceService(hits, PROFILE, search, llm)
    response = await service.answer(_request())
    assert llm.last_user.count("[근거 ") == 30
    assert response.sources[0].sourceId == 29
