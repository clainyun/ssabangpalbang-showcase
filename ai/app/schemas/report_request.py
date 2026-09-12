"""Kafka REPORT_REQUESTED v1 payload schema for AI-007 Gate 3A."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator


class ReportRequestedPayload(BaseModel):
    """Exact payload published by Backend ReportRequestedEvent."""

    model_config = ConfigDict(extra="forbid")

    studyId: int = Field(ge=1)
    sessionId: int = Field(ge=1)
    apartmentId: int = Field(ge=1)
    occurredAt: datetime

    @field_validator("studyId", "sessionId", "apartmentId", mode="before")
    @classmethod
    def ids_must_be_json_integers(cls, value: Any) -> Any:
        # Reject string/bool coercion while still allowing JSON numbers.
        if isinstance(value, bool) or not isinstance(value, int):
            raise ValueError("must be an integer")
        return value

    @field_validator("occurredAt")
    @classmethod
    def occurred_at_requires_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("occurredAt must include a timezone")
        return value
