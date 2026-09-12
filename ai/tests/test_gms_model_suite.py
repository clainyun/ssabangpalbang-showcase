"""Protocol and configuration tests for the explicit GMS model suite."""

from __future__ import annotations

import json
from dataclasses import replace

import httpx
import pytest

import app.providers.gms_model_suite as gms_model_suite
from app.providers.gms_model_suite import (
    ANTHROPIC_MODEL,
    GEMINI_MODEL,
    OPENAI_MODEL,
    GmsModelSuiteSettings,
    create_gms_model_suite,
    create_gms_production_fit_suite,
)
from app.providers.llm_provider import (
    AnthropicMessagesProvider,
    GmsGeminiProvider,
    LlmProviderError,
    OpenAiCompatibleProvider,
    create_llm_provider,
)
from app.schemas.chatbot import CHATBOT_ANSWER_RESPONSE_SCHEMA


class _FakeTransport(httpx.AsyncBaseTransport):
    def __init__(self, handler):
        self.handler = handler
        self.requests: list[httpx.Request] = []

    async def handle_async_request(
        self,
        request: httpx.Request,
    ) -> httpx.Response:
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


def _set_suite_env(monkeypatch) -> None:
    monkeypatch.setenv("GMS_KEY", "one-shared-secret")
    monkeypatch.setenv(
        "GMS_GEMINI_ENDPOINT_URL",
        "https://ai-gateway.example.com/testing/gemini/"
        "v1beta/models/gemini-3.5-flash:generateContent",
    )
    monkeypatch.setenv("GMS_GEMINI_MODEL", GEMINI_MODEL)
    monkeypatch.setenv(
        "GMS_OPENAI_ENDPOINT_URL",
        "https://ai-gateway.example.com/testing/openai/v1/chat/completions",
    )
    monkeypatch.setenv("GMS_OPENAI_MODEL", OPENAI_MODEL)
    monkeypatch.setenv(
        "GMS_ANTHROPIC_ENDPOINT_URL",
        "https://ai-gateway.example.com/testing/anthropic/v1/messages",
    )
    monkeypatch.setenv("GMS_ANTHROPIC_MODEL", ANTHROPIC_MODEL)
    monkeypatch.setenv("GMS_ANTHROPIC_VERSION", "2023-06-01")
    monkeypatch.setenv("GMS_ANTHROPIC_MAX_TOKENS", "4096")
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "0")


def _request_body(request: httpx.Request) -> dict:
    return json.loads(request.content)


def test_suite_builds_three_models_with_one_in_memory_key(monkeypatch):
    _set_suite_env(monkeypatch)

    suite = create_gms_model_suite()

    assert set(suite) == {
        GEMINI_MODEL,
        OPENAI_MODEL,
        ANTHROPIC_MODEL,
    }
    assert isinstance(suite[GEMINI_MODEL], GmsGeminiProvider)
    assert isinstance(suite[OPENAI_MODEL], OpenAiCompatibleProvider)
    assert isinstance(suite[ANTHROPIC_MODEL], AnthropicMessagesProvider)
    assert {
        provider.api_key for provider in suite.values()
    } == {"one-shared-secret"}
    assert (
        suite[GEMINI_MODEL].api_key
        is suite[OPENAI_MODEL].api_key
        is suite[ANTHROPIC_MODEL].api_key
    )


def test_settings_repr_does_not_expose_shared_key(monkeypatch):
    _set_suite_env(monkeypatch)

    settings = GmsModelSuiteSettings.from_env()

    assert "one-shared-secret" not in repr(settings)


def test_settings_load_direct_python_dotenv_before_reading(monkeypatch):
    loaded = False

    def fake_load_ai_env():
        nonlocal loaded
        loaded = True
        _set_suite_env(monkeypatch)

    monkeypatch.setattr(gms_model_suite, "load_ai_env", fake_load_ai_env)

    settings = GmsModelSuiteSettings.from_env()

    assert loaded is True
    assert settings.api_key == "one-shared-secret"


