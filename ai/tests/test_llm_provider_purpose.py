"""Purpose-scoped LLM provider selection and isolation tests for AI-009."""

from __future__ import annotations

import inspect

import pytest

import app.providers.llm_provider as provider_module
from app.api import chatbot
from app.providers.llm_provider import (
    GmsGeminiProvider,
    LlmProviderError,
    OpenAiCompatibleProvider,
    create_llm_provider,
)
from app.schemas.chatbot import CHATBOT_ANSWER_RESPONSE_SCHEMA


def _global_gemini(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "gms-gemini")
    monkeypatch.setenv("AI_API_BASE_URL", "https://gemini.example")
    monkeypatch.setenv("AI_API_KEY", "gemini-key")
    monkeypatch.setenv("AI_MODEL", "gemini-3.5-flash")


def test_chatbot_without_override_falls_back_to_global(monkeypatch):
    _global_gemini(monkeypatch)
    monkeypatch.delenv("CHATBOT_AI_PROVIDER", raising=False)

    provider = create_llm_provider(purpose="CHATBOT")

    assert isinstance(provider, GmsGeminiProvider)


def test_chatbot_provider_override_selects_openai(monkeypatch):
    _global_gemini(monkeypatch)
    monkeypatch.setenv("CHATBOT_AI_PROVIDER", "openai-compatible")

    provider = create_llm_provider(purpose="CHATBOT")

    assert isinstance(provider, OpenAiCompatibleProvider)


def test_purpose_none_preserves_global_provider(monkeypatch):
    _global_gemini(monkeypatch)
    monkeypatch.setenv("CHATBOT_AI_PROVIDER", "openai-compatible")

    provider = create_llm_provider(purpose=None)

    assert isinstance(provider, GmsGeminiProvider)


def test_chatbot_model_override_is_isolated(monkeypatch):
    _global_gemini(monkeypatch)
    monkeypatch.setenv("CHATBOT_AI_MODEL", "gpt-5.4-mini")

    assert create_llm_provider(purpose="CHATBOT").model == "gpt-5.4-mini"
    assert create_llm_provider(purpose=None).model == "gemini-3.5-flash"


def test_chatbot_base_url_and_key_overrides(monkeypatch):
    _global_gemini(monkeypatch)
    monkeypatch.setenv("CHATBOT_AI_API_BASE_URL", "https://openai.example/v1")
    monkeypatch.setenv("CHATBOT_AI_API_KEY", "chatbot-key")

    provider = create_llm_provider(purpose="CHATBOT")

    assert provider.base_url == "https://openai.example/v1"
    assert provider.api_key == "chatbot-key"


@pytest.mark.parametrize("scoped", ["", "   "])
def test_blank_chatbot_model_falls_back_to_global(monkeypatch, scoped):
    _global_gemini(monkeypatch)
    monkeypatch.setenv("CHATBOT_AI_MODEL", scoped)

    assert create_llm_provider(purpose="CHATBOT").model == "gemini-3.5-flash"


def test_retry_and_structured_output_settings_remain_global(monkeypatch):
    _global_gemini(monkeypatch)
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "4")
    monkeypatch.setenv("CHATBOT_AI_JSON_RETRY_COUNT", "9")
    monkeypatch.setenv("AI_STRUCTURED_OUTPUT", "true")
    monkeypatch.setenv("CHATBOT_AI_STRUCTURED_OUTPUT", "false")

    provider = create_llm_provider(
        CHATBOT_ANSWER_RESPONSE_SCHEMA,
        purpose="CHATBOT",
    )

    assert provider_module._json_retry_count() == 4
    assert provider.structured_output is True


def test_unsupported_scoped_provider_fails(monkeypatch):
    _global_gemini(monkeypatch)
    monkeypatch.setenv("CHATBOT_AI_PROVIDER", "지원안함")

    with pytest.raises(LlmProviderError, match="Unsupported AI_PROVIDER: 지원안함"):
        create_llm_provider(purpose="CHATBOT")


def test_chatbot_build_service_passes_chatbot_purpose(monkeypatch):
    captured = {}
    fake_provider = object()
    monkeypatch.setattr(chatbot.RagSettings, "from_env", lambda: object())
    monkeypatch.setattr(chatbot.ChatbotSettings, "from_env", lambda: object())
    monkeypatch.setattr(chatbot, "get_shared_embedder", lambda settings: object())
    monkeypatch.setattr(chatbot, "connect", lambda settings: object())

    def fake_create(schema, *, purpose=None):
        captured["schema"] = schema
        captured["purpose"] = purpose
        return fake_provider

    monkeypatch.setattr(chatbot, "create_llm_provider", fake_create)

    service = chatbot._build_service()

    assert service.provider is fake_provider
    assert captured == {
        "schema": CHATBOT_ANSWER_RESPONSE_SCHEMA,
        "purpose": "CHATBOT",
    }


def test_other_call_sites_do_not_pass_purpose():
    # Read through the import system, not the filesystem. CI copies only
    # ai/tests into the isolated container, so the app lives outside the
    # test file's parent directory and Path(__file__)-relative lookups fail.
    import app.api.checklist as checklist_module
    import app.messaging.report_runtime as report_module

    checklist_source = inspect.getsource(checklist_module)
    report_source = inspect.getsource(report_module)

    assert "create_llm_provider()" in checklist_source
    assert checklist_source.count("create_llm_provider()") == 2
    assert "create_llm_provider()" in report_source
    assert "purpose=" not in checklist_source
    assert "purpose=" not in report_source
