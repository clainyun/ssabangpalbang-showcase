"""Kakao web collection selection and merge tests."""

import asyncio
import logging

import httpx
import pytest

from app.providers.web_search_provider import KakaoWebSearchProvider
from app.rag.settings_chatbot import WebSearchSettings


class _Response:
    status_code = 200

    def __init__(self, documents):
        self.documents = documents

    def json(self):
        return {"documents": self.documents}


class _Client:
    def __init__(self, handler, **kwargs):
        self.handler = handler

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return None

    async def get(self, url, headers, params):
        return await self.handler(url, params)


def _document(name, url):
    return {"title": name, "contents": "강남구 소식", "url": url}


def _settings(collections=("web", "blog")):
    return WebSearchSettings(
        provider="kakao",
        api_key="test-key",
        base_url="https://dapi.example.com",
        collections=collections,
    )


@pytest.mark.asyncio
async def test_collections_run_concurrently_and_merge_round_robin() -> None:
    calls = []
    both_started = asyncio.Event()

    async def handler(url, params):
        collection = url.rsplit("/", 1)[-1]
        calls.append(collection)
        if len(calls) == 2:
            both_started.set()
        await asyncio.wait_for(both_started.wait(), timeout=0.2)
        return _Response(
            [
                _document(f"{collection}-0", f"https://{collection}/0"),
                _document(f"{collection}-1", f"https://{collection}/1"),
            ]
        )

    provider = KakaoWebSearchProvider(
        _settings(), client_factory=lambda **kwargs: _Client(handler, **kwargs)
    )

    results = await provider.search("강남구 재개발", 4)

    assert calls == ["web", "blog"]
    assert [result.title for result in results] == [
        "web-0",
        "blog-0",
        "web-1",
        "blog-1",
    ]


@pytest.mark.asyncio
async def test_collection_order_and_url_deduplication() -> None:
    async def handler(url, params):
        collection = url.rsplit("/", 1)[-1]
        return _Response(
            [
                _document(f"{collection}-0", "https://shared.example.com"),
                _document(f"{collection}-1", f"https://{collection}/1"),
            ]
        )

    provider = KakaoWebSearchProvider(
        _settings(("blog", "web")),
        client_factory=lambda **kwargs: _Client(handler, **kwargs),
    )

    results = await provider.search("강남구 재개발", 5)

    assert [result.title for result in results] == ["blog-0", "blog-1", "web-1"]


@pytest.mark.asyncio
async def test_one_collection_failure_keeps_other_results(caplog) -> None:
    async def handler(url, params):
        if url.endswith("/blog"):
            raise httpx.ConnectError("sensitive detail")
        return _Response([_document("web-result", "https://web/0")])

    provider = KakaoWebSearchProvider(
        _settings(), client_factory=lambda **kwargs: _Client(handler, **kwargs)
    )

    with caplog.at_level(logging.WARNING):
        results = await provider.search("강남구 재개발", 5)

    assert [result.title for result in results] == ["web-result"]
    assert "collection=blog" in caplog.text
    assert "ConnectError" in caplog.text
    assert "sensitive detail" not in caplog.text


@pytest.mark.asyncio
async def test_all_collection_failures_return_empty() -> None:
    async def handler(url, params):
        raise httpx.ConnectError("failed")

    provider = KakaoWebSearchProvider(
        _settings(), client_factory=lambda **kwargs: _Client(handler, **kwargs)
    )

    assert await provider.search("강남구 재개발", 5) == []


@pytest.mark.asyncio
async def test_cafe_collection_uses_cafe_route() -> None:
    calls = []

    async def handler(url, params):
        calls.append(url)
        return _Response([_document("cafe-result", "https://cafe.example.com/1")])

    provider = KakaoWebSearchProvider(
        _settings(("cafe",)),
        client_factory=lambda **kwargs: _Client(handler, **kwargs),
    )

    results = await provider.search("강남구 재개발", 5)

    assert calls == ["https://dapi.example.com/v2/search/cafe"]
    assert [result.title for result in results] == ["cafe-result"]


@pytest.mark.parametrize(
    ("raw", "expected", "warning"),
    [
        ("web,news", ("web",), "collection=news"),
        ("", ("web",), None),
        ("cafe", ("cafe",), None),
    ],
)
def test_collection_settings_selection(monkeypatch, caplog, raw, expected, warning):
    monkeypatch.setenv("WEB_SEARCH_COLLECTIONS", raw)

    with caplog.at_level(logging.WARNING):
        settings = WebSearchSettings.from_env()

    assert settings.collections == expected
    if warning:
        assert warning in caplog.text


def test_max_age_days_settings(monkeypatch) -> None:
    monkeypatch.setenv("WEB_SEARCH_MAX_AGE_DAYS", "30")

    assert WebSearchSettings.from_env().max_age_days == 30


def test_max_age_days_rejects_non_positive_value(monkeypatch) -> None:
    monkeypatch.setenv("WEB_SEARCH_MAX_AGE_DAYS", "0")

    with pytest.raises(ValueError, match="WEB_SEARCH_MAX_AGE_DAYS"):
        WebSearchSettings.from_env()
