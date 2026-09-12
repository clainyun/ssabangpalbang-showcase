"""Web search provider tests (P-1~P-7). No real HTTP call is made."""

from datetime import datetime, timezone

import httpx
import pytest

from app.providers.web_search_provider import (
    KakaoWebSearchProvider,
    NullWebSearchProvider,
    WebSearchResult,
    create_web_search_provider,
    extract_domain,
    strip_html,
)
from app.rag.settings_chatbot import WebSearchSettings


def _settings(**overrides: object) -> WebSearchSettings:
    values = {
        "provider": "kakao",
        "api_key": "test-key",
        "base_url": "https://dapi.example.com",
        "top_k": 5,
        "timeout_seconds": 10.0,
    }
    values.update(overrides)
    return WebSearchSettings(**values)


class FakeResponse:
    def __init__(self, status_code: int, payload: object = None, bad_json: bool = False):
        self.status_code = status_code
        self._payload = payload
        self._bad_json = bad_json

    def json(self) -> object:
        if self._bad_json:
            raise ValueError("not json")
        return self._payload


class FakeClient:
    """Stands in for httpx.AsyncClient as an async context manager."""

    def __init__(self, response: object = None, error: Exception | None = None, **kwargs):
        self.response = response
        self.error = error
        self.requests: list[tuple[str, dict]] = []

    async def __aenter__(self) -> "FakeClient":
        return self

    async def __aexit__(self, *args: object) -> None:
        return None

    async def get(self, url: str, headers: dict, params: dict):
        self.requests.append((url, params))
        if self.error is not None:
            raise self.error
        return self.response


def _factory(response: object = None, error: Exception | None = None):
    holder = {}

    def make(**kwargs) -> FakeClient:
        client = FakeClient(response=response, error=error)
        holder["client"] = client
        return client

    make.holder = holder  # type: ignore[attr-defined]
    return make


def _documents(count: int) -> dict:
    return {
        "documents": [
            {
                "title": f"<b>래미안</b> 문서 {index}",
                "contents": f"<b>교통</b> 요약 {index}",
                "url": f"https://a{index}.example.com/x?y=1",
                "datetime": "2026-08-10T12:34:56+09:00",
            }
            for index in range(count)
        ]
    }


def test_p1_none_provider_is_selected_by_default() -> None:
    provider = create_web_search_provider(WebSearchSettings())

    assert isinstance(provider, NullWebSearchProvider)


def test_p1_kakao_provider_is_selected_by_name() -> None:
    provider = create_web_search_provider(_settings())

    assert isinstance(provider, KakaoWebSearchProvider)


def test_p1_unknown_provider_degrades_to_null() -> None:
    provider = create_web_search_provider(_settings(provider="bing"))

    assert isinstance(provider, NullWebSearchProvider)


@pytest.mark.asyncio
async def test_p2_null_provider_returns_empty_without_raising() -> None:
    assert await NullWebSearchProvider().search("래미안 교통", 5) == []


def test_p3_domain_is_extracted_from_url() -> None:
    assert extract_domain("https://a.example.com/x?y=1") == "a.example.com"


def test_strip_html_removes_highlight_tags() -> None:
    assert strip_html("<b>래미안</b> 옥수") == "래미안 옥수"


@pytest.mark.asyncio
async def test_p4_retrieved_at_is_timezone_aware() -> None:
    provider = KakaoWebSearchProvider(
        _settings(), client_factory=_factory(FakeResponse(200, _documents(1)))
    )

    results = await provider.search("래미안 교통", 5)

    assert len(results) == 1
    assert results[0].retrieved_at.tzinfo is not None
    assert results[0].retrieved_at.utcoffset() == timezone.utc.utcoffset(None)


@pytest.mark.asyncio
async def test_p5_http_4xx_returns_empty_list() -> None:
    provider = KakaoWebSearchProvider(
        _settings(), client_factory=_factory(FakeResponse(401, {}))
    )

    assert await provider.search("래미안 교통", 5) == []


@pytest.mark.asyncio
async def test_p5_http_5xx_returns_empty_list() -> None:
    provider = KakaoWebSearchProvider(
        _settings(), client_factory=_factory(FakeResponse(503, {}))
    )

    assert await provider.search("래미안 교통", 5) == []


