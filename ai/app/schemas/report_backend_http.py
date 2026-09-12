"""HTTP DTOs for AI-007 Gate 3B-A Backend internal contract (DRAFT)."""

from __future__ import annotations

from datetime import datetime
from typing import Any, Generic, Literal, TypeVar

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from app.schemas.report_evidence import EvidenceLinkResult
from app.schemas.report_generation import ReportGenerationResult
from app.schemas.report_worker import AcquireStatus, WorkerStage


def _require_timezone(value: datetime, field_name: str) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name} must include a timezone")
    return value


class _HttpModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


PayloadT = TypeVar("PayloadT")


class ApiSuccessHttpResponse(_HttpModel, Generic[PayloadT]):
    """Backend common flat ApiResponse envelope for successful reads."""

    success: Literal[True]
    code: str = Field(min_length=1)
    message: str = Field(min_length=1)
    data: PayloadT
    timestamp: datetime

    @field_validator("code", "message")
    @classmethod
    def text_not_blank(cls, value: str) -> str:
        text = value.strip()
        if not text:
            raise ValueError("field must not be blank")
        return text

    @field_validator("timestamp")
    @classmethod
    def timestamp_has_timezone(cls, value: datetime) -> datetime:
        return _require_timezone(value, "timestamp")


class ApiErrorHttpResponse(_HttpModel):
    """Backend common flat ApiResponse envelope for HTTP errors."""

    success: Literal[False]
    code: str = Field(min_length=1)
    message: str = Field(min_length=1)
    data: Any | None
    timestamp: datetime

    @field_validator("code", "message")
    @classmethod
    def text_not_blank(cls, value: str) -> str:
        text = value.strip()
        if not text:
            raise ValueError("field must not be blank")
        return text

    @field_validator("timestamp")
    @classmethod
    def timestamp_has_timezone(cls, value: datetime) -> datetime:
        return _require_timezone(value, "timestamp")


class AcquireHttpRequest(_HttpModel):
    studyId: int = Field(ge=1)
    sessionId: int = Field(ge=1)
    apartmentId: int = Field(ge=1)
    occurredAt: datetime

    @field_validator("occurredAt")
    @classmethod
    def occurred_at_has_timezone(cls, value: datetime) -> datetime:
        return _require_timezone(value, "occurredAt")


class AcquireHttpResponse(_HttpModel):
    status: AcquireStatus
    reportId: int | None = None
    processingToken: str | None = None
    processingAttempt: int | None = Field(default=None, ge=1)
    # Optional observability fields. Backend TTL remains authoritative even if omitted.
    leaseExpiresAt: datetime | None = None
    retryAfterSeconds: int | None = Field(default=None, ge=0)

    @field_validator("leaseExpiresAt")
    @classmethod
    def lease_expires_has_timezone(
        cls, value: datetime | None
    ) -> datetime | None:
        if value is None:
            return None
        return _require_timezone(value, "leaseExpiresAt")

    @model_validator(mode="after")
    def acquired_requires_claim_fields(self) -> "AcquireHttpResponse":
        if self.status != AcquireStatus.ACQUIRED:
            return self
        if self.reportId is None:
            raise ValueError("reportId is required when status=ACQUIRED")
        if not self.processingToken:
            raise ValueError("processingToken is required when status=ACQUIRED")
        if self.processingAttempt is None or self.processingAttempt < 1:
            raise ValueError(
                "processingAttempt >= 1 is required when status=ACQUIRED"
            )
        return self


ProgressStage = Literal[
    "RECORD_COLLECTION",
    "STT_VALIDATION",
    "NORMALIZATION",
    "REPORT_GENERATION",
    "EVIDENCE_MAPPING",
    "RESULT_SAVING",
]


class ProgressHttpRequest(_HttpModel):
    processingToken: str = Field(min_length=1)
    processingAttempt: int = Field(ge=1)
    stage: ProgressStage

    @field_validator("processingToken")
    @classmethod
    def token_not_blank(cls, value: str) -> str:
        text = value.strip()
        if not text:
            raise ValueError("processingToken must not be blank")
        return text


class CompleteHttpRequest(_HttpModel):
    processingToken: str = Field(min_length=1)
    processingAttempt: int = Field(ge=1)
    generationResult: ReportGenerationResult
    evidenceResult: EvidenceLinkResult

    @field_validator("processingToken")
    @classmethod
    def token_not_blank(cls, value: str) -> str:
        text = value.strip()
        if not text:
            raise ValueError("processingToken must not be blank")
        return text


class FailHttpRequest(_HttpModel):
    processingToken: str = Field(min_length=1)
    processingAttempt: int = Field(ge=1)
    failedStage: WorkerStage
    errorCode: str = Field(min_length=1, max_length=100)
    message: str = Field(min_length=1, max_length=500)
    retryable: bool

    @field_validator("processingToken", "errorCode", "message")
    @classmethod
    def text_not_blank(cls, value: str) -> str:
        text = value.strip()
        if not text:
            raise ValueError("field must not be blank")
        return text
