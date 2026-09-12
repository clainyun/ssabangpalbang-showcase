"""OpenAI-compatible provider and schema-dialect tests for AI-009."""

from __future__ import annotations

import json

import httpx
import pytest

from app.providers.llm_provider import (
    LlmProviderError,
    OpenAiCompatibleProvider,
    to_openai_json_schema,
)
from app.schemas.chatbot import CHATBOT_ANSWER_RESPONSE_SCHEMA


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


def _provider(response_schema=CHATBOT_ANSWER_RESPONSE_SCHEMA, **kwargs):
    return OpenAiCompatibleProvider(
        base_url=kwargs.get("base_url", "https://openai.example/v1/"),
        api_key=kwargs.get("api_key", "test-secret-key"),
        model=kwargs.get("model", "gpt-5.4-mini"),
        response_schema=response_schema,
    )


def _response(content: str = '{"answer":"a","usedSources":[1]}') -> httpx.Response:
    return httpx.Response(
        200,
        json={"choices": [{"message": {"content": content}}]},
    )


def _body(request: httpx.Request) -> dict:
    return json.loads(request.content)


@pytest.mark.asyncio
async def test_complete_json_parses_normal_response(monkeypatch):
    transport = _FakeTransport(lambda request: _response())
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("system", "user")

    assert result == {"answer": "a", "usedSources": [1]}


@pytest.mark.asyncio
async def test_structured_output_sends_strict_json_schema(monkeypatch):
    monkeypatch.setenv("AI_STRUCTURED_OUTPUT", "true")
    transport = _FakeTransport(lambda request: _response())
    _patch_async_client(monkeypatch, transport)

    await _provider().complete_json("system", "user")

    response_format = _body(transport.requests[0])["response_format"]
    assert response_format["type"] == "json_schema"
    assert response_format["json_schema"]["strict"] is True
    assert response_format["json_schema"]["schema"] == to_openai_json_schema(
        CHATBOT_ANSWER_RESPONSE_SCHEMA
    )


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("response_schema", "structured_output"),
    [(None, "true"), (CHATBOT_ANSWER_RESPONSE_SCHEMA, "false")],
)
async def test_without_enabled_schema_uses_json_object(
    monkeypatch,
    response_schema,
    structured_output,
):
    monkeypatch.setenv("AI_STRUCTURED_OUTPUT", structured_output)
    transport = _FakeTransport(lambda request: _response())
    _patch_async_client(monkeypatch, transport)

    await _provider(response_schema=response_schema).complete_json("system", "user")

    assert _body(transport.requests[0])["response_format"] == {"type": "json_object"}


@pytest.mark.asyncio
async def test_temperature_is_omitted_by_default(monkeypatch):
    monkeypatch.delenv("AI_TEMPERATURE", raising=False)
    transport = _FakeTransport(lambda request: _response())
    _patch_async_client(monkeypatch, transport)

    await _provider().complete_json("system", "user")

    assert "temperature" not in _body(transport.requests[0])


@pytest.mark.asyncio
async def test_temperature_is_sent_when_configured(monkeypatch):
    monkeypatch.setenv("AI_TEMPERATURE", "0.2")
    transport = _FakeTransport(lambda request: _response())
    _patch_async_client(monkeypatch, transport)

    await _provider().complete_json("system", "user")

    assert _body(transport.requests[0])["temperature"] == 0.2


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "bad",
    ["abc", "0.2.", "", " 0.2 x", "nan", "inf", "-inf"],
)
async def test_malformed_temperature_fails_as_provider_error(monkeypatch, bad):
    # A bare ValueError would escape ChatbotAnswerService and become a 500;
    # the contract is PROVIDER_FAILED -> 502.
    monkeypatch.setenv("AI_TEMPERATURE", bad)
    transport = _FakeTransport(lambda request: _response())
    _patch_async_client(monkeypatch, transport)

    if not bad.strip():
        await _provider().complete_json("system", "user")
        assert "temperature" not in _body(transport.requests[0])
        return

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("system", "user")

    assert "AI_TEMPERATURE" in str(exc.value)
    assert transport.requests == []


@pytest.mark.asyncio
async def test_server_error_retries_then_preserves_status(monkeypatch):
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "2")
    transport = _FakeTransport(lambda request: httpx.Response(500))
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("system", "user")

    assert str(exc.value) == (
        "OpenAI-compatible provider server error (status=500)"
    )
    assert exc.value.status_code == 500
    assert len(transport.requests) == 3


