"""Web-result relevance filtering tests."""

from datetime import datetime, timedelta, timezone

import pytest

from app.providers.web_search_provider import WebSearchResult
from app.rag.search import ApartmentProfile, ReportChunkHit
from app.rag.settings_chatbot import ChatbotSettings, WebSearchSettings
from app.schemas.chatbot import ChatbotAnswerRequest
from app.services.chatbot_answer_service import (
    ChatbotAnswerService,
    _filter_web_results,
    _filter_web_results_by_age,
    _to_web_source,
)


NOW = datetime(2026, 8, 12, tzinfo=timezone.utc)


def _result(title, snippet="본문", published_at=None, url="https://example.com"):
    return WebSearchResult(
        title, snippet, url, "example.com", NOW, published_at=published_at
    )


def test_age_filter_keeps_recent_and_unknown_but_removes_stale() -> None:
    recent = _result("최근", published_at=NOW - timedelta(days=100))
    stale = _result("오래됨", published_at=NOW - timedelta(days=1096))
    unknown = _result("날짜 미상")

    assert _filter_web_results_by_age(
        [recent, stale, unknown], 1095, NOW
    ) == [recent, unknown]


def test_age_filter_handles_naive_and_aware_datetimes() -> None:
    naive = _result("naive", published_at=datetime(2026, 8, 1))
    aware = _result(
        "aware",
        published_at=datetime(2026, 8, 1, tzinfo=timezone(timedelta(hours=9))),
    )

    assert _filter_web_results_by_age([naive, aware], 30, NOW) == [naive, aware]


def test_web_source_uses_published_at_then_retrieved_at() -> None:
    published_at = NOW - timedelta(days=10)
    published = _to_web_source(_result("published", published_at=published_at))
    unknown = _to_web_source(_result("unknown"))

    assert published.sourceAt == published_at.isoformat()
    assert unknown.sourceAt == NOW.isoformat()


@pytest.mark.parametrize(
    "result",
    [
        _result("도곡렉슬 시세"),
        _result("학교 안내", "도곡동 초등학교"),
        _result("<b>도곡</b>렉슬 정보"),
        _result("도곡 렉슬 정보"),
        _result("강남구 개발 소식"),
    ],
)
def test_matching_result_passes(result) -> None:
    assert _filter_web_results([result], "도곡렉슬", "강남구", "도곡동") == [result]


def test_unrelated_result_is_discarded() -> None:
    assert _filter_web_results(
        [_result("유경준", "국회의원 소개")], "도곡렉슬", "강남구", "도곡동"
    ) == []


def test_nullable_regions_use_apartment_name_only() -> None:
    matched = _result("도곡렉슬 단지")
    unrelated = _result("도곡동 소식")
    assert _filter_web_results(
        [matched, unrelated], "도곡렉슬", None, None
    ) == [matched]


class _Provider:
    async def complete_json(self, system_prompt, user_prompt):
        return {"query": "강남구 도곡동 재개발"}


def _service(filter_enabled=True):
    return ChatbotAnswerService(
        provider=_Provider(),
        rewrite_provider=_Provider(),
        embedder=object(),
        connection_factory=lambda: None,
        settings=ChatbotSettings(
            web_result_filter_enabled=filter_enabled,
            lazy_web_enabled=False,
        ),
        web_settings=WebSearchSettings(top_k=1),
    )


@pytest.mark.asyncio
async def test_filter_runs_before_top_k_and_logs_count_only(caplog) -> None:
    unrelated = [_result(f"무관 제목 {index}", f"비밀본문-{index}") for index in range(5)]
    relevant = _result("도곡동 관련 결과")
    service = _service()
    search_calls = []

    class SearchProvider:
        async def search(self, query, top_k):
            search_calls.append((query, top_k))
            return [*unrelated, relevant][:top_k]

    service.web_search_provider = SearchProvider()
    request = ChatbotAnswerRequest(apartmentId=221, question="재개발")
    profile = ApartmentProfile("도곡렉슬", None, "강남구", "도곡동", None, None, None)
    with caplog.at_level("INFO"):
        results = await service._rewrite_search_and_filter(request, profile, "fallback")
    assert search_calls == [("강남구 도곡동 재개발", 50)]
    assert results == [relevant]
    assert "filtered: 5" in caplog.text
    assert "비밀본문" not in caplog.text


