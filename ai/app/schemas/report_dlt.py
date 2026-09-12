"""Safe REPORT request DLT / dead-letter event (INF-007 Phase 2)."""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class ReportDeadLetterEvent(BaseModel):
    """DLT payload: identifiers and outcome only — never raw Kafka value."""

    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal[1] = 1
    originalTopic: str = Field(min_length=1, max_length=255)
    originalPartition: int = Field(ge=0)
    originalOffset: int = Field(ge=0)
    consumerGroup: str = Field(min_length=1, max_length=255)
    outcome: str = Field(min_length=1, max_length=64)
    errorCode: str | None = Field(default=None, max_length=100)
    failedAt: datetime
    studyId: int | None = Field(default=None, ge=1)
    sessionId: int | None = Field(default=None, ge=1)
    apartmentId: int | None = Field(default=None, ge=1)
    occurredAt: datetime | None = None
    payloadHash: str = Field(min_length=64, max_length=64)

    @field_validator("originalTopic", "consumerGroup", "outcome")
    @classmethod
    def non_blank(cls, value: str) -> str:
        text = value.strip()
        if not text:
            raise ValueError("field must not be blank")
        return text

    @field_validator("payloadHash")
    @classmethod
    def sha256_hex(cls, value: str) -> str:
        text = value.strip().lower()
        if len(text) != 64 or any(ch not in "0123456789abcdef" for ch in text):
            raise ValueError("payloadHash must be lowercase SHA-256 hex")
        return text

    @field_validator("failedAt", "occurredAt")
    @classmethod
    def require_timezone(cls, value: datetime | None) -> datetime | None:
        if value is None:
            return None
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("datetime must include a timezone")
        return value
