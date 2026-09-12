"""Web search port, Kakao web-document adapter, and null adapter (AI-009).

Mirrors ``llm_provider.py``: ABC + implementations + factory.

Unlike the LLM port, **search never raises**. A failed search is not a broken
pipeline — it means we have nothing trustworthy to cite, which the service turns
into an explicit ``NONE`` answer instead of a 5xx.
"""

from __future__ import annotations

import asyncio
import logging
import re
from abc import ABC, abstractmethod
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Callable
from urllib.parse import urlparse

import httpx

from app.rag.settings_chatbot import WebSearchSettings


logger = logging.getLogger(__name__)

KAKAO_DEFAULT_BASE_URL = "https://dapi.kakao.com"
_HTML_TAG = re.compile(r"<[^>]+>")


@dataclass(frozen=True)
class WebSearchResult:
    title: str
    snippet: str
    url: str
    domain: str
    retrieved_at: datetime
    published_at: datetime | None = None


def extract_domain(url: str) -> str:
    return urlparse(url).netloc


def strip_html(text: str) -> str:
    """Kakao wraps matched terms in <b> tags; they must not reach the prompt."""
    return _HTML_TAG.sub("", text or "").strip()


class WebSearchProvider(ABC):
    @abstractmethod
    async def search(self, query: str, top_k: int) -> list[WebSearchResult]:
        """Return at most ``top_k`` results. Returns [] instead of raising."""


class NullWebSearchProvider(WebSearchProvider):
    """Used when WEB_SEARCH_PROVIDER=none, so the pipeline runs without a key."""

    async def search(self, query: str, top_k: int) -> list[WebSearchResult]:
        return []


class KakaoWebSearchProvider(WebSearchProvider):
    """Kakao web document search.

    GET {base_url}/v2/search/web?query=...&size=...
    Authorization: KakaoAK {api_key}
    """

    def __init__(
        self,
        settings: WebSearchSettings,
        client_factory: Callable[..., Any] | None = None,
    ) -> None:
        self.settings = settings
        self.base_url = (settings.base_url or KAKAO_DEFAULT_BASE_URL).rstrip("/")
        self._client_factory = client_factory or httpx.AsyncClient

    def is_configured(self) -> bool:
        return bool(self.settings.api_key and self.base_url)

    def _timeouts(self) -> httpx.Timeout:
        total = self.settings.timeout_seconds
        return httpx.Timeout(
            connect=total / 2,
            read=total,
            write=total,
            pool=total / 2,
        )

    async def search(self, query: str, top_k: int) -> list[WebSearchResult]:
        if not self.is_configured():
            # Never log the key itself, only whether one is present.
            logger.warning("Kakao web search is not configured; returning no results")
            return []

        retrieved_at = datetime.now(timezone.utc)
        collection_results = await asyncio.gather(
            *(
                self._search_collection(collection, query, top_k, retrieved_at)
                for collection in self.settings.collections
            )
        )
        return _round_robin_unique(collection_results, top_k)

    async def _search_collection(
        self,
        collection: str,
        query: str,
        top_k: int,
        retrieved_at: datetime,
    ) -> list[WebSearchResult]:
        try:
            payload = await self._request(collection, query, top_k)
            if payload is None:
                return []
            return self._parse(payload, top_k, retrieved_at)
        except Exception as exc:
            logger.warning(
                "Kakao search collection=%s failed: %s",
                collection,
                type(exc).__name__,
            )
            return []

    async def _request(
        self, collection: str, query: str, top_k: int
    ) -> dict[str, Any] | None:
        headers = {"Authorization": f"KakaoAK {self.settings.api_key}"}
        params = {"query": query, "size": max(1, min(top_k, 50))}

        async with self._client_factory(timeout=self._timeouts()) as client:
            response = await client.get(
                f"{self.base_url}/v2/search/{collection}",
                headers=headers,
                params=params,
            )

        if response.status_code >= 400:
            # Quota exhaustion and bad keys both land here. Not a 5xx for us.
            logger.warning(
                "Kakao search collection=%s returned status %s",
                collection,
                response.status_code,
            )
            return None

        try:
            return response.json()
        except ValueError:
            logger.warning(
                "Kakao search collection=%s returned non-JSON body", collection
            )
            return None

    def _parse(
        self,
        payload: dict[str, Any],
        top_k: int,
        retrieved_at: datetime,
    ) -> list[WebSearchResult]:
        documents = payload.get("documents") or []
        results: list[WebSearchResult] = []
        for document in documents[:top_k]:
            url = (document.get("url") or "").strip()
            title = strip_html(document.get("title", ""))
            if not url or not title:
                continue
            results.append(
                WebSearchResult(
                    title=title,
                    snippet=strip_html(document.get("contents", "")),
                    url=url,
                    domain=extract_domain(url),
                    retrieved_at=retrieved_at,
                    published_at=_parse_published_at(document.get("datetime")),
                )
            )
        return results


def _parse_published_at(value: object) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        parsed = datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed


def _round_robin_unique(
    collections: list[list[WebSearchResult]], top_k: int
) -> list[WebSearchResult]:
    merged: list[WebSearchResult] = []
    seen_urls: set[str] = set()
    max_size = max((len(results) for results in collections), default=0)
    for index in range(max_size):
        for results in collections:
            if index >= len(results):
                continue
            result = results[index]
            if result.url in seen_urls:
                continue
            seen_urls.add(result.url)
            merged.append(result)
            if len(merged) >= top_k:
                return merged
    return merged


def create_web_search_provider(
    settings: WebSearchSettings | None = None,
) -> WebSearchProvider:
    """Select provider from WEB_SEARCH_PROVIDER (default: none)."""
    settings = settings or WebSearchSettings.from_env()
    name = settings.provider
    if name in {"none", ""}:
        return NullWebSearchProvider()
    if name in {"kakao", "kakao-web", "daum"}:
        provider = KakaoWebSearchProvider(settings)
        logger.info(
            "Selected web search provider=kakao configured=%s",
            provider.is_configured(),
        )
        return provider
    # An unknown name is a config typo. Degrade to NONE answers rather than
    # failing every request with a 502.
    logger.warning(
        "Unsupported WEB_SEARCH_PROVIDER=%s; falling back to none", name
    )
    return NullWebSearchProvider()
