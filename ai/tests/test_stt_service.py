"""Unit tests for STT media/model orchestration."""

from __future__ import annotations

import asyncio
import time
from pathlib import Path

import pytest

from app.providers.audio_store import (
    AudioDownloadError,
    FakeAudioStore,
    PassthroughAudioNormalizer,
)
from app.providers.stt_provider import (
    FakeSttProvider,
    SttProviderError,
    Transcription,
)
from app.schemas.stt import SttRequest
from app.services.stt_service import SttProcessingError, SttService


def request_payload(**overrides) -> SttRequest:
    payload = {
        "schemaVersion": 1,
        "sttId": "stt-test",
        "attemptNo": 1,
        "audioFileId": 90,
        "objectKey": "field-visit/stt/7/90.m4a",
        "contentType": "audio/m4a",
        "language": "ko-KR",
    }
    payload.update(overrides)
    return SttRequest.model_validate(payload)


@pytest.mark.asyncio
async def test_service_downloads_transcribes_and_normalizes_language() -> None:
    store = FakeAudioStore()
    provider = FakeSttProvider(text="  역까지   도보 8분입니다. ")
    service = SttService(
        audio_store=store,
        normalizer=PassthroughAudioNormalizer(),
        provider=provider,
        timeout_seconds=2,
    )
    try:
        result = await service.transcribe(request_payload())
    finally:
        service.close()

    assert result == "역까지 도보 8분입니다."
    assert store.download_count == 1
    assert provider.call_count == 1
    assert provider.last_language == "ko"


class _FailingStore(FakeAudioStore):
    def download(self, object_key: str, destination: Path) -> None:
        raise AudioDownloadError("contains-internal-object-location")


@pytest.mark.asyncio
async def test_service_maps_download_error_to_safe_retryable_failure() -> None:
    service = SttService(
        audio_store=_FailingStore(),
        normalizer=PassthroughAudioNormalizer(),
        provider=FakeSttProvider(),
        timeout_seconds=2,
    )
    try:
        with pytest.raises(SttProcessingError) as caught:
            await service.transcribe(request_payload())
    finally:
        service.close()

    assert caught.value.code == "AUDIO_DOWNLOAD_FAILED"
    assert caught.value.retryable is True
    assert "object" not in caught.value.reason


@pytest.mark.asyncio
async def test_service_rejects_empty_transcript() -> None:
    service = SttService(
        audio_store=FakeAudioStore(),
        normalizer=PassthroughAudioNormalizer(),
        provider=FakeSttProvider(text="  "),
        timeout_seconds=2,
    )
    try:
        with pytest.raises(SttProcessingError) as caught:
            await service.transcribe(request_payload())
    finally:
        service.close()

    assert caught.value.code == "EMPTY_TRANSCRIPT"
    assert caught.value.retryable is False


@pytest.mark.asyncio
async def test_service_preserves_provider_error_classification() -> None:
    provider = FakeSttProvider(
        error=SttProviderError(
            "private provider detail",
            code="PROVIDER_AUTH_FAILED",
            retryable=False,
        )
    )
    service = SttService(
        audio_store=FakeAudioStore(),
        normalizer=PassthroughAudioNormalizer(),
        provider=provider,
        timeout_seconds=2,
    )
    try:
        with pytest.raises(SttProcessingError) as caught:
            await service.transcribe(request_payload())
    finally:
        service.close()

    assert caught.value.code == "PROVIDER_AUTH_FAILED"
    assert caught.value.retryable is False
    assert "private provider detail" not in caught.value.reason


class _SlowProvider(FakeSttProvider):
    def __init__(self) -> None:
        super().__init__()
        self.aborted = False

    def transcribe(self, audio_path: Path, language: str) -> Transcription:
        time.sleep(0.1)
        return Transcription(text="늦은 결과")

    def abort(self) -> None:
        self.aborted = True


class _SlowStore(FakeAudioStore):
    def download(self, object_key: str, destination: Path) -> None:
        time.sleep(0.1)
        super().download(object_key, destination)


@pytest.mark.asyncio
async def test_service_maps_timeout() -> None:
    provider = _SlowProvider()
    service = SttService(
        audio_store=FakeAudioStore(),
        normalizer=PassthroughAudioNormalizer(),
        provider=provider,
        timeout_seconds=0.01,
    )
    try:
        with pytest.raises(SttProcessingError) as caught:
            await service.transcribe(request_payload())
    finally:
        service.close()

    assert caught.value.code == "TRANSCRIPTION_TIMEOUT"
    assert caught.value.retryable is True
    assert provider.aborted is True


@pytest.mark.asyncio
async def test_timeout_during_download_does_not_start_model_later() -> None:
    provider = FakeSttProvider()
    service = SttService(
        audio_store=_SlowStore(),
        normalizer=PassthroughAudioNormalizer(),
        provider=provider,
        timeout_seconds=0.01,
    )
    try:
        with pytest.raises(SttProcessingError) as caught:
            await service.transcribe(request_payload())
        await asyncio.sleep(0.15)
    finally:
        service.close()

    assert caught.value.code == "TRANSCRIPTION_TIMEOUT"
    assert provider.call_count == 0
