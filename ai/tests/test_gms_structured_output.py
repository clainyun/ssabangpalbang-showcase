"""Tests for opt-in Gemini responseSchema injection."""

from __future__ import annotations

import json
from pathlib import Path

import httpx
import pytest

from app.providers.llm_provider import (
    GmsGeminiProvider,
    LlmProviderError,
    OpenAiCompatibleProvider,
    create_llm_provider,
)
from app.schemas.chatbot import CHATBOT_ANSWER_RESPONSE_SCHEMA


FIXTURE_ROOT = Path(__file__).parent / "fixtures" / "gms_gemini"


class _FakeTransport(httpx.AsyncBaseTransport):
    def __init__(self, handler):
        self.handler = handler
        self.requests: list[httpx.Request] = []

    async def handle_async_request(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        return self.handler(request)


def _patch_async_client(monkeypatch, transport: _FakeTransport) -> None:
    real_async_client = httpx.AsyncClient

    class _Client:
        def __init__(self, *args, **kwargs):
            self._client = real_async_client(transport=transport)

        async def __aenter__(self):
            await self._client.__aenter__()
            return self._client

        async def __aexit__(self, *args):
            return await self._client.__aexit__(*args)

    monkeypatch.setattr(httpx, "AsyncClient", _Client)


def _gms_response(text: str = '{"answer":"ok","usedSources":[]}') -> dict:
    return {
        "candidates": [
            {
                "content": {"parts": [{"text": text}]},
                "finishReason": "STOP",
            }
        ]
    }


def _provider(response_schema=CHATBOT_ANSWER_RESPONSE_SCHEMA):
    return GmsGeminiProvider(
        base_url="https://gms.example/gmsapi/generativelanguage.googleapis.com",
        api_key="test-key",
        model="gemini-3.5-flash",
        response_schema=response_schema,
    )


def _request_body(request: httpx.Request) -> dict:
    return json.loads(request.content)


@pytest.mark.asyncio
async def test_structured_output_true_sends_response_schema(monkeypatch):
    monkeypatch.setenv("AI_STRUCTURED_OUTPUT", "true")
    transport = _FakeTransport(
        lambda request: httpx.Response(200, json=_gms_response())
    )
    _patch_async_client(monkeypatch, transport)

    await _provider().complete_json("sys", "user")

    config = _request_body(transport.requests[0])["generationConfig"]
    assert config["responseSchema"] == CHATBOT_ANSWER_RESPONSE_SCHEMA
    assert config["responseMimeType"] == "application/json"
    assert config["temperature"] == 0.2


@pytest.mark.asyncio
async def test_structured_output_false_keeps_existing_request_shape(monkeypatch):
    monkeypatch.setenv("AI_STRUCTURED_OUTPUT", "false")
    transport = _FakeTransport(
        lambda request: httpx.Response(200, json=_gms_response())
    )
    _patch_async_client(monkeypatch, transport)

    await _provider().complete_json("sys", "user")

    config = _request_body(transport.requests[0])["generationConfig"]
    assert config == {
        "temperature": 0.2,
        "responseMimeType": "application/json",
    }


@pytest.mark.asyncio
async def test_none_schema_is_not_sent_even_when_enabled(monkeypatch):
    monkeypatch.setenv("AI_STRUCTURED_OUTPUT", "true")
    transport = _FakeTransport(
        lambda request: httpx.Response(200, json=_gms_response())
    )
    _patch_async_client(monkeypatch, transport)

    await _provider(response_schema=None).complete_json("sys", "user")

    config = _request_body(transport.requests[0])["generationConfig"]
    assert "responseSchema" not in config


def test_create_provider_without_schema_preserves_default(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "gms-gemini")
    monkeypatch.setenv("AI_API_BASE_URL", "https://example")
    monkeypatch.setenv("AI_API_KEY", "key")
    monkeypatch.setenv("AI_MODEL", "gemini-3.5-flash")

    provider = create_llm_provider()

    assert isinstance(provider, GmsGeminiProvider)
    assert provider.response_schema is None


def test_purpose_none_keeps_gemini_structured_output_behavior(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "gms-gemini")
    monkeypatch.setenv("AI_API_BASE_URL", "https://example")
    monkeypatch.setenv("AI_API_KEY", "key")
    monkeypatch.setenv("AI_MODEL", "gemini-3.5-flash")
    monkeypatch.setenv("AI_STRUCTURED_OUTPUT", "true")
    monkeypatch.setenv("CHATBOT_AI_PROVIDER", "openai-compatible")

    provider = create_llm_provider(
        CHATBOT_ANSWER_RESPONSE_SCHEMA,
        purpose=None,
    )

    assert isinstance(provider, GmsGeminiProvider)
    assert provider.response_schema == CHATBOT_ANSWER_RESPONSE_SCHEMA
    assert provider.structured_output is True


def test_create_provider_stores_injected_schema(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "gms-gemini")
    monkeypatch.setenv("AI_API_BASE_URL", "https://example")
    monkeypatch.setenv("AI_API_KEY", "key")
    monkeypatch.setenv("AI_MODEL", "gemini-3.5-flash")

    provider = create_llm_provider(CHATBOT_ANSWER_RESPONSE_SCHEMA)

    assert isinstance(provider, GmsGeminiProvider)
    assert provider.response_schema == CHATBOT_ANSWER_RESPONSE_SCHEMA


@pytest.mark.asyncio
async def test_structured_output_4xx_does_not_retry(monkeypatch):
    monkeypatch.setenv("AI_STRUCTURED_OUTPUT", "true")
    transport = _FakeTransport(
        lambda request: httpx.Response(400, json={"error": "bad schema"})
    )
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    assert str(exc.value) == "GMS Gemini client error (status=400)"
    assert exc.value.status_code == 400
    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_openai_provider_uses_standard_strict_response_schema(monkeypatch):
    transport = _FakeTransport(
        lambda request: httpx.Response(
            200,
            json={
                "choices": [
                    {"message": {"content": '{"answer":"ok"}'}}
                ]
            },
        )
    )
    _patch_async_client(monkeypatch, transport)
    provider = OpenAiCompatibleProvider(
        base_url="https://openai.example/v1",
        api_key="test-key",
        model="test-model",
        response_schema=CHATBOT_ANSWER_RESPONSE_SCHEMA,
    )

    result = await provider.complete_json("sys", "user")

    assert result == {"answer": "ok"}
    body = _request_body(transport.requests[0])
    assert body["response_format"]["type"] == "json_schema"
    assert body["response_format"]["json_schema"]["strict"] is True


def test_chatbot_response_schema_is_gemini_safe_constant():
    serialized = json.dumps(CHATBOT_ANSWER_RESPONSE_SCHEMA)

    assert "$defs" not in serialized
    assert "title" not in serialized
    assert CHATBOT_ANSWER_RESPONSE_SCHEMA["type"] == "OBJECT"


@pytest.mark.asyncio
async def test_structured_output_keeps_brace_repair_safety_net(monkeypatch):
    monkeypatch.setenv("AI_STRUCTURED_OUTPUT", "true")
    payload = json.loads(
        (FIXTURE_ROOT / "unclosed_brace_response.json").read_text(
            encoding="utf-8"
        )
    )
    transport = _FakeTransport(
        lambda request: httpx.Response(200, json=payload)
    )
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result["answer"]
    assert result["usedSources"] == []
    assert len(transport.requests) == 1
    config = _request_body(transport.requests[0])["generationConfig"]
    assert "responseSchema" in config


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("1", True),
        ("true", True),
        ("yes", True),
        ("on", True),
        ("0", False),
        ("false", False),
        ("no", False),
        ("off", False),
    ],
)
def test_structured_output_uses_env_bool(monkeypatch, raw, expected):
    monkeypatch.setenv("AI_STRUCTURED_OUTPUT", raw)

    provider = _provider()

    assert provider.structured_output is expected