def test_settings_from_mapping_uses_only_the_explicit_source(monkeypatch):
    _set_suite_env(monkeypatch)
    values = {
        "GMS_KEY": "file-only-secret",
        "GMS_GEMINI_ENDPOINT_URL": (
            "https://ai-gateway.example.com/testing/gemini/"
            "v1beta/models/gemini-3.5-flash:generateContent"
        ),
        "GMS_OPENAI_ENDPOINT_URL": (
            "https://ai-gateway.example.com/testing/openai/v1/chat/completions"
        ),
        "GMS_ANTHROPIC_ENDPOINT_URL": (
            "https://ai-gateway.example.com/testing/anthropic/v1/messages"
        ),
    }

    settings = GmsModelSuiteSettings.from_mapping(values)

    assert settings.api_key == "file-only-secret"
    assert settings.gemini_model == GEMINI_MODEL
    assert settings.openai_model == OPENAI_MODEL
    assert settings.anthropic_model == ANTHROPIC_MODEL
    assert settings.anthropic_max_tokens == 4096


def test_injected_whitespace_only_setting_is_rejected(monkeypatch):
    _set_suite_env(monkeypatch)
    settings = replace(
        GmsModelSuiteSettings.from_env(),
        openai_endpoint_url="   ",
    )

    with pytest.raises(LlmProviderError) as exc:
        create_gms_model_suite(settings)

    assert "GMS_OPENAI_ENDPOINT_URL" in str(exc.value)
    assert "one-shared-secret" not in str(exc.value)


@pytest.mark.parametrize(
    ("setting_name", "unsafe_url"),
    [
        (
            "GMS_OPENAI_ENDPOINT_URL",
            "http://ai-gateway.example.com/v1/chat/completions",
        ),
        (
            "GMS_OPENAI_ENDPOINT_URL",
            "https://attacker.example/v1/chat/completions",
        ),
        (
            "GMS_ANTHROPIC_ENDPOINT_URL",
            "https://ai-gateway.example.com.attacker.example/v1/messages",
        ),
    ],
)
def test_suite_rejects_endpoint_that_could_disclose_key(
    monkeypatch,
    setting_name,
    unsafe_url,
):
    _set_suite_env(monkeypatch)
    monkeypatch.setenv(setting_name, unsafe_url)

    with pytest.raises(LlmProviderError) as exc:
        create_gms_model_suite()

    assert setting_name in str(exc.value)
    assert "one-shared-secret" not in str(exc.value)


def test_gemini_endpoint_must_match_configured_model(monkeypatch):
    _set_suite_env(monkeypatch)
    monkeypatch.setenv("GMS_GEMINI_MODEL", "gemini-other")

    with pytest.raises(LlmProviderError) as exc:
        create_gms_model_suite()

    assert "GMS_GEMINI_ENDPOINT_URL" in str(exc.value)


@pytest.mark.parametrize(
    "missing_name",
    [
        "GMS_KEY",
        "GMS_GEMINI_ENDPOINT_URL",
        "GMS_OPENAI_ENDPOINT_URL",
        "GMS_ANTHROPIC_ENDPOINT_URL",
    ],
)
def test_missing_required_configuration_fails_before_http(
    monkeypatch,
    missing_name,
):
    _set_suite_env(monkeypatch)
    monkeypatch.setenv(missing_name, "")
    called = False

    class _UnexpectedClient:
        def __init__(self, *args, **kwargs):
            nonlocal called
            called = True

    monkeypatch.setattr(httpx, "AsyncClient", _UnexpectedClient)

    with pytest.raises(LlmProviderError) as exc:
        create_gms_model_suite()

    assert missing_name in str(exc.value)
    assert "one-shared-secret" not in str(exc.value)
    assert called is False


@pytest.mark.parametrize("raw", ["0", "-1", "not-a-number"])
def test_anthropic_max_tokens_must_be_positive_integer(monkeypatch, raw):
    _set_suite_env(monkeypatch)
    monkeypatch.setenv("GMS_ANTHROPIC_MAX_TOKENS", raw)

    with pytest.raises(LlmProviderError) as exc:
        create_gms_model_suite()

    assert str(exc.value) == (
        "GMS_ANTHROPIC_MAX_TOKENS must be a positive integer"
    )
    assert raw not in str(exc.value)


