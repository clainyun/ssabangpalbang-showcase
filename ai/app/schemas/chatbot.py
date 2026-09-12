"""Pydantic schemas for the Spring ↔ FastAPI chatbot answer contract (AI-009).

Field names must match Java DTOs:
- ChatbotAnswerRequest
- ChatbotAnswerResponse / ChatbotAnswerSource
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StrictInt,
    field_validator,
    model_validator,
)


REPORT_BASIS_TYPE = "REPORT"
REPORT_BASIS_LABEL = "리포트 기반"
WEB_BASIS_TYPE = "WEB"
WEB_BASIS_LABEL = "웹 기반"
NONE_BASIS_TYPE = "NONE"

INSUFFICIENT_EVIDENCE_ANSWER = "신뢰할 수 있는 자료를 찾지 못해 답변드리기 어렵습니다."
"""Returned instead of guessing when no trustworthy evidence was found."""

CHATBOT_ANSWER_RESPONSE_SCHEMA: dict[str, Any] = {
    "type": "OBJECT",
    "properties": {
        "answer": {"type": "STRING"},
        "usedSources": {
            "type": "ARRAY",
            "items": {"type": "INTEGER"},
        },
        "usedProfile": {
            "type": "ARRAY",
            "items": {"type": "STRING"},
        },
    },
    "required": ["answer", "usedSources", "usedProfile"],
}

CHATBOT_SEARCH_QUERY_SCHEMA: dict[str, Any] = {
    "type": "OBJECT",
    "properties": {"query": {"type": "STRING"}},
    "required": ["query"],
}


class ChatbotAnswerRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    apartmentId: int = Field(gt=0)
    question: str = Field(min_length=1, max_length=2000)
    apartmentName: str | None = None
    conversationId: int | None = None
    messageId: int | None = None

    @field_validator("question")
    @classmethod
    def question_not_blank(cls, value: str) -> str:
        if value.strip() == "":
            raise ValueError("question must not be blank")
        return value


class ChatbotAnswerSource(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sourceType: Literal["REPORT", "WEB"] = REPORT_BASIS_TYPE
    sourceId: int | None = None
    reportId: int | None = None
    title: str
    sectionLabel: str | None = None
    url: str | None = None
    sourceAt: str | None = None


class ChatbotAnswerResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    basisType: Literal["REPORT", "WEB", "NONE"] = REPORT_BASIS_TYPE
    basisLabel: str | None = REPORT_BASIS_LABEL
    answer: str
    sources: list[ChatbotAnswerSource] = Field(default_factory=list)
    topSimilarity: float | None = None
    fallbackToWeb: bool = False

    @field_validator("fallbackToWeb")
    @classmethod
    def blank_answer_requires_fallback(cls, value: bool, info) -> bool:
        # An empty answer with fallbackToWeb=False is a contradictory state:
        # the caller would render nothing and never try the web path.
        answer = info.data.get("answer")
        if answer is not None and answer.strip() == "" and not value:
            raise ValueError(
                "answer must not be blank unless fallbackToWeb is true"
            )
        return value

    @model_validator(mode="after")
    def label_matches_basis_type(self) -> "ChatbotAnswerResponse":
        expected = {
            REPORT_BASIS_TYPE: REPORT_BASIS_LABEL,
            WEB_BASIS_TYPE: WEB_BASIS_LABEL,
            NONE_BASIS_TYPE: None,
        }[self.basisType]
        if self.basisLabel != expected:
            raise ValueError(
                f"basisLabel for {self.basisType} must be {expected!r}"
            )
        return self


class LlmAnswerPayload(BaseModel):
    """The model answer and the one-based evidence numbers it actually used."""

    model_config = ConfigDict(extra="forbid")

    answer: str = Field(min_length=1)
    usedSources: list[StrictInt] = Field(default_factory=list)
    usedProfile: list[str] = Field(default_factory=list)

    @field_validator("answer")
    @classmethod
    def answer_not_blank(cls, value: str) -> str:
        if value.strip() == "":
            raise ValueError("answer must not be blank")
        return value


def llm_answer_json_schema() -> dict:
    """JSON Schema used for secondary validation of LLM output."""
    return LlmAnswerPayload.model_json_schema()


class SearchQueryPayload(BaseModel):
    """A validated one-line query returned by the rewrite provider."""

    model_config = ConfigDict(extra="forbid")

    query: str = Field(min_length=1)

    @field_validator("query")
    @classmethod
    def query_not_blank(cls, value: str) -> str:
        query = " ".join(value.split())
        if not query:
            raise ValueError("query must not be blank")
        return query
