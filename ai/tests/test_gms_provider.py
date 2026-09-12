"""Unit tests for GMS Gemini provider parsing/auth shape."""

from __future__ import annotations

import json
import logging
import time
from pathlib import Path

import httpx
import jsonschema
import pytest

from app.providers.llm_provider import GmsGeminiProvider, LlmProviderError, create_llm_provider
from app.schemas.chatbot import LlmAnswerPayload, llm_answer_json_schema


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


def _provider(*, api_key: str = "test-key") -> GmsGeminiProvider:
    return GmsGeminiProvider(
        base_url="https://gms.example/gmsapi/generativelanguage.googleapis.com",
        api_key=api_key,
        model="gemini-3.5-flash",
    )


def _json_transport(payload, status_code: int = 200) -> _FakeTransport:
    return _FakeTransport(
        lambda request: httpx.Response(status_code, json=payload)
    )


def _fixture(name: str):
    return json.loads((FIXTURE_ROOT / name).read_text(encoding="utf-8"))


def _valid_payload() -> dict:
    return {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {"text": '{"answer":"ok","usedSources":[1]}'}
                    ]
                },
                "finishReason": "STOP",
            }
        ]
    }


def _unrecoverable_payload() -> dict:
    return {
        "candidates": [
            {
                "content": {
                    "parts": [{"text": '"answer":"broken"'}]
                },
                "finishReason": "STOP",
            }
        ]
    }


def _sequence_transport(*payloads) -> _FakeTransport:
    responses = list(payloads)

    def handler(request: httpx.Request) -> httpx.Response:
        payload = responses.pop(0)
        if isinstance(payload, httpx.Response):
            return payload
        return httpx.Response(200, json=payload)

    return _FakeTransport(handler)


@pytest.mark.asyncio
async def test_gms_gemini_success(monkeypatch):
    payload = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {
                            "text": json.dumps(
                                {
                                    "items": [
                                        {
                                            "category": "교통",
                                            "title": "역 접근성",
                                            "subtitle": None,
                                            "displayOrder": 1,
                                        }
                                    ]
                                },
                                ensure_ascii=False,
                            )
                        }
                    ]
                }
            }
        ]
    }

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.headers.get("x-goog-api-key") == "test-key"
        assert str(request.url).endswith(
            "/v1beta/models/gemini-3.5-flash:generateContent"
        )
        return httpx.Response(200, json=payload)

    transport = _FakeTransport(handler)
    _patch_async_client(monkeypatch, transport)

    provider = GmsGeminiProvider(
        base_url="https://gms.example/gmsapi/generativelanguage.googleapis.com",
        api_key="test-key",
        model="gemini-3.5-flash",
    )
    result = await provider.complete_json("sys", "user")
    assert result["items"][0]["title"] == "역 접근성"


@pytest.mark.asyncio
async def test_gms_gemini_4xx(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, json={"error": "unauthorized"})

    transport = _FakeTransport(handler)
    _patch_async_client(monkeypatch, transport)

    provider = GmsGeminiProvider(
        base_url="https://gms.example",
        api_key="bad",
        model="gemini-3.5-flash",
    )
    with pytest.raises(LlmProviderError) as exc:
        await provider.complete_json("sys", "user")
    assert str(exc.value) == "GMS Gemini client error (status=401)"
    assert exc.value.status_code == 401
    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_gms_gemini_parses_multipart_thinking_response(monkeypatch):
    payload = json.loads(
        (FIXTURE_ROOT / "multipart_thinking_response.json").read_text(
            encoding="utf-8"
        )
    )
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result == {
        "answer": "주차 여건은 리포트에서 확인됩니다.",
        "usedSources": [1],
    }
    assert "사용자는" not in json.dumps(result, ensure_ascii=False)


@pytest.mark.asyncio
async def test_gms_gemini_skips_non_dict_parts(monkeypatch):
    payload = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        "unexpected",
                        None,
                        {"text": '{"answer":"ok","usedSources":[]}'},
                    ]
                },
                "finishReason": "STOP",
            }
        ]
    }
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result == {"answer": "ok", "usedSources": []}


@pytest.mark.asyncio
async def test_gms_gemini_keeps_code_fence_parsing(monkeypatch):
    payload = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {
                            "text": (
                                "```json\n"
                                '{"answer":"ok","usedSources":[]}\n'
                                "```"
                            )
                        }
                    ]
                },
                "finishReason": "STOP",
            }
        ]
    }
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result == {"answer": "ok", "usedSources": []}