@pytest.mark.asyncio
async def test_age_filter_runs_before_top_k_and_logs_count_only(caplog) -> None:
    stale = [
        _result(
            f"도곡동 오래된 결과 {index}",
            f"비밀본문-{index}",
            NOW - timedelta(days=1096),
            f"https://old{index}.example.com",
        )
        for index in range(5)
    ]
    recent = _result(
        "도곡동 최신 결과",
        published_at=NOW - timedelta(days=1),
        url="https://recent.example.com",
    )
    service = _service()

    class SearchProvider:
        async def search(self, query, top_k):
            return [*stale, recent][:top_k]

    service.web_search_provider = SearchProvider()
    request = ChatbotAnswerRequest(apartmentId=221, question="재개발")
    profile = ApartmentProfile("도곡렉슬", None, "강남구", "도곡동", None, None, None)
    with caplog.at_level("INFO"):
        results = await service._rewrite_search_and_filter(request, profile, "fallback")

    assert results == [recent]
    assert "stale web results filtered: 5" in caplog.text
    assert "비밀본문" not in caplog.text


@pytest.mark.asyncio
async def test_disabled_filter_keeps_unrelated_results() -> None:
    unrelated = _result("무관한 문서")
    service = _service(filter_enabled=False)
    search_calls = []

    class SearchProvider:
        async def search(self, query, top_k):
            search_calls.append((query, top_k))
            return [unrelated][:top_k]

    service.web_search_provider = SearchProvider()
    request = ChatbotAnswerRequest(apartmentId=221, question="재개발")
    profile = ApartmentProfile("도곡렉슬", None, "강남구", "도곡동", None, None, None)
    assert await service._rewrite_search_and_filter(
        request, profile, "fallback"
    ) == [unrelated]
    assert search_calls == [("강남구 도곡동 재개발", 50)]


class _AnswerProvider:
    def __init__(self, used_sources):
        self.used_sources = used_sources
        self.call_count = 0

    async def complete_json(self, system_prompt, user_prompt):
        self.call_count += 1
        return {"answer": "정상 답변", "usedSources": self.used_sources}


class _FixedSearchProvider:
    def __init__(self, results):
        self.results = results

    async def search(self, query, top_k):
        return self.results[:top_k]


@pytest.mark.asyncio
async def test_all_filtered_results_keep_report_answer_path() -> None:
    service = _service()
    answer = _AnswerProvider([1])
    profile = ApartmentProfile(
        "도곡렉슬", None, "강남구", "도곡동", None, None, None
    )
    service.provider = answer
    service.web_search_provider = _FixedSearchProvider(
        [_result("무관한 문서", "다른 지역")]
    )
    service._fetch_profile = lambda request: (profile, True)
    service._retrieve = lambda request: [
        ReportChunkHit(7, "리포트 근거", NOW, 0.8)
    ]

    response = await service.answer(
        ChatbotAnswerRequest(apartmentId=221, question="재개발")
    )
    assert response.basisType == "REPORT"
    assert response.sources[0].sourceId == 7
    assert answer.call_count == 1


@pytest.mark.asyncio
async def test_all_filtered_results_keep_insufficient_none_path() -> None:
    service = _service()
    answer = _AnswerProvider([])
    service.provider = answer
    service.web_search_provider = _FixedSearchProvider(
        [_result("무관한 문서", "다른 지역")]
    )
    service._fetch_profile = lambda request: (None, False)
    service._retrieve = lambda request: []

    response = await service.answer(
        ChatbotAnswerRequest(
            apartmentId=221,
            apartmentName="도곡렉슬",
            question="재개발",
        )
    )
    assert response.basisType == "NONE"
    assert answer.call_count == 0
