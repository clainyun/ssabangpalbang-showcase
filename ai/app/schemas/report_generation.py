"""Pydantic schemas for AI-005 pre-evidence report generation.

This is an internal pipeline result consumed by AI-006/AI-007.
Public API fields such as sourceIds, evidenceCount, status, and apartment
metadata are assembled later and must not appear here.
"""

from __future__ import annotations

from enum import StrEnum
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class OpinionType(StrEnum):
    POSITIVE = "POSITIVE"
    CAUTION = "CAUTION"


class _StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ReportMetrics(_StrictModel):
    totalChecklistItemCount: int = Field(ge=0)
    completedChecklistItemCount: int = Field(ge=0)
    averageCompletionRate: float = Field(ge=0.0, le=100.0)
    fieldRecordCount: int = Field(ge=0)

    @model_validator(mode="after")
    def completed_not_above_total(self) -> "ReportMetrics":
        if self.completedChecklistItemCount > self.totalChecklistItemCount:
            raise ValueError(
                "completedChecklistItemCount must not exceed totalChecklistItemCount"
            )
        return self


class ReportFeature(_StrictModel):
    rank: int = Field(ge=1)
    label: str = Field(min_length=1, max_length=80)
    summary: str = Field(min_length=1, max_length=500)
    mentionCount: int = Field(ge=1)
    participantRefs: list[str] = Field(min_length=1)

    @field_validator("label", "summary")
    @classmethod
    def not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped

    @field_validator("participantRefs")
    @classmethod
    def refs_unique_and_shaped(cls, value: list[str]) -> list[str]:
        if len(value) != len(set(value)):
            raise ValueError("participantRefs must be unique")
        for ref in value:
            if not ref.startswith("P"):
                raise ValueError("participantRefs must use AI-004 anonymous refs")
        return value

    @model_validator(mode="after")
    def mention_matches_refs(self) -> "ReportFeature":
        if self.mentionCount != len(self.participantRefs):
            raise ValueError("mentionCount must equal unique participantRefs")
        return self


class CommonOpinion(_StrictModel):
    category: str = Field(min_length=1, max_length=30)
    label: str = Field(min_length=1, max_length=80)
    opinionType: OpinionType
    summary: str = Field(min_length=1, max_length=500)
    participantCount: int = Field(ge=2)
    participantRefs: list[str] = Field(min_length=2)

    @field_validator("category", "label", "summary")
    @classmethod
    def not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped

    @model_validator(mode="after")
    def counts_match_refs(self) -> "CommonOpinion":
        if len(self.participantRefs) != len(set(self.participantRefs)):
            raise ValueError("participantRefs must be unique")
        if self.participantCount != len(self.participantRefs):
            raise ValueError("participantCount must equal unique participantRefs")
        return self


class ConflictingOpinion(_StrictModel):
    category: str = Field(min_length=1, max_length=30)
    label: str = Field(min_length=1, max_length=80)
    summary: str = Field(min_length=1, max_length=500)
    positiveParticipantCount: int = Field(ge=1)
    cautionParticipantCount: int = Field(ge=1)
    positiveParticipantRefs: list[str] = Field(min_length=1)
    cautionParticipantRefs: list[str] = Field(min_length=1)

    @field_validator("category", "label", "summary")
    @classmethod
    def not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped

    @model_validator(mode="after")
    def conflict_sets_are_valid(self) -> "ConflictingOpinion":
        if len(self.positiveParticipantRefs) != len(set(self.positiveParticipantRefs)):
            raise ValueError("positiveParticipantRefs must be unique")
        if len(self.cautionParticipantRefs) != len(set(self.cautionParticipantRefs)):
            raise ValueError("cautionParticipantRefs must be unique")
        if self.positiveParticipantCount != len(self.positiveParticipantRefs):
            raise ValueError(
                "positiveParticipantCount must equal unique positiveParticipantRefs"
            )
        if self.cautionParticipantCount != len(self.cautionParticipantRefs):
            raise ValueError(
                "cautionParticipantCount must equal unique cautionParticipantRefs"
            )
        combined = set(self.positiveParticipantRefs) | set(self.cautionParticipantRefs)
        if len(combined) < 2:
            raise ValueError("conflicting opinions require at least two participants")
        return self


class ParticipantOpinion(_StrictModel):
    participantRef: str = Field(pattern=r"^P[1-9][0-9]*$")
    participantLabel: str = Field(min_length=1, max_length=40)
    opinionType: OpinionType
    summary: str = Field(min_length=1, max_length=500)

    @field_validator("participantLabel", "summary")
    @classmethod
    def not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped


class ReportCategory(_StrictModel):
    category: str = Field(min_length=1, max_length=30)
    summary: str = Field(min_length=1, max_length=500)
    positiveOpinionCount: int = Field(ge=0)
    cautionOpinionCount: int = Field(ge=0)
    dataSufficient: bool
    participantOpinions: list[ParticipantOpinion]

    @field_validator("category", "summary")
    @classmethod
    def not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped


