"""Environment-backed settings for chatbot answer generation (AI-009)."""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass, field

from app.config import env_bool, load_ai_env


DEFAULT_TOP_K = 60
DEFAULT_MAX_CONTEXT_CHARS = 30000

DEFAULT_WEB_SEARCH_PROVIDER = "none"
DEFAULT_WEB_SEARCH_TOP_K = 5
DEFAULT_WEB_SEARCH_TIMEOUT_SECONDS = 10.0
DEFAULT_WEB_SEARCH_COLLECTIONS = ("web", "blog")
DEFAULT_WEB_SEARCH_MAX_AGE_DAYS = 1095
_ALLOWED_WEB_SEARCH_COLLECTIONS = frozenset({"web", "blog", "cafe"})


logger = logging.getLogger(__name__)


def _positive_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    value = default if raw is None or not raw.strip() else int(raw)
    if value < 1:
        raise ValueError(f"{name} must be at least 1")
    return value


@dataclass(frozen=True)
class ChatbotSettings:
    """Retrieval tuning knobs for the unified evidence prompt."""

    top_k: int = DEFAULT_TOP_K
    max_context_chars: int = DEFAULT_MAX_CONTEXT_CHARS
    search_rewrite_enabled: bool = True
    web_result_filter_enabled: bool = True
    lazy_web_enabled: bool = True

    @classmethod
    def from_env(cls) -> "ChatbotSettings":
        load_ai_env()
        return cls(
            top_k=_positive_int("CHATBOT_TOP_K", DEFAULT_TOP_K),
            max_context_chars=_positive_int(
                "CHATBOT_MAX_CONTEXT_CHARS",
                DEFAULT_MAX_CONTEXT_CHARS,
            ),
            search_rewrite_enabled=env_bool(
                "CHATBOT_SEARCH_REWRITE_ENABLED", True
            ),
            web_result_filter_enabled=env_bool(
                "CHATBOT_WEB_RESULT_FILTER_ENABLED", True
            ),
            lazy_web_enabled=env_bool("CHATBOT_LAZY_WEB_ENABLED", True),
        )


def _positive_float(name: str, default: float) -> float:
    raw = os.getenv(name)
    value = default if raw is None or not raw.strip() else float(raw)
    if value <= 0:
        raise ValueError(f"{name} must be greater than 0")
    return value


@dataclass(frozen=True)
class WebSearchSettings:
    """External search settings without secret-bearing repr output.

    ``provider`` defaults to ``none`` so the whole pipeline runs without an API
    key: no key simply means every web answer comes back as ``NONE``.
    """

    provider: str = DEFAULT_WEB_SEARCH_PROVIDER
    api_key: str = field(repr=False, default="")
    base_url: str = ""
    top_k: int = DEFAULT_WEB_SEARCH_TOP_K
    timeout_seconds: float = DEFAULT_WEB_SEARCH_TIMEOUT_SECONDS
    collections: tuple[str, ...] = DEFAULT_WEB_SEARCH_COLLECTIONS
    max_age_days: int = DEFAULT_WEB_SEARCH_MAX_AGE_DAYS

    @classmethod
    def from_env(cls) -> "WebSearchSettings":
        load_ai_env()
        collections = _web_search_collections(
            os.getenv("WEB_SEARCH_COLLECTIONS", "web,blog")
        )
        return cls(
            provider=(
                os.getenv("WEB_SEARCH_PROVIDER", DEFAULT_WEB_SEARCH_PROVIDER)
                .strip()
                .lower()
                or DEFAULT_WEB_SEARCH_PROVIDER
            ),
            api_key=os.getenv("WEB_SEARCH_API_KEY", ""),
            base_url=os.getenv("WEB_SEARCH_BASE_URL", "").strip(),
            top_k=_positive_int("WEB_SEARCH_TOP_K", DEFAULT_WEB_SEARCH_TOP_K),
            timeout_seconds=_positive_float(
                "WEB_SEARCH_TIMEOUT_SECONDS",
                DEFAULT_WEB_SEARCH_TIMEOUT_SECONDS,
            ),
            collections=collections,
            max_age_days=_positive_int(
                "WEB_SEARCH_MAX_AGE_DAYS",
                DEFAULT_WEB_SEARCH_MAX_AGE_DAYS,
            ),
        )


def _web_search_collections(raw: str) -> tuple[str, ...]:
    collections: list[str] = []
    for item in raw.split(","):
        name = item.strip().lower()
        if not name:
            continue
        if name not in _ALLOWED_WEB_SEARCH_COLLECTIONS:
            logger.warning("Unsupported web search collection=%s; ignoring", name)
            continue
        if name not in collections:
            collections.append(name)
    return tuple(collections) or ("web",)