@pytest.mark.asyncio
async def test_gms_gemini_no_answer_text_includes_finish_reason_and_part_count(
    monkeypatch,
):
    payload = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {"thought": True, "text": "private reasoning"},
                        {"thoughtSignature": "signature"},
                    ]
                },
                "finishReason": "MAX_TOKENS",
            }
        ]
    }
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("secret system", "secret user")

    message = str(exc.value)
    assert "finishReason=MAX_TOKENS" in message
    assert "parts=2" in message
    assert "private reasoning" not in message
    assert "secret system" not in message
    assert "secret user" not in message
    assert "test-key" not in message
    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_gms_gemini_empty_candidates_includes_block_reason(monkeypatch):
    payload = {
        "candidates": [],
        "promptFeedback": {"blockReason": "SAFETY"},
    }
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    assert "blockReason=SAFETY" in str(exc.value)


@pytest.mark.asyncio
async def test_gms_gemini_empty_parts_is_provider_error(monkeypatch):
    payload = {
        "candidates": [
            {
                "content": {"parts": []},
                "finishReason": "STOP",
            }
        ]
    }
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    assert "finishReason=STOP" in str(exc.value)
    assert "parts=0" in str(exc.value)
    assert len(transport.requests) == 3


@pytest.mark.asyncio
async def test_gms_gemini_non_json_http_body_is_provider_error(monkeypatch):
    response_body = "not-json secret-response-body"
    transport = _FakeTransport(
        lambda request: httpx.Response(200, text=response_body)
    )
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("secret system", "secret user")

    message = str(exc.value)
    assert "after 3 attempts" in message
    assert f"len={len(response_body)}" in message
    assert "msg=Expecting value" in message
    assert response_body not in message
    assert "secret system" not in message
    assert "secret user" not in message
    assert "test-key" not in message
    assert len(transport.requests) == 3


@pytest.mark.asyncio
async def test_gms_gemini_5xx_preserves_status_code(monkeypatch):
    transport = _json_transport({"error": "unavailable"}, status_code=503)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    assert str(exc.value) == "GMS Gemini server error (status=503)"
    assert exc.value.status_code == 503
    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_gms_gemini_timeout_is_provider_error(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("timed out", request=request)

    transport = _FakeTransport(handler)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    assert str(exc.value) == "GMS Gemini timeout"
    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_gms_gemini_unconfigured_does_not_call_http(monkeypatch):
    monkeypatch.setenv("AI_API_KEY", "")
    transport = _json_transport({"unexpected": True})
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider(api_key="").complete_json("sys", "user")

    assert str(exc.value) == "GMS Gemini provider is not configured"
    assert transport.requests == []


@pytest.mark.asyncio
async def test_gms_gemini_joined_text_must_be_json(monkeypatch):
    payload = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {"text": "not "},
                        {"text": "json"},
                    ]
                },
                "finishReason": "STOP",
            }
        ]
    }
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    message = str(exc.value)
    assert "after 3 attempts" in message
    assert "finishReason=STOP" in message
    assert "parts=2" in message
    assert "len=8" in message
    assert "msg=Expecting value" in message
    assert len(transport.requests) == 3


@pytest.mark.asyncio
async def test_gms_gemini_retries_once_then_returns_valid_json(monkeypatch):
    malformed = _unrecoverable_payload()
    transport = _sequence_transport(malformed, _valid_payload())
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("same-system", "same-user")

    assert result == {"answer": "ok", "usedSources": [1]}
    assert len(transport.requests) == 2
    assert transport.requests[0].content == transport.requests[1].content


@pytest.mark.asyncio
async def test_gms_gemini_retries_twice_then_returns_valid_json(monkeypatch):
    malformed = _unrecoverable_payload()
    transport = _sequence_transport(malformed, malformed, _valid_payload())
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result == {"answer": "ok", "usedSources": [1]}
    assert len(transport.requests) == 3


@pytest.mark.asyncio
async def test_gms_gemini_exhausts_default_three_attempts(monkeypatch):
    malformed = _unrecoverable_payload()
    transport = _json_transport(malformed)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    message = str(exc.value)
    assert "after 3 attempts" in message
    assert "finishReason=STOP" in message
    assert "parts=1" in message
    assert "len=" in message
    assert "at=" in message
    assert "msg=Extra data" in message
    assert len(transport.requests) == 3


