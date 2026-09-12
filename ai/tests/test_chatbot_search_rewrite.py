"""Search-query rewriting, fallback and provider-isolation tests."""

from datetime import datetime, timezone

import pytest

from app.prompts.chatbot_prompt import SEARCH_QUERY_SYSTEM_PROMPT
from app.providers.llm_provider import LlmProviderError
from app.rag.search import ApartmentProfile
from app.rag.settings_chatbot import ChatbotSettings, WebSearchSettings
from app.schemas.chatbot import (
    CHATBOT_ANSWER_RESPONSE_SCHEMA,
    CHATBOT_SEARCH_QUERY_SCHEMA,
    ChatbotAnswerRequest,
)
from app.services.chatbot_answer_service import ChatbotAnswerService


PROFILE = ApartmentProfile(
    "도곡렉슬", "서울 강남구", "강남구", "도곡동", 3002, "2006-01", 4000
)


class RecordingProvider:
    def __init__(self, payload=None, error=None):
        self.payload = payload
        self.error = error
        self.calls = []

    async def complete_json(self, system_prompt, user_prompt):
        self.calls.append((system_prompt, user_prompt))
        if self.error is not None:
            raise self.error
        return self.payload


class RecordingWebProvider:
    def __init__(self, results=None):
        self.results = results or []
        self.calls = []

    async def search(self, query, top_k):
        self.calls.append((query, top_k))
        return self.results[:top_k]


def _service(rewrite_provider, *, rewrite_enabled=True):
    return ChatbotAnswerService(
        provider=RecordingProvider({"answer": "답변", "usedSources": []}),
        rewrite_provider=rewrite_provider,
        embedder=object(),
        connection_factory=lambda: None,
        settings=ChatbotSettings(
            search_rewrite_enabled=rewrite_enabled,
            lazy_web_enabled=False,
        ),
        web_settings=WebSearchSettings(),
    )


def _request():
    return ChatbotAnswerRequest(
        apartmentId=221,
        apartmentName="도곡렉슬",
        question="도곡렉슬 그 뭐야 재개발 단지 뭐있나 확인해봐",
    )


@pytest.mark.asyncio
async def test_rewrite_success_and_prompt_context() -> None:
    rewrite = RecordingProvider({"query": "강남구 도곡동 재개발"})
    service = _service(rewrite)
    web = RecordingWebProvider()
    service.web_search_provider = web
    service._fetch_profile = lambda request: (PROFILE, False)
    service._retrieve = lambda request: []
    response = await service.answer(_request())
    assert response.basisType == "NONE"
    assert web.calls == [("강남구 도곡동 재개발", 50)]
    assert rewrite.calls[0][0] == SEARCH_QUERY_SYSTEM_PROMPT
    assert all(
        value in rewrite.calls[0][1]
        for value in ("도곡렉슬", "강남구", "도곡동", "그 뭐야")
    )


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("payload", "error", "error_type"),
    [
        (None, LlmProviderError("secret-url"), "LlmProviderError"),
        ({"wrong": "shape"}, None, "ValidationError"),
        ({"query": "   "}, None, "ValidationError"),
    ],
)
async def test_rewrite_failure_falls_back_and_answer_stays_normal(
    payload, error, error_type, caplog
) -> None:
    rewrite = RecordingProvider(payload, error)
    service = _service(rewrite)
    web = RecordingWebProvider()
    service.web_search_provider = web
    service._fetch_profile = lambda request: (PROFILE, False)
    service._retrieve = lambda request: []
    with caplog.at_level("WARNING"):
        response = await service.answer(_request())
    assert response.basisType == "NONE"
    assert web.calls == [
        (
            "강남구 도곡동 도곡렉슬 그 뭐야 재개발 단지 뭐있나 확인해봐",
            50,
        )
    ]
    assert len(rewrite.calls) == 1
    assert error_type in caplog.text
    assert "secret-url" not in caplog.text


@pytest.mark.asyncio
async def test_disabled_or_missing_provider_uses_fallback_without_call() -> None:
    rewrite = RecordingProvider({"query": "rewritten"})
    assert await _service(rewrite, rewrite_enabled=False)._rewrite_query(
        _request(), PROFILE, "A query"
    ) == "A query"
    assert rewrite.calls == []
    assert await _service(None)._rewrite_query(
        _request(), PROFILE, "A query"
    ) == "A query"


@pytest.mark.asyncio
async def test_rewrite_and_answer_providers_are_distinct(monkeypatch) -> None:
    from app.api import chatbot

    rewrite = RecordingProvider({"query": "강남구 도곡동 재개발"})
    answer = RecordingProvider({"answer": "정상 답변", "usedSources": []})
    providers = [rewrite, answer]
    factory_calls = []

    def create_provider(schema, *, purpose):
        factory_calls.append((schema, purpose))
        return providers.pop(0)

    monkeypatch.setattr(chatbot, "create_llm_provider", create_provider)
    monkeypatch.setattr(chatbot.RagSettings, "from_env", lambda: object())
    monkeypatch.setattr(chatbot, "get_shared_embedder", lambda settings: object())
    monkeypatch.setattr(chatbot, "connect", lambda settings: None)
    service = chatbot._build_service()
    assert factory_calls == [
        (CHATBOT_SEARCH_QUERY_SCHEMA, "CHATBOT"),
        (CHATBOT_ANSWER_RESPONSE_SCHEMA, "CHATBOT"),
    ]
    assert service.rewrite_provider is rewrite
    assert service.provider is answer

    service.web_search_provider = RecordingWebProvider()
    service._fetch_profile = lambda request: (PROFILE, False)
    service._retrieve = lambda request: []
    response = await service.answer(_request())
    assert response.answer == "정상 답변"
    assert len(rewrite.calls) == 1
    assert len(answer.calls) == 1
    assert rewrite.calls[0][0] == SEARCH_QUERY_SYSTEM_PROMPT
    assert answer.calls[0][0] != SEARCH_QUERY_SYSTEM_PROMPT


def test_rewrite_prompt_keeps_topic_scope_rules() -> None:
    assert "단지 단위 주제" in SEARCH_QUERY_SYSTEM_PROMPT
    assert "지역 단위 주제" in SEARCH_QUERY_SYSTEM_PROMPT
    assert "아파트명을 제외" in SEARCH_QUERY_SYSTEM_PROMPT
