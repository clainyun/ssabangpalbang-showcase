"""Offline tests for the deployment-time OpenAI STT live check."""

from __future__ import annotations

import httpx
import pytest

from app.config import SttSettings
from app.diagnostics._openai_stt_live_fixture import load_wav_bytes
from app.diagnostics.verify_openai_stt import (
    LiveSttVerificationError,
    main,
    run_live_verification,
    validate_live_fixture,
    validate_live_settings,
    validate_transcript,
)
from app.providers.stt_provider import (
    FakeSttProvider,
    OpenAIWhisperProvider,
    SttProviderError,
)


def _settings(
    monkeypatch: pytest.MonkeyPatch,
    *,
    provider: str = "openai",
    model: str = "whisper-1",
    base_url: str = "https://api.openai.com/v1",
) -> SttSettings:
    monkeypatch.setenv("STT_ENABLED", "true")
    monkeypatch.setenv("STT_PROVIDER", provider)
    monkeypatch.setenv("STT_MODEL", model)
    monkeypatch.setenv("STT_OPENAI_API_KEY", "test-openai-stt-key")
    monkeypatch.setenv("STT_OPENAI_BASE_URL", base_url)
    monkeypatch.setenv("STT_AUDIO_NORMALIZER", "auto")
    return SttSettings.from_env()


def test_embedded_fixture_matches_production_wav_contract() -> None:
    audio = load_wav_bytes()

    validate_live_fixture(audio)

    assert audio.startswith(b"RIFF")
    assert b"WAVE" in audio[:16]


def test_live_fixture_rejects_truncated_audio() -> None:
    with pytest.raises(LiveSttVerificationError, match="16 kHz"):
        validate_live_fixture(load_wav_bytes()[:-1])


@pytest.mark.parametrize(
    ("provider", "model", "message"),
    [
        ("faster-whisper", "small", "STT_PROVIDER must be openai"),
        ("openai", "small", "STT_MODEL must be whisper-1"),
    ],
)
def test_live_check_rejects_the_old_local_model_path_before_factory(
    monkeypatch: pytest.MonkeyPatch,
    provider: str,
    model: str,
    message: str,
) -> None:
    settings = _settings(monkeypatch, provider=provider, model=model)
    factory_called = False

    def fail_factory(_: SttSettings):
        nonlocal factory_called
        factory_called = True
        raise AssertionError("provider factory must not run")

    with pytest.raises(LiveSttVerificationError, match=message):
        run_live_verification(
            settings_loader=lambda: settings,
            provider_factory=fail_factory,
        )

    assert factory_called is False


def test_live_check_requires_the_official_openai_endpoint(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    settings = _settings(
        monkeypatch,
        base_url="https://compatible-provider.example/v1",
    )

    with pytest.raises(LiveSttVerificationError, match="official OpenAI API"):
        validate_live_settings(settings)


def test_live_check_uses_factory_selected_whisper_1_and_validates_transcript(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    settings = _settings(monkeypatch)
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["body"] = request.read()
        return httpx.Response(200, json={"text": "음성 테스트입니다."})

    client = httpx.Client(transport=httpx.MockTransport(handler))
    provider = OpenAIWhisperProvider(
        api_key=settings.openai_api_key,
        model=settings.model,
        base_url=settings.openai_base_url,
        timeout_seconds=settings.openai_timeout_seconds,
        client=client,
    )
    try:
        result = run_live_verification(
            settings_loader=lambda: settings,
            provider_factory=lambda configured: (
                provider if configured is settings else pytest.fail("wrong settings")
            ),
        )
    finally:
        client.close()

    assert result.provider == "openai"
    assert result.model == "whisper-1"
    assert captured["url"] == "https://api.openai.com/v1/audio/transcriptions"
    assert b"whisper-1" in captured["body"]


def test_live_check_rejects_a_non_openai_factory_result(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    settings = _settings(monkeypatch)

    with pytest.raises(LiveSttVerificationError, match="OpenAIWhisperProvider"):
        run_live_verification(
            settings_loader=lambda: settings,
            provider_factory=lambda _: FakeSttProvider(),
        )


def test_live_check_rejects_factory_configuration_drift(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    settings = _settings(monkeypatch)
    provider = OpenAIWhisperProvider(
        api_key="wrong-key",
        model="whisper-1",
        base_url="https://api.openai.com/v1",
    )

    with pytest.raises(LiveSttVerificationError, match="validated configuration"):
        run_live_verification(
            settings_loader=lambda: settings,
            provider_factory=lambda _: provider,
        )


def test_transcript_keyword_check_ignores_spacing_and_punctuation() -> None:
    validate_transcript("음성 테.스!트입니다")

    with pytest.raises(LiveSttVerificationError, match="expected fixture keyword"):
        validate_transcript("전혀 다른 결과")


def test_main_logs_only_safe_verification_metadata(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    from app.diagnostics import verify_openai_stt as target

    monkeypatch.setattr(
        target,
        "run_live_verification",
        lambda: target.LiveSttVerificationResult(
            provider="openai",
            model="whisper-1",
            keyword="secret-transcript-keyword",
        ),
    )

    assert main() == 0
    output = capsys.readouterr().out
    assert "provider=openai" in output
    assert "model=whisper-1" in output
    assert "secret-transcript-keyword" not in output


def test_main_redacts_provider_failure_details(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    from app.diagnostics import verify_openai_stt as target

    def fail_with_sensitive_detail() -> None:
        raise SttProviderError("response leaked sk-sensitive-value")

    monkeypatch.setattr(target, "run_live_verification", fail_with_sensitive_detail)

    assert main() == 1
    output = capsys.readouterr().err
    assert "failed safely" in output
    assert "sk-sensitive-value" not in output