@pytest.mark.asyncio
async def test_gms_gemini_retry_count_zero_calls_once(monkeypatch):
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "0")
    transport = _json_transport(_unrecoverable_payload())
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    assert "after 1 attempts" in str(exc.value)
    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_gms_gemini_retry_count_five_calls_six_times(monkeypatch):
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "5")
    transport = _json_transport(_unrecoverable_payload())
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    assert "after 6 attempts" in str(exc.value)
    assert len(transport.requests) == 6


@pytest.mark.asyncio
async def test_gms_gemini_rejects_negative_retry_count_without_http(monkeypatch):
    monkeypatch.setenv("AI_JSON_RETRY_COUNT", "-1")
    transport = _json_transport(_valid_payload())
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(ValueError, match="AI_JSON_RETRY_COUNT"):
        await _provider().complete_json("sys", "user")

    assert transport.requests == []


@pytest.mark.asyncio
async def test_gms_gemini_success_does_not_retry(monkeypatch):
    transport = _json_transport(_valid_payload())
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result["answer"] == "ok"
    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_gms_gemini_retries_without_sleep_or_backoff(monkeypatch):
    malformed = _unrecoverable_payload()
    transport = _sequence_transport(malformed, malformed, _valid_payload())
    _patch_async_client(monkeypatch, transport)

    started_at = time.perf_counter()
    await _provider().complete_json("sys", "user")
    elapsed = time.perf_counter() - started_at

    assert len(transport.requests) == 3
    assert elapsed < 0.5


@pytest.mark.asyncio
async def test_gms_gemini_connection_failure_does_not_retry(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection secret", request=request)

    transport = _FakeTransport(handler)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    assert "GMS Gemini connection failed" in str(exc.value)
    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_gms_gemini_safety_finish_reason_does_not_retry(monkeypatch):
    payload = {
        "candidates": [
            {
                "content": {"parts": []},
                "finishReason": "SAFETY",
            }
        ]
    }
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    assert "finishReason=SAFETY" in str(exc.value)
    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_gms_gemini_text_and_thought_signature_in_same_part(monkeypatch):
    payload = _valid_payload()
    payload["candidates"][0]["content"]["parts"][0]["thoughtSignature"] = "sig"
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result == {"answer": "ok", "usedSources": [1]}


@pytest.mark.asyncio
async def test_gms_gemini_json_root_array_is_retried(monkeypatch):
    payload = {
        "candidates": [
            {
                "content": {"parts": [{"text": "[]"}]},
                "finishReason": "STOP",
            }
        ]
    }
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("sys", "user")

    assert "msg=GMS Gemini returned unrecoverable JSON" in str(exc.value)
    assert len(transport.requests) == 3


@pytest.mark.asyncio
async def test_gms_gemini_empty_candidates_does_not_retry(monkeypatch):
    payload = {
        "candidates": [],
        "promptFeedback": {"blockReason": "SAFETY"},
    }
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError):
        await _provider().complete_json("sys", "user")

    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_gms_gemini_retry_and_success_logs_include_attempts(
    monkeypatch,
    caplog,
):
    malformed = _unrecoverable_payload()
    transport = _sequence_transport(malformed, _valid_payload())
    _patch_async_client(monkeypatch, transport)
    caplog.set_level(logging.INFO, logger="app.providers.llm_provider")

    await _provider().complete_json("private prompt", "private question")

    messages = [record.getMessage() for record in caplog.records]
    assert any("attempt=1/3" in message for message in messages)
    assert any("attempt=2/3" in message for message in messages)
    combined = " ".join(messages)
    assert "private prompt" not in combined
    assert "private question" not in combined
    assert "test-key" not in combined


@pytest.mark.asyncio
async def test_gms_gemini_final_failure_does_not_leak_secrets(monkeypatch):
    secret_response = "TOP-SECRET-RESPONSE"
    transport = _FakeTransport(
        lambda request: httpx.Response(200, text=secret_response)
    )
    _patch_async_client(monkeypatch, transport)

    with pytest.raises(LlmProviderError) as exc:
        await _provider().complete_json("TOP-SECRET-SYSTEM", "TOP-SECRET-USER")

    message = str(exc.value)
    assert secret_response not in message
    assert "TOP-SECRET-SYSTEM" not in message
    assert "TOP-SECRET-USER" not in message
    assert "test-key" not in message