@pytest.mark.asyncio
async def test_three_adapters_use_native_protocols_and_parse_json(monkeypatch):
    _set_suite_env(monkeypatch)

    def handler(request: httpx.Request) -> httpx.Response:
        if "/testing/gemini/" in request.url.path:
            return httpx.Response(
                200,
                json={
                    "candidates": [
                        {
                            "content": {
                                "parts": [
                                    {
                                        "text": (
                                            '{"answer":"gemini",'
                                            '"usedSources":[]}'
                                        )
                                    }
                                ]
                            },
                            "finishReason": "STOP",
                        }
                    ]
                },
            )
        if "/testing/openai/" in request.url.path:
            return httpx.Response(
                200,
                json={
                    "choices": [
                        {
                            "message": {
                                "content": (
                                    '{"answer":"openai",'
                                    '"usedSources":[]}'
                                )
                            }
                        }
                    ]
                },
            )
        return httpx.Response(
            200,
            json={
                "content": [
                    {"type": "thinking", "thinking": "private"},
                    {"type": "text", "text": '{"answer":"claude",'},
                    {"type": "text", "text": '"usedSources":[]}'},
                ],
                "stop_reason": "end_turn",
            },
        )

    transport = _FakeTransport(handler)
    _patch_async_client(monkeypatch, transport)
    suite = create_gms_model_suite()

    results = {
        model: await provider.complete_json("developer instruction", "question")
        for model, provider in suite.items()
    }

    assert results[GEMINI_MODEL]["answer"] == "gemini"
    assert results[OPENAI_MODEL]["answer"] == "openai"
    assert results[ANTHROPIC_MODEL]["answer"] == "claude"
    assert len(transport.requests) == 3

    requests = {request.url.path: request for request in transport.requests}
    gemini_request = next(
        request
        for path, request in requests.items()
        if "/testing/gemini/" in path
    )
    assert str(gemini_request.url) == (
        "https://ai-gateway.example.com/testing/gemini/"
        "v1beta/models/gemini-3.5-flash:generateContent"
    )
    assert gemini_request.headers["x-goog-api-key"] == "one-shared-secret"
    assert "authorization" not in gemini_request.headers
    assert _request_body(gemini_request)["generationConfig"] == {
        "temperature": 0.2,
    }

    openai_request = next(
        request
        for path, request in requests.items()
        if "/testing/openai/" in path
    )
    assert str(openai_request.url) == (
        "https://ai-gateway.example.com/testing/openai/v1/chat/completions"
    )
    assert openai_request.headers["authorization"] == (
        "Bearer one-shared-secret"
    )
    openai_body = _request_body(openai_request)
    assert openai_body["model"] == OPENAI_MODEL
    assert "response_format" not in openai_body
    assert openai_body["messages"][0] == {
        "role": "developer",
        "content": "developer instruction",
    }

    anthropic_request = next(
        request
        for path, request in requests.items()
        if "/testing/anthropic/" in path
    )
    assert str(anthropic_request.url) == (
        "https://ai-gateway.example.com/testing/anthropic/v1/messages"
    )
    assert anthropic_request.headers["x-api-key"] == "one-shared-secret"
    assert anthropic_request.headers["anthropic-version"] == "2023-06-01"
    anthropic_body = _request_body(anthropic_request)
    assert anthropic_body == {
        "model": ANTHROPIC_MODEL,
        "max_tokens": 4096,
        "temperature": 0.2,
        "system": "developer instruction",
        "messages": [{"role": "user", "content": "question"}],
    }


@pytest.mark.asyncio
async def test_anthropic_default_keeps_temperature_unset(monkeypatch):
    transport = _FakeTransport(
        lambda request: httpx.Response(
            200,
            json={
                "content": [
                    {"type": "text", "text": '{"answer":"ok"}'}
                ],
                "stop_reason": "end_turn",
            },
        )
    )
    _patch_async_client(monkeypatch, transport)
    provider = AnthropicMessagesProvider(
        endpoint_url="https://anthropic.test/v1/messages",
        api_key="secret-key",
        model=ANTHROPIC_MODEL,
    )

    await provider.complete_json("system", "user")

    assert "temperature" not in _request_body(transport.requests[0])


