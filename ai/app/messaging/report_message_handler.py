"""Pure REPORT_REQUESTED Kafka record handler (no AIOKafkaConsumer)."""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from datetime import datetime
from typing import Any

from pydantic import ValidationError

from app.schemas.report_request import ReportRequestedPayload
from app.schemas.report_worker import (
    CommitDecision,
    WorkerErrorCode,
    WorkerOutcome,
    commit_decision_for,
)
from app.services.report_worker import ReportWorker, WorkerResult

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class KafkaRecordLike:
    """Minimal Kafka record shape for unit tests."""

    key: bytes | str | None
    value: bytes | str | None
    topic: str = "field-visit.report.request.v1"
    partition: int = 0
    offset: int = 0


@dataclass(frozen=True)
class HandlerResult:
    outcome: WorkerOutcome
    commit_decision: CommitDecision
    worker_result: WorkerResult | None = None
    error_code: str | None = None
    conflict_code: str | None = None
    study_id: int | None = None
    session_id: int | None = None
    apartment_id: int | None = None
    occurred_at: datetime | None = None


class ReportMessageHandler:
    """Validate Kafka key/payload, run Worker, map commit decision."""

    def __init__(self, worker: ReportWorker) -> None:
        self._worker = worker

    async def handle(self, record: KafkaRecordLike) -> HandlerResult:
        parsed = _parse_record(record)
        if isinstance(parsed, WorkerOutcome):
            return HandlerResult(
                outcome=parsed,
                commit_decision=commit_decision_for(parsed),
                error_code=WorkerErrorCode.INVALID_EVENT.value,
            )

        payload, _study_key = parsed
        result = await self._worker.process(payload)
        return HandlerResult(
            outcome=result.outcome,
            commit_decision=commit_decision_for(result.outcome),
            worker_result=result,
            error_code=result.error_code,
            conflict_code=result.conflict_code,
            study_id=payload.studyId,
            session_id=payload.sessionId,
            apartment_id=payload.apartmentId,
            occurred_at=payload.occurredAt,
        )


def _parse_record(
    record: KafkaRecordLike,
) -> tuple[ReportRequestedPayload, str] | WorkerOutcome:
    try:
        key_text = _decode_key(record.key)
        payload_obj = _decode_payload(record.value)
        payload = ReportRequestedPayload.model_validate(payload_obj)
    except Exception:
        # Never log raw key/value or ValidationError details (may contain values).
        logger.info(
            "invalid report request event topic=%s partition=%s offset=%s",
            record.topic,
            record.partition,
            record.offset,
        )
        return WorkerOutcome.INVALID_EVENT

    if key_text != str(payload.studyId):
        logger.info(
            "report request key mismatch topic=%s partition=%s offset=%s",
            record.topic,
            record.partition,
            record.offset,
        )
        return WorkerOutcome.INVALID_EVENT

    return payload, key_text


def _decode_key(key: bytes | str | None) -> str:
    if key is None:
        raise ValueError("missing key")
    if isinstance(key, bytes):
        text = key.decode("utf-8")
    else:
        text = str(key)
    text = text.strip()
    if not text:
        raise ValueError("blank key")
    if not text.isdigit() or int(text) < 1:
        raise ValueError("invalid key")
    return str(int(text))


def _decode_payload(value: bytes | str | None) -> Any:
    if value is None:
        raise ValueError("missing value")
    if isinstance(value, bytes):
        text = value.decode("utf-8")
    else:
        text = value
    data = json.loads(text)
    if not isinstance(data, dict):
        raise ValueError("payload must be object")
    return data


def safe_validation_error_code(error: ValidationError) -> str:
    """Expose only a stable code; never return invalid values."""

    del error
    return WorkerErrorCode.INVALID_EVENT.value
