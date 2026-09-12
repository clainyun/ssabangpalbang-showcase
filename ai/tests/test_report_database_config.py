"""Safety tests for the AI-004 read-only database settings."""

import pytest

from app.config import ReportDatabaseSettings


_REQUIRED_NAMES = (
    "AI_REPORT_DB_NAME",
    "AI_REPORT_DB_USER",
    "AI_REPORT_DB_PASSWORD",
)


def test_report_database_settings_require_credentials(monkeypatch) -> None:
    for name in _REQUIRED_NAMES:
        monkeypatch.delenv(name, raising=False)

    with pytest.raises(ValueError, match="AI_REPORT_DB_NAME"):
        ReportDatabaseSettings.from_env()


def test_report_database_settings_load_without_exposing_password(
    monkeypatch,
) -> None:
    monkeypatch.setenv("AI_REPORT_DB_HOST", "postgres")
    monkeypatch.setenv("AI_REPORT_DB_PORT", "5432")
    monkeypatch.setenv("AI_REPORT_DB_NAME", "ssabangpalbang")
    monkeypatch.setenv("AI_REPORT_DB_USER", "report_reader")
    monkeypatch.setenv("AI_REPORT_DB_PASSWORD", "local-test-password")

    settings = ReportDatabaseSettings.from_env()

    assert settings.host == "postgres"
    assert settings.database == "ssabangpalbang"
    assert "local-test-password" not in repr(settings)


def test_report_database_port_must_be_valid(monkeypatch) -> None:
    monkeypatch.setenv("AI_REPORT_DB_PORT", "65536")
    monkeypatch.setenv("AI_REPORT_DB_NAME", "ssabangpalbang")
    monkeypatch.setenv("AI_REPORT_DB_USER", "report_reader")
    monkeypatch.setenv("AI_REPORT_DB_PASSWORD", "local-test-password")

    with pytest.raises(ValueError, match="at most 65535"):
        ReportDatabaseSettings.from_env()