class ReportGenerationResult(_StrictModel):
    title: str = Field(min_length=1, max_length=120)
    summary: str = Field(min_length=1, max_length=2000)
    metrics: ReportMetrics
    topPositiveFeatures: list[ReportFeature] = Field(max_length=3)
    topCautionFeatures: list[ReportFeature] = Field(max_length=3)
    commonOpinions: list[CommonOpinion]
    conflictingOpinions: list[ConflictingOpinion]
    categories: list[ReportCategory]

    @field_validator("title", "summary")
    @classmethod
    def not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped

    @model_validator(mode="after")
    def ranks_are_sequential(self) -> "ReportGenerationResult":
        for features in (self.topPositiveFeatures, self.topCautionFeatures):
            expected = list(range(1, len(features) + 1))
            actual = [item.rank for item in features]
            if actual != expected:
                raise ValueError("feature ranks must be sequential from 1")
        return self


# --- LLM draft contract (internal; not part of ReportGenerationResult) ---


class LlmOpinionCandidate(_StrictModel):
    participantRef: str = Field(pattern=r"^P[1-9][0-9]*$")
    category: str = Field(min_length=1, max_length=30)
    opinionType: OpinionType
    label: str = Field(min_length=1, max_length=80)
    summary: str = Field(min_length=1, max_length=500)
    sourceIndex: int = Field(ge=0)
    conclusionQuote: str | None = Field(default=None, max_length=60)

    @field_validator("category", "label", "summary")
    @classmethod
    def not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped

    @field_validator("conclusionQuote", mode="before")
    @classmethod
    def blank_conclusion_quote_to_none(cls, value: Any) -> Any:
        if isinstance(value, str) and not value.strip():
            return None
        return value


class LlmCategorySummary(_StrictModel):
    category: str = Field(min_length=1, max_length=30)
    summary: str = Field(min_length=1, max_length=500)
    sourceIndexes: list[int] = Field(min_length=1, max_length=200)

    @field_validator("category", "summary")
    @classmethod
    def not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped

    @field_validator("sourceIndexes")
    @classmethod
    def source_indexes_unique_and_non_negative(cls, value: list[int]) -> list[int]:
        if any(index < 0 for index in value):
            raise ValueError("sourceIndexes must be non-negative")
        if len(value) != len(set(value)):
            raise ValueError("sourceIndexes must be unique")
        return value


class LlmCommonOpinionSummary(_StrictModel):
    """공통 의견 요약. 자연어 키 대신 opinionCandidates 의 0-based index 로 참조한다.

    이전 계약은 category/label/opinionType 을 후보와 문자 단위로 똑같이 반복
    생성하도록 요구했는데, LLM 이 한 응답 안에서도 label 표기를 흔들어
    INVALID_REFERENCE 로 리포트가 통째로 실패했다. 어떤 후보를 묶었는지를
    숫자로 받으면 그 실패 모드가 구조적으로 사라진다.
    """

    candidateIndexes: list[int] = Field(min_length=1, max_length=200)
    summary: str = Field(min_length=1, max_length=500)

    @field_validator("candidateIndexes")
    @classmethod
    def indexes_non_negative(cls, value: list[int]) -> list[int]:
        if any(index < 0 for index in value):
            raise ValueError("candidateIndexes must be non-negative")
        return value

    @field_validator("summary")
    @classmethod
    def not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped


class LlmConflictingOpinionSummary(_StrictModel):
    """상충 의견 요약. LlmCommonOpinionSummary 와 같은 이유로 index 참조를 쓴다."""

    candidateIndexes: list[int] = Field(min_length=1, max_length=200)
    summary: str = Field(min_length=1, max_length=500)

    @field_validator("candidateIndexes")
    @classmethod
    def indexes_non_negative(cls, value: list[int]) -> list[int]:
        if any(index < 0 for index in value):
            raise ValueError("candidateIndexes must be non-negative")
        return value

    @field_validator("summary")
    @classmethod
    def not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped


class LlmReportDraft(_StrictModel):
    """Structured LLM output before deterministic aggregation."""

    title: str = Field(min_length=1, max_length=120)
    summary: str = Field(min_length=1, max_length=2000)
    opinionCandidates: list[LlmOpinionCandidate]
    categorySummaries: list[LlmCategorySummary] = Field(default_factory=list)
    commonOpinionSummaries: list[LlmCommonOpinionSummary] = Field(default_factory=list)
    conflictingOpinionSummaries: list[LlmConflictingOpinionSummary] = Field(
        default_factory=list
    )

    @field_validator("title", "summary")
    @classmethod
    def not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped


def report_generation_result_json_schema() -> dict[str, Any]:
    return ReportGenerationResult.model_json_schema()


def llm_report_draft_json_schema() -> dict[str, Any]:
    return LlmReportDraft.model_json_schema()
