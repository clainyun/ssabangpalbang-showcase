"""Tests for the IAM-free STT media download path."""

from pathlib import Path

import httpx
import pytest

from app.providers.audio_store import (
    AudioNotFoundError,
    MediaGatewayAudioStore,
)


def test_download_uses_fresh_gateway_url_and_streams_file(
    tmp_path: Path,
) -> None:
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if request.url.path == "/media/download-url":
            assert request.headers["Authorization"] == "Bearer test-token"
            assert request.read()
            return httpx.Response(
                200,
                json={
                    "fileUsage": "STT_AUDIO",
                    "s3Key": "stt/2026/07/test.m4a",
                    "downloadUrl": "https://signed.example/audio",
                    "expiresAt": "2099-07-30T03:10:00Z",
                },
            )
        if request.url.path == "/audio":
            return httpx.Response(200, content=b"audio-bytes")
        return httpx.Response(500)

    client = httpx.Client(
        transport=httpx.MockTransport(handler),
        follow_redirects=True,
    )
    store = MediaGatewayAudioStore(
        base_url="https://gateway.example",
        internal_token="test-token",
        client=client,
    )
    destination = tmp_path / "source.audio"

    store.download("stt/2026/07/test.m4a", destination)

    assert destination.read_bytes() == b"audio-bytes"
    assert [request.url.path for request in requests] == [
        "/media/download-url",
        "/audio",
    ]


def test_gateway_not_found_is_not_retryable_download_error(
    tmp_path: Path,
) -> None:
    client = httpx.Client(
        transport=httpx.MockTransport(
            lambda request: httpx.Response(404, json={"error": "not found"})
        )
    )
    store = MediaGatewayAudioStore(
        base_url="https://gateway.example",
        internal_token="test-token",
        client=client,
    )

    with pytest.raises(AudioNotFoundError):
        store.download("missing.m4a", tmp_path / "missing.audio")
