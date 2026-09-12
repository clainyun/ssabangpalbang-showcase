"""Deployment-safe activation tests for optional AI database features."""

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.api import chatbot
from app.config import is_report_database_enabled
from app.main import app
from app.rag.config import is_rag_enabled
from app.schemas.chatbot import ChatbotAnswerRequest


def test_database_features_are_disabled_when_flags_are_false(
    monkeypatch,
) -> None:
    monkeypatch.setenv("AI_REPORT_DB_ENABLED", "false")
    monkeypatch.setenv("RAG_ENABLED", "false")
    monkeypatch.delenv("AI_REPORT_DB_USER", raising=False)
    monkeypatch.delenv("AI_REPORT_DB_PASSWORD", raising=False)
    monkeypatch.delenv("RAG_DB_USER", raising=False)
    monkeypatch.delenv("RAG_DB_PASSWORD", raising=False)

    assert is_report_database_enabled() is False
    assert is_rag_enabled() is False


@pytest.mark.parametrize("value", ["1", "true", "TRUE", "yes", "on"])
def test_database_feature_flags_accept_supported_true_values(
    monkeypatch, value: str
) -> None:
    monkeypatch.setenv("AI_REPORT_DB_ENABLED", value)
    monkeypatch.setenv("RAG_ENABLED", value)

    assert is_report_database_enabled() is True
    assert is_rag_enabled() is True


@pytest.mark.parametrize("value", ["0", "false", "FALSE", "no", "off", ""])
def test_database_feature_flags_accept_supported_false_values(
    monkeypatch, value: str
) -> None:
    monkeypatch.setenv("AI_REPORT_DB_ENABLED", value)
    monkeypatch.setenv("RAG_ENABLED", value)

    assert is_report_database_enabled() is False
    assert is_rag_enabled() is False


@pytest.mark.parametrize("variable", ["AI_REPORT_DB_ENABLED", "RAG_ENABLED"])
def test_database_feature_flags_reject_ambiguous_values(
    monkeypatch, variable: str
) -> None:
    monkeypatch.setenv(variable, "ture")

    parser = (
        is_report_database_enabled
        if variable == "AI_REPORT_DB_ENABLED"
        else is_rag_enabled
    )

    with pytest.raises(ValueError, match=variable):
        parser()


def test_health_stays_up_without_optional_database_credentials(
    monkeypatch,
) -> None:
    monkeypatch.setenv("AI_REPORT_DB_ENABLED", "false")
    monkeypatch.setenv("RAG_ENABLED", "false")
    monkeypatch.setenv("STT_ENABLED", "false")
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "false")
    monkeypatch.delenv("AI_REPORT_DB_USER", raising=False)
    monkeypatch.delenv("AI_REPORT_DB_PASSWORD", raising=False)
    monkeypatch.delenv("RAG_DB_USER", raising=False)
    monkeypatch.delenv("RAG_DB_PASSWORD", raising=False)

    with TestClient(app) as client:
        response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "sttEnabled": False,
        "sttHealthy": True,
        "reportDbEnabled": False,
        "ragEnabled": False,
        "reportWorkerEnabled": False,
        "reportWorkerHealthy": True,
    }


@pytest.mark.asyncio
async def test_disabled_rag_rejects_request_before_building_service(
    monkeypatch,
) -> None:
    monkeypatch.setenv("RAG_ENABLED", "false")

    def fail_if_called():
        raise AssertionError("RAG service must not initialize while disabled")

    monkeypatch.setattr(chatbot, "_build_service", fail_if_called)

    with pytest.raises(HTTPException) as exc:
        await chatbot.generate_answer(
            ChatbotAnswerRequest(apartmentId=1, question="교통은 어떤가요?")
        )

    assert exc.value.status_code == 503
    assert exc.value.detail["code"] == "RAG_DISABLED"