@pytest.mark.asyncio
async def test_legacy_openai_constructor_keeps_base_url_and_system_role(
    monkeypatch,
):
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
        base_url="https://legacy.test/v1",
        api_key="legacy-key",
        model="legacy-model",
    )

    result = await provider.complete_json("system", "user")

    assert result == {"answer": "ok"}
    assert str(transport.requests[0].url) == (
        "https://legacy.test/v1/chat/completions"
    )
    assert _request_body(transport.requests[0])["messages"][0]["role"] == (
        "system"
    )


@pytest.mark.asyncio
async def test_openai_malformed_response_uses_provider_error(monkeypatch):
    transport = _FakeTransport(
        lambda request: httpx.Response(200, content=b"not-json")
    )
    _patch_async_client(monkeypatch, transport)
    provider = OpenAiCompatibleProvider(
        endpoint_url="https://openai.test/v1/chat/completions",
        api_key="secret-key",
        model=OPENAI_MODEL,
    )

    with pytest.raises(LlmProviderError) as exc:
        await provider.complete_json("system", "user")

    assert str(exc.value) == "LLM returned invalid JSON"
    assert "secret-key" not in str(exc.value)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("finish_reason", "expected_text"),
    [
        ("length", "truncated content"),
        ("content_filter", "blocked content"),
    ],
)
async def test_openai_rejects_incomplete_or_blocked_json(
    monkeypatch,
    finish_reason,
    expected_text,
):
    transport = _FakeTransport(
        lambda request: httpx.Response(
            200,
            json={
                "choices": [
                    {
                        "finish_reason": finish_reason,
                        "message": {"content": '{"answer":"partial"}'},
                    }
                ]
            },
        )
    )
    _patch_async_client(monkeypatch, transport)
    provider = OpenAiCompatibleProvider(
        endpoint_url="https://openai.test/v1/chat/completions",
        api_key="secret-key",
        model=OPENAI_MODEL,
    )

    with pytest.raises(LlmProviderError) as exc:
        await provider.complete_json("system", "user")

    assert expected_text in str(exc.value)


@pytest.mark.asyncio
async def test_gemini_rejects_parseable_max_tokens_response(monkeypatch):
    transport = _FakeTransport(
        lambda request: httpx.Response(
            200,
            json={
                "candidates": [
                    {
                        "finishReason": "MAX_TOKENS",
                        "content": {
                            "parts": [{"text": '{"answer":"partial"}'}]
                        },
                    }
                ]
            },
        )
    )
    _patch_async_client(monkeypatch, transport)
    provider = GmsGeminiProvider(
        endpoint_url=(
            "https://gemini.test/v1beta/models/"
            "gemini-3.5-flash:generateContent"
        ),
        api_key="secret-key",
        model=GEMINI_MODEL,
    )

    with pytest.raises(LlmProviderError) as exc:
        await provider.complete_json("system", "user")

    assert "truncated content" in str(exc.value)
    assert "finishReason=MAX_TOKENS" in str(exc.value)


