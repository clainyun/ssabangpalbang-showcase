"""Contract tests for the OpenAI Whisper-1 STT provider."""

from __future__ import annotations

from pathlib import Path
from types import SimpleNamespace

import httpx
import pytest

from app.providers.stt_provider import (
    OpenAIWhisperProvider,
    SttProviderError,
    create_stt_provider,
)


def _audio_file(tmp_path: Path) -> Path:
    audio_path = tmp_path / "normalized.wav"
    audio_path.write_bytes(b"RIFF-fake-wave")
    return audio_path


def _provider(
    handler,
) -> tuple[OpenAIWhisperProvider, httpx.Client]:
    client = httpx.Client(transport=httpx.MockTransport(handler))
    provider = OpenAIWhisperProvider(
        api_key="test-openai-key",
        model="whisper-1",
        base_url="https://api.openai.com/v1",
        timeout_seconds=5,
        client=client,
    )
    return provider, client


def test_transcribe_posts_multipart_whisper_request(tmp_path: Path) -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["authorization"] = request.headers.get("Authorization")
        captured["content_type"] = request.headers.get("Content-Type")
        captured["body"] = request.read()
        return httpx.Response(200, json={"text": "창문 상태가 좋습니다."})

    provider, client = _provider(handler)
    try:
        provider.warmup()
        result = provider.transcribe(_audio_file(tmp_path), "ko")
    finally:
        provider.close()
        client.close()

    assert result.text == "창문 상태가 좋습니다."
    assert captured["url"] == "https://api.openai.com/v1/audio/transcriptions"
    assert captured["authorization"] == "Bearer test-openai-key"
    assert str(captured["content_type"]).startswith("multipart/form-data; boundary=")
    body = captured["body"]
    assert isinstance(body, bytes)
    assert b'name="model"' in body
    assert b"whisper-1" in body
    assert b'name="language"' in body
    assert b"ko" in body
    assert b'name="response_format"' in body
    assert b"json" in body
    assert b'filename="normalized.wav"' in body


@pytest.mark.parametrize(
    ("status_code", "expected_code", "expected_retryable"),
    [
        (400, "PROVIDER_REQUEST_REJECTED", False),
        (401, "PROVIDER_AUTH_FAILED", False),
        (403, "PROVIDER_AUTH_FAILED", False),
        (413, "PROVIDER_REQUEST_REJECTED", False),
        (422, "PROVIDER_REQUEST_REJECTED", False),
        (429, "PROVIDER_RATE_LIMITED", True),
        (500, "MODEL_UNAVAILABLE", True),
        (503, "MODEL_UNAVAILABLE", True),
    ],
)
def test_http_status_is_mapped_without_exposing_response(
    tmp_path: Path,
    status_code: int,
    expected_code: str,
    expected_retryable: bool,
) -> None:
    secret_response = "provider-body-must-not-leak"

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(status_code, text=secret_response)

    provider, client = _provider(handler)
    try:
        with pytest.raises(SttProviderError) as caught:
            provider.transcribe(_audio_file(tmp_path), "ko")
    finally:
        provider.close()
        client.close()

    assert caught.value.code == expected_code
    assert caught.value.retryable is expected_retryable
    assert secret_response not in str(caught.value)


def test_network_timeout_is_retryable(tmp_path: Path) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("private-timeout-detail", request=request)

    provider, client = _provider(handler)
    try:
        with pytest.raises(SttProviderError) as caught:
            provider.transcribe(_audio_file(tmp_path), "ko")
    finally:
        provider.close()
        client.close()

    assert caught.value.code == "TRANSCRIPTION_TIMEOUT"
    assert caught.value.retryable is True
    assert "private-timeout-detail" not in str(caught.value)


@pytest.mark.parametrize(
    "response",
    [
        httpx.Response(200, content=b"not-json"),
        httpx.Response(200, json={}),
        httpx.Response(200, json={"text": None}),
    ],
)
def test_invalid_success_response_is_retryable(
    tmp_path: Path,
    response: httpx.Response,
) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return response

    provider, client = _provider(handler)
    try:
        with pytest.raises(SttProviderError) as caught:
            provider.transcribe(_audio_file(tmp_path), "ko")
    finally:
        provider.close()
        client.close()

    assert caught.value.code == "PROVIDER_INVALID_RESPONSE"
    assert caught.value.retryable is True


def test_closed_provider_is_not_ready(tmp_path: Path) -> None:
    provider, client = _provider(
        lambda request: httpx.Response(200, json={"text": "unused"})
    )
    provider.close()
    try:
        assert provider.is_ready() is False
        with pytest.raises(SttProviderError) as caught:
            provider.transcribe(_audio_file(tmp_path), "ko")
    finally:
        client.close()

    assert caught.value.code == "SERVICE_UNAVAILABLE"
    assert caught.value.retryable is True


def test_factory_selects_openai_whisper_provider() -> None:
    settings = SimpleNamespace(
        provider="openai",
        openai_api_key="test-openai-key",
        model="whisper-1",
        openai_base_url="https://api.openai.com/v1",
        openai_timeout_seconds=5,
    )

    provider = create_stt_provider(settings)
    try:
        assert isinstance(provider, OpenAIWhisperProvider)
        assert provider.is_ready() is True
    finally:
        provider.close()
