"""Strict Kafka contracts for AI-003 Spring Boot ↔ FastAPI STT events."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, TypeAdapter, field_validator


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class _ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class SttRequest(_ContractModel):
    schemaVersion: Literal[1]
    sttId: str = Field(min_length=1, max_length=50)
    attemptNo: int = Field(ge=1)
    audioFileId: int = Field(ge=1)
    objectKey: str = Field(min_length=1, max_length=1024)
    contentType: str = Field(min_length=1, max_length=255)
    language: str = Field(min_length=2, max_length=35)

    @field_validator("sttId", "objectKey", "contentType", "language")
    @classmethod
    def reject_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value


class _SttResult(_ContractModel):
    schemaVersion: Literal[1] = 1
    sttId: str = Field(min_length=1, max_length=50)
    attemptNo: int = Field(ge=1)


class SttProcessingResult(_SttResult):
    status: Literal["PROCESSING"] = "PROCESSING"
    startedAt: datetime

    @field_validator("startedAt")
    @classmethod
    def timestamp_has_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("startedAt must include a timezone")
        return value


class SttDoneResult(_SttResult):
    status: Literal["DONE"] = "DONE"
    textContent: str = Field(min_length=1)
    completedAt: datetime

    @field_validator("textContent")
    @classmethod
    def text_is_not_blank(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("textContent must not be blank")
        return normalized

    @field_validator("completedAt")
    @classmethod
    def timestamp_has_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("completedAt must include a timezone")
        return value


class SttFailedResult(_SttResult):
    status: Literal["FAILED"] = "FAILED"
    failCode: str = Field(min_length=1, max_length=100)
    failReason: str = Field(min_length=1, max_length=500)
    retryable: bool
    failedAt: datetime

    @field_validator("failCode", "failReason")
    @classmethod
    def failure_fields_are_not_blank(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("failure fields must not be blank")
        return normalized

    @field_validator("failedAt")
    @classmethod
    def timestamp_has_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("failedAt must include a timezone")
        return value


SttFinalResult = Annotated[
    SttDoneResult | SttFailedResult,
    Field(discriminator="status"),
]
SttResult = Annotated[
    SttProcessingResult | SttDoneResult | SttFailedResult,
    Field(discriminator="status"),
]

STT_FINAL_RESULT_ADAPTER = TypeAdapter(SttFinalResult)
STT_RESULT_ADAPTER = TypeAdapter(SttResult)


class SttRequestDlq(_ContractModel):
    schemaVersion: Literal[1] = 1
    sourceTopic: str = Field(min_length=1, max_length=249)
    sourcePartition: int = Field(ge=0)
    sourceOffset: int = Field(ge=0)
    key: str = Field(max_length=100)
    failCode: Literal["INVALID_MESSAGE"] = "INVALID_MESSAGE"
    failReason: str = Field(min_length=1, max_length=500)
    failedAt: datetime

    @field_validator("failedAt")
    @classmethod
    def timestamp_has_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("failedAt must include a timezone")
        return value
