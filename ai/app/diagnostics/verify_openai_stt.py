"""Perform one real OpenAI Whisper-1 transcription before production deploy."""

from __future__ import annotations

from collections.abc import Callable
from contextlib import suppress
from dataclasses import dataclass
import hashlib
from io import BytesIO
from pathlib import Path
import re
import sys
import tempfile
import wave

from app.config import SttSettings
from app.diagnostics._openai_stt_live_fixture import (
    EXPECTED_TRANSCRIPT_KEYWORD,
    EXPECTED_WAV_SHA256,
    load_wav_bytes,
)
from app.providers.stt_provider import (
    OpenAIWhisperProvider,
    SttProvider,
    SttProviderError,
    create_stt_provider,
)


REQUIRED_PROVIDER = "openai"
REQUIRED_MODEL = "whisper-1"
REQUIRED_BASE_URL = "https://api.openai.com/v1"


class LiveSttVerificationError(RuntimeError):
    """The deployment does not prove a real Whisper-1 transcription path."""


@dataclass(frozen=True)
class LiveSttVerificationResult:
    provider: str
    model: str
    keyword: str


def validate_live_settings(settings: SttSettings) -> None:
    """Reject rollback or compatible-provider settings before any API call."""
    if not settings.enabled:
        raise LiveSttVerificationError("STT_ENABLED must be true")
    if settings.provider != REQUIRED_PROVIDER:
        raise LiveSttVerificationError("STT_PROVIDER must be openai")
    if settings.model != REQUIRED_MODEL:
        raise LiveSttVerificationError("STT_MODEL must be whisper-1")
    if settings.openai_base_url.rstrip("/") != REQUIRED_BASE_URL:
        raise LiveSttVerificationError(
            "STT_OPENAI_BASE_URL must target the official OpenAI API"
        )
    if not settings.openai_api_key:
        raise LiveSttVerificationError("STT_OPENAI_API_KEY must be configured")


def validate_live_fixture(audio: bytes) -> None:
    """Keep the committed probe aligned with the production normalizer output."""
    try:
        with wave.open(BytesIO(audio), "rb") as wav:
            frame_count = wav.getnframes()
            expected_frame_bytes = (
                frame_count * wav.getnchannels() * wav.getsampwidth()
            )
            frames = wav.readframes(frame_count)
            valid = (
                wav.getnchannels() == 1
                and wav.getsampwidth() == 2
                and wav.getframerate() == 16_000
                and frame_count > 0
                and wav.getcomptype() == "NONE"
                and len(frames) == expected_frame_bytes
                and hashlib.sha256(audio).hexdigest() == EXPECTED_WAV_SHA256
            )
    except (EOFError, wave.Error) as exc:
        raise LiveSttVerificationError("live STT fixture is not a valid WAV") from exc
    if not valid:
        raise LiveSttVerificationError(
            "live STT fixture must be 16 kHz, 16-bit, mono PCM WAV"
        )


def normalize_transcript(value: str) -> str:
    """Ignore whitespace and punctuation while preserving Korean characters."""
    return re.sub(r"[^0-9A-Za-z가-힣]", "", value).casefold()


def validate_transcript(text: str, keyword: str = EXPECTED_TRANSCRIPT_KEYWORD) -> None:
    """Require meaningful audio recognition instead of a merely successful HTTP call."""
    normalized_text = normalize_transcript(text)
    normalized_keyword = normalize_transcript(keyword)
    if not normalized_text or normalized_keyword not in normalized_text:
        raise LiveSttVerificationError(
            "Whisper-1 transcript did not contain the expected fixture keyword"
        )


def run_live_verification(
    *,
    settings_loader: Callable[[], SttSettings] = SttSettings.from_env,
    provider_factory: Callable[[SttSettings], SttProvider] = create_stt_provider,
    fixture_loader: Callable[[], bytes] = load_wav_bytes,
) -> LiveSttVerificationResult:
    """Exercise the production settings/factory and the real transcription endpoint."""
    settings = settings_loader()
    validate_live_settings(settings)

    audio = fixture_loader()
    validate_live_fixture(audio)
    provider = provider_factory(settings)
    try:
        if not isinstance(provider, OpenAIWhisperProvider):
            raise LiveSttVerificationError(
                "STT provider factory did not select OpenAIWhisperProvider"
            )
        if not provider.matches_configuration(
            api_key=settings.openai_api_key,
            model=REQUIRED_MODEL,
            base_url=REQUIRED_BASE_URL,
        ):
            raise LiveSttVerificationError(
                "OpenAI STT provider does not match the validated configuration"
            )

        with tempfile.TemporaryDirectory(prefix="stt-live-check-") as directory:
            audio_path = Path(directory) / "fixture.wav"
            audio_path.write_bytes(audio)
            provider.warmup()
            transcription = provider.transcribe(audio_path, "ko")
        validate_transcript(transcription.text)
    finally:
        with suppress(Exception):
            provider.close()

    return LiveSttVerificationResult(
        provider=settings.provider,
        model=settings.model,
        keyword=EXPECTED_TRANSCRIPT_KEYWORD,
    )


def main() -> int:
    try:
        result = run_live_verification()
    except LiveSttVerificationError as exc:
        print(f"OpenAI STT live verification failed: {exc}", file=sys.stderr)
        return 1
    except (SttProviderError, ValueError):
        print("OpenAI STT live verification failed safely", file=sys.stderr)
        return 1
    except Exception:
        print("OpenAI STT live verification failed unexpectedly", file=sys.stderr)
        return 1

    print(
        "OpenAI STT live verification passed: "
        f"provider={result.provider} model={result.model} "
        "endpoint=/v1/audio/transcriptions"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
