"""Web search query, failure handling and unchanged response fixtures."""

import json
from pathlib import Path

import pytest

from app.prompts.chatbot_prompt import build_search_query
from app.schemas.chatbot import ChatbotAnswerResponse, INSUFFICIENT_EVIDENCE_ANSWER


CONTRACTS = Path(__file__).resolve().parents[2] / "contracts" / "ai-009"


def test_search_query_drops_missing_and_blank_parts() -> None:
    assert build_search_query("래미안", None, " ", "교통 어때요?") == "래미안 교통 어때요?"


def test_search_query_includes_region_parts_when_present() -> None:
    assert build_search_query("래미안", "성동구", "옥수동", "교통 어때요?") == (
        "래미안 성동구 옥수동 교통 어때요?"
    )


def _load(name: str) -> dict:
    return json.loads((CONTRACTS / name).read_text(encoding="utf-8"))


def test_web_fixture_remains_compatible() -> None:
    response = ChatbotAnswerResponse.model_validate(_load("chatbot_answer_response_web.json"))
    assert response.basisType == "WEB"
    assert response.sources[0].url is not None


def test_none_fixture_remains_compatible() -> None:
    response = ChatbotAnswerResponse.model_validate(_load("chatbot_answer_response_none.json"))
    assert response.basisType == "NONE"
    assert response.answer == INSUFFICIENT_EVIDENCE_ANSWER


def test_none_with_label_is_rejected() -> None:
    payload = _load("chatbot_answer_response_none.json") | {"basisLabel": "웹 기반"}
    with pytest.raises(Exception):
        ChatbotAnswerResponse.model_validate(payload)
