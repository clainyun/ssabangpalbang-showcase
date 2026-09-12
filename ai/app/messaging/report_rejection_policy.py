"""POLICY_PENDING rejection policies for INF-007 Phase 2 (block or DLT)."""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Protocol

from app.messaging.report_dlt import (
    DltPublishError,
    build_dead_letter_event,
    dlt_message_key,
    log_dlt_publish,
    payload_sha256,
)
from app.schemas.report_worker import WorkerOutcome

logger = logging.getLogger(__name__)

DLT_PUBLISH_FAILED = "DLT_PUBLISH_FAILED"


@dataclass(frozen=True)
class RejectionHandleResult:
    """Outcome of permanent-rejection handling.

    published=True means DLT was ACK'd and the Runtime may commit the
    original offset. BlockingRejectionPolicy always returns published=False.
    """

    published: bool


class ReportRejectionPolicy(Protocol):
    """Permanent rejection handling for POLICY_PENDING outcomes."""

    async def handle_policy_pending(
        self,
        *,
        outcome: WorkerOutcome,
        topic: str,
        partition: int,
        offset: int,
        raw_value: bytes | str | None,
        consumer_group: str,
        study_id: int | None,
        session_id: int | None,
        apartment_id: int | None,
        occurred_at: datetime | None,
        error_code: str | None,
    ) -> RejectionHandleResult:
        """Handle POLICY_PENDING without committing the original offset."""


class BlockingRejectionPolicy:
    """Safe default: observe only; never DLT-publish or drop the message."""

    async def handle_policy_pending(
        self,
        *,
        outcome: WorkerOutcome,
        topic: str,
        partition: int,
        offset: int,
        raw_value: bytes | str | None,
        consumer_group: str,
        study_id: int | None,
        session_id: int | None,
        apartment_id: int | None,
        occurred_at: datetime | None,
        error_code: str | None,
    ) -> RejectionHandleResult:
        del (
            outcome,
            topic,
            partition,
            offset,
            raw_value,
            consumer_group,
            study_id,
            session_id,
            apartment_id,
            occurred_at,
            error_code,
        )
        return RejectionHandleResult(published=False)


class KafkaDltRejectionPolicy:
    """Publish a safe DLT event with acks=all; never commit the source offset."""

    def __init__(
        self,
        *,
        producer: Any,
        dlt_topic: str,
    ) -> None:
        self._producer = producer
        self._dlt_topic = dlt_topic

    async def handle_policy_pending(
        self,
        *,
        outcome: WorkerOutcome,
        topic: str,
        partition: int,
        offset: int,
        raw_value: bytes | str | None,
        consumer_group: str,
        study_id: int | None,
        session_id: int | None,
        apartment_id: int | None,
        occurred_at: datetime | None,
        error_code: str | None,
    ) -> RejectionHandleResult:
        if self._producer is None:
            log_dlt_publish(
                topic=topic,
                partition=partition,
                offset=offset,
                dlt_topic=self._dlt_topic,
                error_code=error_code,
                success=False,
            )
            raise DltPublishError(DLT_PUBLISH_FAILED)

        event = build_dead_letter_event(
            original_topic=topic,
            original_partition=partition,
            original_offset=offset,
            consumer_group=consumer_group,
            outcome=outcome.value,
            error_code=error_code,
            payload_hash=payload_sha256(raw_value),
            study_id=study_id,
            session_id=session_id,
            apartment_id=apartment_id,
            occurred_at=occurred_at,
        )
        key = dlt_message_key(
            study_id=study_id,
            topic=topic,
            partition=partition,
            offset=offset,
        )
        body = event.model_dump_json().encode("utf-8")
        try:
            await self._producer.send_and_wait(
                self._dlt_topic,
                value=body,
                key=key.encode("utf-8"),
            )
        except Exception as exc:
            log_dlt_publish(
                topic=topic,
                partition=partition,
                offset=offset,
                dlt_topic=self._dlt_topic,
                error_code=error_code,
                success=False,
            )
            raise DltPublishError(DLT_PUBLISH_FAILED) from exc

        log_dlt_publish(
            topic=topic,
            partition=partition,
            offset=offset,
            dlt_topic=self._dlt_topic,
            error_code=error_code,
            success=True,
        )
        return RejectionHandleResult(published=True)