def test_production_factory_still_uses_legacy_ai_settings(monkeypatch):
    _set_suite_env(monkeypatch)
    monkeypatch.setenv("AI_PROVIDER", "gms-gemini")
    monkeypatch.setenv("AI_API_BASE_URL", "https://legacy-gemini.test")
    monkeypatch.setenv("AI_API_KEY", "legacy-key")
    monkeypatch.setenv("AI_MODEL", "legacy-gemini")

    provider = create_llm_provider()

    assert isinstance(provider, GmsGeminiProvider)
    assert provider.base_url == "https://legacy-gemini.test"
    assert provider.api_key == "legacy-key"
    assert provider.model == "legacy-gemini"
    assert provider.endpoint_url is None


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("status_code", "expected_message"),
    [
        (401, "GMS Anthropic client error (status=401)"),
        (503, "GMS Anthropic server error (status=503)"),
    ],
)
async def test_anthropic_http_errors_preserve_status(
    monkeypatch,
    status_code,
    expected_message,
):
    transport = _FakeTransport(
        lambda request: httpx.Response(status_code, json={"error": "hidden"})
    )
    _patch_async_client(monkeypatch, transport)
    provider = AnthropicMessagesProvider(
        endpoint_url="https://anthropic.test/v1/messages",
        api_key="secret-key",
        model=ANTHROPIC_MODEL,
    )

    with pytest.raises(LlmProviderError) as exc:
        await provider.complete_json("secret system", "secret user")

    assert str(exc.value) == expected_message
    assert exc.value.status_code == status_code
    assert "secret-key" not in str(exc.value)


@pytest.mark.asyncio
async def test_anthropic_timeout_is_provider_error(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("hidden timeout", request=request)

    transport = _FakeTransport(handler)
    _patch_async_client(monkeypatch, transport)
    provider = AnthropicMessagesProvider(
        endpoint_url="https://anthropic.test/v1/messages",
        api_key="secret-key",
        model=ANTHROPIC_MODEL,
    )

    with pytest.raises(LlmProviderError) as exc:
        await provider.complete_json("secret system", "secret user")

    assert str(exc.value) == "GMS Anthropic timeout"
    assert "secret" not in str(exc.value)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("payload", "expected_message"),
    [
        ([], "GMS Anthropic response root must be an object"),
        ({}, "GMS Anthropic returned invalid content"),
        (
            {
                "content": [{"type": "thinking", "thinking": "private"}],
                "stop_reason": "end_turn",
            },
            "GMS Anthropic returned empty content",
        ),
        (
            {
                "content": [{"type": "text", "text": "not-json"}],
                "stop_reason": "end_turn",
            },
            "GMS Anthropic returned invalid JSON",
        ),
        (
            {
                "content": [{"type": "text", "text": '{"answer":"cut"}'}],
                "stop_reason": "max_tokens",
            },
            "GMS Anthropic returned truncated content "
            "(stop_reason=max_tokens)",
        ),
    ],
)
async def test_anthropic_rejects_malformed_empty_or_truncated_content(
    monkeypatch,
    payload,
    expected_message,
):
    transport = _FakeTransport(
        lambda request: httpx.Response(200, json=payload)
    )
    _patch_async_client(monkeypatch, transport)
    provider = AnthropicMessagesProvider(
        endpoint_url="https://anthropic.test/v1/messages",
        api_key="secret-key",
        model=ANTHROPIC_MODEL,
    )

    with pytest.raises(LlmProviderError) as exc:
        await provider.complete_json("secret system", "secret user")

    assert str(exc.value) == expected_message
    assert "secret" not in str(exc.value)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("stop_reason", "expected_text"),
    [
        ("model_context_window_exceeded", "truncated content"),
        ("pause_turn", "generation incomplete"),
        ("refusal", "generation incomplete"),
        (None, "generation incomplete"),
    ],
)
async def test_anthropic_rejects_nonterminal_or_limited_stop_reason(
    monkeypatch,
    stop_reason,
    expected_text,
):
    transport = _FakeTransport(
        lambda request: httpx.Response(
            200,
            json={
                "content": [
                    {"type": "text", "text": '{"answer":"partial"}'}
                ],
                "stop_reason": stop_reason,
            },
        )
    )
    _patch_async_client(monkeypatch, transport)
    provider = AnthropicMessagesProvider(
        endpoint_url="https://anthropic.test/v1/messages",
        api_key="secret-key",
        model=ANTHROPIC_MODEL,
    )

    with pytest.raises(LlmProviderError) as exc:
        await provider.complete_json("system", "user")

    assert expected_text in str(exc.value)