@pytest.mark.asyncio
async def test_p6_timeout_returns_empty_list() -> None:
    provider = KakaoWebSearchProvider(
        _settings(),
        client_factory=_factory(error=httpx.TimeoutException("timeout")),
    )

    assert await provider.search("래미안 교통", 5) == []


@pytest.mark.asyncio
async def test_p6_connection_error_returns_empty_list() -> None:
    provider = KakaoWebSearchProvider(
        _settings(),
        client_factory=_factory(error=httpx.ConnectError("refused")),
    )

    assert await provider.search("래미안 교통", 5) == []


@pytest.mark.asyncio
async def test_p7_non_json_body_returns_empty_list() -> None:
    provider = KakaoWebSearchProvider(
        _settings(),
        client_factory=_factory(FakeResponse(200, bad_json=True)),
    )

    assert await provider.search("래미안 교통", 5) == []


@pytest.mark.asyncio
async def test_p7_unexpected_shape_returns_empty_list() -> None:
    provider = KakaoWebSearchProvider(
        _settings(), client_factory=_factory(FakeResponse(200, {"docs": "?"}))
    )

    assert await provider.search("래미안 교통", 5) == []


@pytest.mark.asyncio
async def test_missing_api_key_returns_empty_without_calling_out() -> None:
    factory = _factory(FakeResponse(200, _documents(1)))
    provider = KakaoWebSearchProvider(
        _settings(api_key=""), client_factory=factory
    )

    assert await provider.search("래미안 교통", 5) == []
    assert "client" not in factory.holder  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_results_are_parsed_and_cleaned() -> None:
    provider = KakaoWebSearchProvider(
        _settings(), client_factory=_factory(FakeResponse(200, _documents(2)))
    )

    results = await provider.search("래미안 교통", 5)

    assert [result.title for result in results] == ["래미안 문서 0", "래미안 문서 1"]
    assert results[0].snippet == "교통 요약 0"
    assert results[0].domain == "a0.example.com"
    assert results[0].published_at == datetime.fromisoformat(
        "2026-08-10T12:34:56+09:00"
    )


@pytest.mark.asyncio
@pytest.mark.parametrize("value", [None, "not-a-date"])
async def test_missing_or_invalid_datetime_becomes_none(value) -> None:
    payload = {
        "documents": [
            {
                "title": "<b>래미안</b>",
                "contents": "<b>교통</b>",
                "url": "https://example.com/document",
                **({} if value is None else {"datetime": value}),
            }
        ]
    }
    provider = KakaoWebSearchProvider(
        _settings(collections=("web",)),
        client_factory=_factory(FakeResponse(200, payload)),
    )

    results = await provider.search("래미안 교통", 5)

    assert results[0].published_at is None
    assert results[0].title == "래미안"
    assert results[0].snippet == "교통"


def test_published_at_defaults_to_none_for_backward_compatibility() -> None:
    result = WebSearchResult(
        "제목",
        "본문",
        "https://example.com",
        "example.com",
        datetime.now(timezone.utc),
    )

    assert result.published_at is None


@pytest.mark.asyncio
async def test_documents_without_url_are_dropped() -> None:
    payload = {"documents": [{"title": "제목", "contents": "요약", "url": ""}]}
    provider = KakaoWebSearchProvider(
        _settings(), client_factory=_factory(FakeResponse(200, payload))
    )

    assert await provider.search("래미안 교통", 5) == []


@pytest.mark.asyncio
async def test_top_k_is_sent_as_size_and_caps_results() -> None:
    factory = _factory(FakeResponse(200, _documents(10)))
    provider = KakaoWebSearchProvider(_settings(), client_factory=factory)

    results = await provider.search("래미안 교통", 3)

    assert len(results) == 3
    _, params = factory.holder["client"].requests[0]  # type: ignore[attr-defined]
    assert params["size"] == 3
    assert params["query"] == "래미안 교통"


def test_api_key_is_not_in_settings_repr() -> None:
    assert "test-key" not in repr(_settings())