@pytest.mark.asyncio
async def test_client_error_does_not_retry(monkeypatch):
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "2")
    transport = _FakeTransport(lambda request: httpx.Response(400))
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("system", "user")

    assert str(exc.value) == (
        "OpenAI-compatible provider client error (status=400)"
    )
    assert exc.value.status_code == 400
    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_server_error_then_success(monkeypatch):
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "2")
    responses = iter([httpx.Response(500), _response()])
    transport = _FakeTransport(lambda request: next(responses))
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("system", "user")

    assert result["answer"] == "a"
    assert len(transport.requests) == 2


@pytest.mark.asyncio
async def test_timeout_retries(monkeypatch):
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "1")

    def timeout(request):
        raise httpx.ReadTimeout("timed out", request=request)

    transport = _FakeTransport(timeout)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError, match="LLM provider timeout"):
        await _provider().complete_json("system", "user")

    assert len(transport.requests) == 2


@pytest.mark.asyncio
async def test_empty_content_retries(monkeypatch):
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "1")
    transport = _FakeTransport(lambda request: _response(""))
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError, match="LLM returned empty content"):
        await _provider().complete_json("system", "user")

    assert len(transport.requests) == 2


@pytest.mark.asyncio
async def test_markdown_fenced_json_is_parsed(monkeypatch):
    transport = _FakeTransport(
        lambda request: _response('```json\n{"answer":"a","usedSources":[]}\n```')
    )
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("system", "user")

    assert result == {"answer": "a", "usedSources": []}


@pytest.mark.asyncio
@pytest.mark.parametrize("missing", ["base_url", "api_key", "model"])
async def test_missing_configuration_fails_without_http(monkeypatch, missing):
    for name in ("AI_API_BASE_URL", "AI_API_KEY", "AI_MODEL"):
        monkeypatch.delenv(name, raising=False)
    values = {
        "base_url": "https://openai.example/v1",
        "api_key": "key",
        "model": "model",
    }
    values[missing] = ""
    transport = _FakeTransport(lambda request: _response())
    _patch_async_client(monkeypatch, transport)
    provider = _provider(**values)

    assert provider.is_configured() is False
    with pytest.raises(LlmProviderError, match="not configured"):
        await provider.complete_json("system", "user")
    assert transport.requests == []


@pytest.mark.asyncio
async def test_request_headers_and_url(monkeypatch):
    transport = _FakeTransport(lambda request: _response())
    _patch_async_client(monkeypatch, transport)

    await _provider().complete_json("system", "user")

    request = transport.requests[0]
    assert request.url == httpx.URL("https://openai.example/v1/chat/completions")
    assert request.headers["Authorization"] == "Bearer test-secret-key"
    assert request.headers["Content-Type"] == "application/json"


def test_provider_repr_does_not_expose_api_key():
    assert "test-secret-key" not in repr(_provider())


def test_schema_conversion_matches_strict_openai_shape_without_mutation():
    original = json.loads(json.dumps(CHATBOT_ANSWER_RESPONSE_SCHEMA))

    converted = to_openai_json_schema(CHATBOT_ANSWER_RESPONSE_SCHEMA)

    assert converted == {
        "type": "object",
        "properties": {
            "answer": {"type": "string"},
            "usedSources": {"type": "array", "items": {"type": "integer"}},
            "usedProfile": {"type": "array", "items": {"type": "string"}},
        },
        "required": ["answer", "usedSources", "usedProfile"],
        "additionalProperties": False,
    }
    assert CHATBOT_ANSWER_RESPONSE_SCHEMA == original
    assert CHATBOT_ANSWER_RESPONSE_SCHEMA["type"] == "OBJECT"


def test_schema_conversion_recurses_into_nested_objects_and_array_items():
    schema = {
        "type": "OBJECT",
        "properties": {
            "nested": {
                "type": "OBJECT",
                "properties": {"value": {"type": "STRING"}},
            },
            "rows": {
                "type": "ARRAY",
                "items": {
                    "type": "OBJECT",
                    "properties": {"count": {"type": "NUMBER"}},
                },
            },
        },
    }

    converted = to_openai_json_schema(schema)

    nested = converted["properties"]["nested"]
    assert nested["required"] == ["value"]
    assert nested["additionalProperties"] is False
    item = converted["properties"]["rows"]["items"]
    assert item["type"] == "object"
    assert item["required"] == ["count"]
    assert item["additionalProperties"] is False


def test_schema_conversion_rejects_unknown_type():
    with pytest.raises(ValueError, match="TIMESTAMP"):
        to_openai_json_schema({"type": "TIMESTAMP"})


def test_schema_conversion_requires_every_property():
    converted = to_openai_json_schema(
        {"type": "OBJECT", "properties": {"x": {"type": "BOOLEAN"}}}
    )

    assert converted["required"] == ["x"]
