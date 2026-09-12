"""Build safe REPORT DLT events without retaining raw Kafka payloads."""

from __future__ import annotations

import hashlib
import logging
from datetime import datetime, timezone
from typing import Any

from app.schemas.report_dlt import ReportDeadLetterEvent

logger = logging.getLogger(__name__)


def payload_sha256(raw: bytes | str | None) -> str:
    """Hash Kafka value bytes for DLT identity; raw bytes are not stored."""

    if raw is None:
        data = b""
    elif isinstance(raw, str):
        data = raw.encode("utf-8")
    else:
        data = raw
    return hashlib.sha256(data).hexdigest()


def dlt_message_key(*, study_id: int | None, topic: str, partition: int, offset: int) -> str:
    if study_id is not None and study_id >= 1:
        return str(study_id)
    return f"{topic}-{partition}-{offset}"


def build_dead_letter_event(
    *,
    original_topic: str,
    original_partition: int,
    original_offset: int,
    consumer_group: str,
    outcome: str,
    error_code: str | None,
    payload_hash: str,
    study_id: int | None = None,
    session_id: int | None = None,
    apartment_id: int | None = None,
    occurred_at: datetime | None = None,
    failed_at: datetime | None = None,
) -> ReportDeadLetterEvent:
    return ReportDeadLetterEvent(
        originalTopic=original_topic,
        originalPartition=original_partition,
        originalOffset=original_offset,
        consumerGroup=consumer_group,
        outcome=outcome,
        errorCode=error_code,
        failedAt=failed_at or datetime.now(timezone.utc),
        studyId=study_id,
        sessionId=session_id,
        apartmentId=apartment_id,
        occurredAt=occurred_at,
        payloadHash=payload_hash,
    )


def log_dlt_publish(
    *,
    topic: str,
    partition: int,
    offset: int,
    dlt_topic: str,
    error_code: str | None,
    success: bool,
) -> None:
    logger.info(
        "report dlt publish success=%s dltTopic=%s "
        "originalTopic=%s partition=%s offset=%s errorCode=%s",
        success,
        dlt_topic,
        topic,
        partition,
        offset,
        error_code or "-",
    )


class DltPublishError(Exception):
    """Raised when DLT publish or ACK fails (no raw body attached)."""

    def __init__(self, code: str = "DLT_PUBLISH_FAILED") -> None:
        super().__init__(code)
        self.code = code
