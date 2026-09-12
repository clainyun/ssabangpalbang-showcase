"""Pydantic schemas for AI-006 claim ↔ TEXT/STT evidence linking.

AI-004 NormalizedReportInput and AI-005 ReportGenerationResult are reused
without modification. This module defines only AI-006 request/result and the
internal LLM draft contract.
"""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from app.schemas.report_generation import OpinionType, ReportGenerationResult
from app.schemas.report_input import NormalizedReportInput, ReportSourceType


class ClaimType(StrEnum):
    FEATURE_POSITIVE = "FEATURE_POSITIVE"
    FEATURE_CAUTION = "FEATURE_CAUTION"
    COMMON = "COMMON"
    CONFLICT = "CONFLICT"
    PARTICIPANT_OPINION = "PARTICIPANT_OPINION"


class EvidenceRole(StrEnum):
    SUPPORT = "SUPPORT"
    SUPPORT_POSITIVE = "SUPPORT_POSITIVE"
    SUPPORT_CAUTION = "SUPPORT_CAUTION"


class _StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class EvidenceLinkRequest(_StrictModel):
    normalizedInput: NormalizedReportInput
    generationResult: ReportGenerationResult


class EvidenceRef(_StrictModel):
    sourceType: Literal[ReportSourceType.TEXT, ReportSourceType.STT]
    sourceId: int = Field(ge=1)
    participantRef: str = Field(pattern=r"^P[1-9][0-9]*$")
    checklistItemId: int = Field(ge=1)
    category: str = Field(min_length=1, max_length=30)
    recordedAt: datetime
    evidenceRole: EvidenceRole

    @field_validator("category")
    @classmethod
    def category_not_blank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("must not be blank")
        return stripped

    @field_validator("recordedAt")
    @classmethod
    def recorded_at_has_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("recordedAt must include a timezone")
        return value


class EvidenceClaim(_StrictModel):
    claimKey: str = Field(min_length=1, max_length=100)
    claimType: ClaimType
    category: str | None = Field(default=None, max_length=30)
    label: str | None = Field(default=None, max_length=80)
    opinionType: OpinionType | None = None
    participantRefs: list[str] = Field(min_length=1)
    evidences: list[EvidenceRef] = Field(min_length=1)
    displayOrder: int = Field(ge=1)

    @field_validator("category", "label")
    @classmethod
    def optional_text_not_blank(cls, value: str | None) -> str | None:
        if value is None:
            return None
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
    def conflict_role_rules(self) -> "EvidenceClaim":
        roles = {item.evidenceRole for item in self.evidences}
        if self.claimType == ClaimType.CONFLICT:
            if EvidenceRole.SUPPORT in roles:
                raise ValueError("CONFLICT evidences must use side-specific roles")
            if EvidenceRole.SUPPORT_POSITIVE not in roles:
                raise ValueError("CONFLICT requires SUPPORT_POSITIVE evidence")
            if EvidenceRole.SUPPORT_CAUTION not in roles:
                raise ValueError("CONFLICT requires SUPPORT_CAUTION evidence")
        elif roles != {EvidenceRole.SUPPORT}:
            raise ValueError("non-CONFLICT evidences must use SUPPORT role")
        return self


class EvidenceLinkResult(_StrictModel):
    claims: list[EvidenceClaim]


# --- LLM draft contract (internal; not part of EvidenceLinkResult) ---


class LlmNormalEvidenceMapping(_StrictModel):
    claimKey: str = Field(min_length=1, max_length=100)
    # Field is required (no default). Explicit [] is allowed so the service can
    # classify empty evidence as MISSING_EVIDENCE (retryable). Missing field is
    # a schema violation. Final EvidenceClaim.evidences still requires >=1.
    sourceIndexes: list[int]

    @field_validator("sourceIndexes")
    @classmethod
    def indexes_non_negative(cls, value: list[int]) -> list[int]:
        if any(index < 0 for index in value):
            raise ValueError("sourceIndexes must be non-negative")
        return value


class LlmConflictEvidenceMapping(_StrictModel):
    claimKey: str = Field(min_length=1, max_length=100)
    # Both side arrays are required fields. Explicit [] is allowed and mapped to
    # MISSING_EVIDENCE by the service. Omitting a field is a schema violation.
    positiveSourceIndexes: list[int]
    cautionSourceIndexes: list[int]

    @field_validator("positiveSourceIndexes", "cautionSourceIndexes")
    @classmethod
    def indexes_non_negative(cls, value: list[int]) -> list[int]:
        if any(index < 0 for index in value):
            raise ValueError("source indexes must be non-negative")
        return value


class LlmEvidenceDraft(_StrictModel):
    """Structured LLM output before deterministic evidence assembly."""

    normalMappings: list[LlmNormalEvidenceMapping] = Field(default_factory=list)
    conflictMappings: list[LlmConflictEvidenceMapping] = Field(default_factory=list)


def evidence_link_result_json_schema() -> dict[str, Any]:
    return EvidenceLinkResult.model_json_schema()


def llm_evidence_draft_json_schema() -> dict[str, Any]:
    return LlmEvidenceDraft.model_json_schema()
