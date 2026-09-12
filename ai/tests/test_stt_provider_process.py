"""Process lifecycle tests for the replaceable Whisper runtime."""

import time
from pathlib import Path

import pytest

from app.providers.audio_store import FakeAudioStore, PassthroughAudioNormalizer
from app.providers.stt_provider import (
    ProcessIsolatedSttProvider,
    SttProviderError,
)
from app.schemas.stt import SttRequest
from app.services.stt_service import SttProcessingError, SttService


def _fake_child(connection, _config) -> None:
    try:
        connection.send(("ready", None))
        while True:
            command = connection.recv()
            if command[0] == "stop":
                return
            if command[0] == "transcribe":
                connection.send(("result", "isolated result"))
    except EOFError:
        return
    finally:
        connection.close()


def _hanging_child(connection, _config) -> None:
    try:
        connection.send(("ready", None))
        while True:
            command = connection.recv()
            if command[0] == "stop":
                return
            if command[0] == "transcribe":
                time.sleep(60)
    except EOFError:
        return
    finally:
        connection.close()


def test_process_is_warmed_and_replaced_after_abort(tmp_path: Path) -> None:
    provider = ProcessIsolatedSttProvider(
        model="unused",
        device="cpu",
        compute_type="int8",
        warmup_timeout_seconds=5,
        _process_target=_fake_child,
    )
    audio_path = tmp_path / "audio.wav"
    audio_path.touch()
    try:
        provider.warmup()
        first_pid = provider._process.pid
        assert provider.is_ready() is True
        assert provider.transcribe(audio_path, "ko").text == "isolated result"

        provider.abort()
        assert provider.is_ready() is False

        provider.warmup()
        assert provider.is_ready() is True
        assert provider._process.pid != first_pid
    finally:
        provider.close()

    with pytest.raises(SttProviderError, match="closed"):
        provider.warmup()


@pytest.mark.asyncio
async def test_hung_inference_is_killed_and_next_process_is_new() -> None:
    provider = ProcessIsolatedSttProvider(
        model="unused",
        device="cpu",
        compute_type="int8",
        warmup_timeout_seconds=5,
        _process_target=_hanging_child,
    )
    provider.warmup()
    first_process = provider._process
    service = SttService(
        audio_store=FakeAudioStore(),
        normalizer=PassthroughAudioNormalizer(),
        provider=provider,
        timeout_seconds=0.05,
    )
    request = SttRequest.model_validate({
        "schemaVersion": 1,
        "sttId": "stt-process-timeout",
        "attemptNo": 1,
        "audioFileId": 90,
        "objectKey": "stt/audio.wav",
        "contentType": "audio/wav",
        "language": "ko-KR",
    })
    try:
        with pytest.raises(SttProcessingError) as caught:
            await service.transcribe(request)
        assert caught.value.code == "TRANSCRIPTION_TIMEOUT"
        assert first_process.is_alive() is False
        assert provider.is_ready() is False

        provider.warmup()
        assert provider.is_ready() is True
        assert provider._process.pid != first_process.pid
    finally:
        service.close()
