"""Safety-invariant tests for the AI-003 runtime configuration."""

import pytest

from app.config import SttSettings


def test_topics_must_be_distinct(monkeypatch) -> None:
    monkeypatch.setenv("STT_KAFKA_REQUEST_TOPIC", "same-topic")
    monkeypatch.setenv("STT_KAFKA_RESULT_TOPIC", "same-topic")

    with pytest.raises(ValueError, match="topics must differ"):
        SttSettings.from_env()


def test_poll_interval_must_exceed_processing_timeout(monkeypatch) -> None:
    monkeypatch.setenv("STT_TIMEOUT_SECONDS", "10")
    monkeypatch.setenv("STT_KAFKA_MAX_POLL_INTERVAL_MS", "10000")

    with pytest.raises(ValueError, match="MAX_POLL_INTERVAL"):
        SttSettings.from_env()


def test_redis_lease_must_outlive_processing_timeout(monkeypatch) -> None:
    monkeypatch.setenv("STT_TIMEOUT_SECONDS", "10")
    monkeypatch.setenv("STT_REDIS_LEASE_SECONDS", "10")

    with pytest.raises(ValueError, match="REDIS_LEASE"):
        SttSettings.from_env()


def test_single_record_processing_is_explicit(monkeypatch) -> None:
    monkeypatch.setenv("STT_MAX_CONCURRENCY", "2")

    with pytest.raises(ValueError, match="MAX_CONCURRENCY"):
        SttSettings.from_env()


def test_openai_whisper_is_the_default_provider(monkeypatch) -> None:
    monkeypatch.delenv("STT_ENABLED", raising=False)
    monkeypatch.delenv("STT_PROVIDER", raising=False)
    monkeypatch.delenv("STT_MODEL", raising=False)

    settings = SttSettings.from_env()

    assert settings.enabled is False
    assert settings.provider == "openai"
    assert settings.model == "whisper-1"
    assert settings.openai_base_url == "https://api.openai.com/v1"


def test_enabled_openai_provider_requires_dedicated_api_key(monkeypatch) -> None:
    monkeypatch.setenv("STT_ENABLED", "true")
    monkeypatch.setenv("STT_PROVIDER", "openai")
    monkeypatch.delenv("STT_OPENAI_API_KEY", raising=False)

    with pytest.raises(ValueError, match="STT_OPENAI_API_KEY"):
        SttSettings.from_env()


def test_enabled_openai_provider_requires_https_base_url(monkeypatch) -> None:
    monkeypatch.setenv("STT_ENABLED", "true")
    monkeypatch.setenv("STT_PROVIDER", "openai")
    monkeypatch.setenv("STT_OPENAI_API_KEY", "test-key")
    monkeypatch.setenv("STT_OPENAI_BASE_URL", "http://api.openai.invalid/v1")

    with pytest.raises(ValueError, match="absolute HTTPS URL"):
        SttSettings.from_env()


def test_openai_request_timeout_must_fit_service_timeout(monkeypatch) -> None:
    monkeypatch.setenv("STT_ENABLED", "true")
    monkeypatch.setenv("STT_PROVIDER", "openai")
    monkeypatch.setenv("STT_OPENAI_API_KEY", "test-key")
    monkeypatch.setenv("STT_TIMEOUT_SECONDS", "60")
    monkeypatch.setenv("STT_OPENAI_TIMEOUT_SECONDS", "60")

    with pytest.raises(ValueError, match="STT_OPENAI_TIMEOUT_SECONDS"):
        SttSettings.from_env()


def test_openai_provider_requires_wav_normalization(monkeypatch) -> None:
    monkeypatch.setenv("STT_ENABLED", "true")
    monkeypatch.setenv("STT_PROVIDER", "openai")
    monkeypatch.setenv("STT_OPENAI_API_KEY", "test-key")
    monkeypatch.setenv("STT_AUDIO_NORMALIZER", "passthrough")

    with pytest.raises(ValueError, match="STT_AUDIO_NORMALIZER"):
        SttSettings.from_env()


def test_unsupported_provider_is_rejected(monkeypatch) -> None:
    monkeypatch.setenv("STT_PROVIDER", "unknown")

    with pytest.raises(ValueError, match="STT_PROVIDER"):
        SttSettings.from_env()