@pytest.mark.asyncio
async def test_gms_gemini_repairs_unclosed_brace_without_retry(
    monkeypatch,
    caplog,
):
    transport = _json_transport(_fixture("unclosed_brace_response.json"))
    _patch_async_client(monkeypatch, transport)
    caplog.set_level(logging.WARNING, logger="app.providers.llm_provider")

    result = await _provider().complete_json("sys", "user")

    assert result == {
        "answer": "제공된 정보로는 반포자이의 위치를 확인할 수 없습니다.",
        "usedSources": [],
    }
    assert len(transport.requests) == 1
    assert any(
        "brace-repaired (stage=missing-1" in record.getMessage()
        for record in caplog.records
    )


@pytest.mark.asyncio
async def test_gms_gemini_repairs_extra_brace_without_retry(
    monkeypatch,
    caplog,
):
    transport = _json_transport(_fixture("extra_brace_response.json"))
    _patch_async_client(monkeypatch, transport)
    caplog.set_level(logging.WARNING, logger="app.providers.llm_provider")

    result = await _provider().complete_json("sys", "user")

    assert result["usedSources"] == [1]
    assert "방문 차량 자리는 사실상 없습니다." in result["answer"]
    assert len(transport.requests) == 1
    assert any(
        "brace-repaired (stage=trailing-data" in record.getMessage()
        for record in caplog.records
    )


@pytest.mark.asyncio
async def test_gms_gemini_repairs_two_missing_braces(monkeypatch):
    payload = {
        "candidates": [
            {
                "content": {
                    "parts": [{"text": '{"outer":{"answer":"ok"'}]
                },
                "finishReason": "STOP",
            }
        ]
    }
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result == {"outer": {"answer": "ok"}}
    assert len(transport.requests) == 1


@pytest.mark.asyncio
async def test_gms_gemini_four_missing_braces_falls_through_to_retry(monkeypatch):
    payload = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {"text": '{"a":{"b":{"c":{"answer":"ok"'}
                    ]
                },
                "finishReason": "STOP",
            }
        ]
    }
    transport = _sequence_transport(payload, _valid_payload())
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result["answer"] == "ok"
    assert len(transport.requests) == 2


@pytest.mark.asyncio
async def test_gms_gemini_does_not_add_opening_brace(monkeypatch):
    transport = _sequence_transport(_unrecoverable_payload(), _valid_payload())
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result["answer"] == "ok"
    assert len(transport.requests) == 2


@pytest.mark.asyncio
async def test_gms_gemini_array_root_is_not_brace_repaired(monkeypatch):
    array_payload = {
        "candidates": [
            {
                "content": {"parts": [{"text": "[]"}]},
                "finishReason": "STOP",
            }
        ]
    }
    transport = _sequence_transport(array_payload, _valid_payload())
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result["answer"] == "ok"
    assert len(transport.requests) == 2


@pytest.mark.asyncio
async def test_gms_gemini_brace_repair_preserves_string_value(monkeypatch):
    original = "따옴표 '와 한글, 숫자 123을 그대로 보존합니다."
    payload = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {
                            "text": json.dumps(
                                {"answer": original, "usedSources": [2]},
                                ensure_ascii=False,
                            )[:-1]
                        }
                    ]
                },
                "finishReason": "STOP",
            }
        ]
    }
    transport = _json_transport(payload)
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    assert result == {"answer": original, "usedSources": [2]}


@pytest.mark.asyncio
async def test_gms_gemini_normal_json_has_no_repair_log(monkeypatch, caplog):
    transport = _json_transport(_valid_payload())
    _patch_async_client(monkeypatch, transport)
    caplog.set_level(logging.WARNING, logger="app.providers.llm_provider")

    await _provider().complete_json("sys", "user")

    assert not any("brace-repaired" in record.getMessage() for record in caplog.records)


@pytest.mark.asyncio
async def test_gms_gemini_repaired_payload_passes_chatbot_validation(monkeypatch):
    transport = _json_transport(_fixture("unclosed_brace_response.json"))
    _patch_async_client(monkeypatch, transport)

    result = await _provider().complete_json("sys", "user")

    jsonschema.validate(instance=result, schema=llm_answer_json_schema())
    validated = LlmAnswerPayload.model_validate(result)
    assert validated.usedSources == []


def test_create_provider_selects_gms(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "gms-gemini")
    monkeypatch.setenv("AI_API_BASE_URL", "https://example")
    monkeypatch.setenv("AI_API_KEY", "k")
    monkeypatch.setenv("AI_MODEL", "gemini-3.5-flash")
    provider = create_llm_provider(purpose=None)
    assert isinstance(provider, GmsGeminiProvider)
