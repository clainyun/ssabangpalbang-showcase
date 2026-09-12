"""Contract fixture tests for the chatbot answer schemas (F-1~F-5)."""

import hashlib
import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.schemas.chatbot import (
    CHATBOT_ANSWER_RESPONSE_SCHEMA,
    ChatbotAnswerRequest,
    ChatbotAnswerResponse,
    LlmAnswerPayload,
    llm_answer_json_schema,
)


CONTRACTS = Path(__file__).resolve().parents[2] / "contracts" / "ai-009"
FIXTURE_NAMES = (
    "chatbot_answer_request.json",
    "chatbot_answer_response_fallback.json",
    "chatbot_answer_response_none.json",
    "chatbot_answer_response_report.json",
    "chatbot_answer_response_web.json",
)


def _load(name: str) -> dict:
    return json.loads((CONTRACTS / name).read_text(encoding="utf-8"))


def test_shared_contract_fixtures_match_backend_classpath() -> None:
    """Keep canonical AI fixtures and backend Docker copies byte-identical."""
    repo_root = Path(__file__).resolve().parents[2]
    backend_copy_root = (
        repo_root
        / "backend"
        / "src"
        / "test"
        / "resources"
        / "contracts"
        / "ai-009"
    )

    for name in FIXTURE_NAMES:
        canonical = CONTRACTS / name
        backend_copy = backend_copy_root / name
        assert backend_copy.is_file(), (
            f"backend classpath fixture missing: {backend_copy}"
        )
        assert hashlib.sha256(canonical.read_bytes()).digest() == hashlib.sha256(
            backend_copy.read_bytes()
        ).digest(), f"fixture copy differs from canonical: {name}"


def test_f1_request_fixture_parses() -> None:
    request = ChatbotAnswerRequest.model_validate(
        _load("chatbot_answer_request.json")
    )

    assert request.apartmentId == 15
    assert request.question == "교통 어때요?"
    assert request.conversationId == 41


def test_f2_report_response_fixture_parses() -> None:
    response = ChatbotAnswerResponse.model_validate(
        _load("chatbot_answer_response_report.json")
    )

    assert response.basisType == "REPORT"
    assert response.basisLabel == "리포트 기반"
    assert response.fallbackToWeb is False
    assert len(response.sources) == 1
    assert response.sources[0].reportId == response.sources[0].sourceId


def test_f3_fallback_fixture_parses_with_fallback_flag() -> None:
    response = ChatbotAnswerResponse.model_validate(
        _load("chatbot_answer_response_fallback.json")
    )

    assert response.fallbackToWeb is True
    assert response.answer == ""
    assert response.sources == []


def test_f4_undefined_request_field_is_rejected() -> None:
    payload = _load("chatbot_answer_request.json") | {"memberId": 7}

    with pytest.raises(ValidationError):
        ChatbotAnswerRequest.model_validate(payload)


def test_f4_undefined_response_field_is_rejected() -> None:
    payload = _load("chatbot_answer_response_report.json") | {"extra": 1}

    with pytest.raises(ValidationError):
        ChatbotAnswerResponse.model_validate(payload)


def test_f5_blank_question_is_rejected() -> None:
    with pytest.raises(ValidationError):
        ChatbotAnswerRequest.model_validate(
            {"apartmentId": 15, "question": "   "}
        )


def test_non_positive_apartment_id_is_rejected() -> None:
    with pytest.raises(ValidationError):
        ChatbotAnswerRequest.model_validate(
            {"apartmentId": 0, "question": "교통 어때요?"}
        )


def test_blank_answer_without_fallback_is_rejected() -> None:
    with pytest.raises(ValidationError):
        ChatbotAnswerResponse.model_validate(
            {
                "basisType": "REPORT",
                "basisLabel": "리포트 기반",
                "answer": "",
                "sources": [],
                "topSimilarity": 0.9,
                "fallbackToWeb": False,
            }
        )


def test_llm_payload_accepts_used_source_numbers() -> None:
    payload = LlmAnswerPayload.model_validate(
        {"answer": "근거 답변", "usedSources": [1, 2]}
    )
    assert payload.usedSources == [1, 2]


def test_llm_payload_defaults_used_sources_to_empty() -> None:
    assert LlmAnswerPayload.model_validate({"answer": "확인 불가"}).usedSources == []


def test_llm_payload_defaults_used_profile_to_empty() -> None:
    payload = LlmAnswerPayload.model_validate({"answer": "확인 불가"})

    assert payload.usedProfile == []


def test_llm_payload_preserves_used_profile() -> None:
    payload = LlmAnswerPayload.model_validate(
        {"answer": "3002세대입니다.", "usedProfile": ["household_count"]}
    )

    assert payload.usedProfile == ["household_count"]


def test_llm_payload_rejects_non_string_used_profile_item() -> None:
    with pytest.raises(ValidationError):
        LlmAnswerPayload.model_validate(
            {"answer": "3002세대입니다.", "usedProfile": [1]}
        )


def test_gemini_schema_requires_used_profile() -> None:
    assert "usedProfile" in CHATBOT_ANSWER_RESPONSE_SCHEMA["properties"]
    assert "usedProfile" in CHATBOT_ANSWER_RESPONSE_SCHEMA["required"]


def test_pydantic_json_schema_keeps_used_profile_optional() -> None:
    assert "usedProfile" not in llm_answer_json_schema().get("required", [])


def test_llm_payload_rejects_blank_answer() -> None:
    with pytest.raises(ValidationError):
        LlmAnswerPayload.model_validate({"answer": "   ", "usedSources": []})


def test_llm_payload_rejects_string_source_numbers() -> None:
    with pytest.raises(ValidationError):
        LlmAnswerPayload.model_validate({"answer": "근거 답변", "usedSources": ["1"]})


def test_response_keeps_compatibility_fields() -> None:
    fields = ChatbotAnswerResponse.model_fields
    assert "fallbackToWeb" in fields
    assert "topSimilarity" in fields