@pytest.mark.asyncio
async def test_unconfigured_anthropic_does_not_call_http(monkeypatch):
    transport = _FakeTransport(
        lambda request: httpx.Response(200, json={"unexpected": True})
    )
    _patch_async_client(monkeypatch, transport)
    monkeypatch.setenv("AI_API_BASE_URL", "")
    monkeypatch.setenv("AI_API_KEY", "")
    monkeypatch.setenv("AI_MODEL", "")
    provider = AnthropicMessagesProvider(
        endpoint_url="",
        api_key="",
        model="",
    )

    with pytest.raises(LlmProviderError) as exc:
        await provider.complete_json("system", "user")

    assert str(exc.value) == "GMS Anthropic provider is not configured"
    assert transport.requests == []


@pytest.mark.asyncio
async def test_production_fit_suite_pins_native_json_modes(monkeypatch):
    _set_suite_env(monkeypatch)
    monkeypatch.setenv("AI_STRUCTURED_OUTPUT", "false")
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "9")

    def handler(request: httpx.Request) -> httpx.Response:
        if "/testing/gemini/" in request.url.path:
            return httpx.Response(
                200,
                json={
                    "candidates": [
                        {
                            "content": {
                                "parts": [
                                    {
                                        "text": (
                                            '{"answer":"gemini",'
                                            '"usedSources":[]}'
                                        )
                                    }
                                ]
                            },
                            "finishReason": "STOP",
                        }
                    ]
                },
            )
        if "/testing/openai/" in request.url.path:
            return httpx.Response(
                200,
                json={
                    "choices": [
                        {
                            "finish_reason": "stop",
                            "message": {
                                "content": (
                                    '{"answer":"openai",'
                                    '"usedSources":[]}'
                                )
                            },
                        }
                    ]
                },
            )
        return httpx.Response(
            200,
            json={
                "content": [
                    {
                        "type": "text",
                        "text": '{"answer":"claude","usedSources":[]}',
                    }
                ],
                "stop_reason": "end_turn",
            },
        )

    transport = _FakeTransport(handler)
    _patch_async_client(monkeypatch, transport)
    suite = create_gms_production_fit_suite(
        GmsModelSuiteSettings.from_env(),
        response_schema=CHATBOT_ANSWER_RESPONSE_SCHEMA,
    )

    for provider in suite.values():
        await provider.complete_json("developer instruction", "question")

    gemini = next(
        request
        for request in transport.requests
        if "/testing/gemini/" in request.url.path
    )
    assert _request_body(gemini)["generationConfig"] == {
        "temperature": 0.2,
        "responseMimeType": "application/json",
        "responseSchema": CHATBOT_ANSWER_RESPONSE_SCHEMA,
    }
    assert suite[GEMINI_MODEL].json_retry_count == 0

    openai = next(
        request
        for request in transport.requests
        if "/testing/openai/" in request.url.path
    )
    openai_body = _request_body(openai)
    assert openai_body["response_format"] == {"type": "json_object"}
    assert openai_body["messages"][0]["role"] == "developer"

    anthropic = next(
        request
        for request in transport.requests
        if "/testing/anthropic/" in request.url.path
    )
    anthropic_body = _request_body(anthropic)
    assert anthropic_body["max_tokens"] == 4096
    assert anthropic_body["temperature"] == 0.2
    assert "response_format" not in anthropic_body


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "provider",
    [
        GmsGeminiProvider(
            endpoint_url="https://provider.test/gemini",
            api_key="secret",
            model=GEMINI_MODEL,
            json_retry_count=0,
        ),
        OpenAiCompatibleProvider(
            endpoint_url="https://provider.test/openai",
            api_key="secret",
            model=OPENAI_MODEL,
        ),
        AnthropicMessagesProvider(
            endpoint_url="https://provider.test/anthropic",
            api_key="secret",
            model=ANTHROPIC_MODEL,
        ),
    ],
)
async def test_provider_redirect_is_not_a_success(monkeypatch, provider):
    transport = _FakeTransport(
        lambda request: httpx.Response(
            302,
            headers={"location": "https://provider.test/redirected"},
        )
    )
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await provider.complete_json("system", "user")

    assert exc.value.status_code == 302
