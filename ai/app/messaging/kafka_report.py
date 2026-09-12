"""AIOKafkaConsumer runtime for AI-007 report worker (INF-007 Phase 2)."""

from __future__ import annotations

import asyncio
import contextlib
import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Callable

from app.messaging.report_dlt import DltPublishError
from app.messaging.report_dlt_eligibility import (
    dlt_safe_error_code,
    should_publish_to_dlt,
)
from app.messaging.report_message_handler import (
    KafkaRecordLike,
    ReportMessageHandler,
)
from app.messaging.report_rejection_policy import (
    BlockingRejectionPolicy,
    ReportRejectionPolicy,
)
from app.schemas.report_worker import CommitDecision

logger = logging.getLogger(__name__)

RUNTIME_UNEXPECTED = "RUNTIME_UNEXPECTED"
COMMIT_FAILED = "COMMIT_FAILED"
DLT_PUBLISH_FAILED = "DLT_PUBLISH_FAILED"


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class ReportWorkerHealth:
    enabled: bool = False
    running: bool = False
    consumer_started: bool = False
    assigned_partitions: list[str] = field(default_factory=list)
    blocked_partitions: list[str] = field(default_factory=list)
    last_poll_at: str | None = None
    last_success_at: str | None = None
    last_error_code: str | None = None
    attempted_stage: str | None = None
    last_completed_stage: str | None = None
    last_dlt_at: str | None = None
    dlt_publish_count: int = 0
    dlt_failure_count: int = 0
    rejection_policy: str = "block"
    dlt_topic: str | None = None
    dlt_producer_started: bool = False


class ReportKafkaWorker:
    """Manual-commit Kafka consumer loop for REPORT_REQUESTED events."""

    def __init__(
        self,
        *,
        consumer: Any,
        handler: ReportMessageHandler,
        retry_backoff_seconds: float = 2.0,
        rejection_policy: ReportRejectionPolicy | None = None,
        topic_partition_factory: Callable[[str, int], Any] | None = None,
        shutdown_grace_seconds: float = 30.0,
        consumer_group: str = "ai.report-worker.v1",
        rejection_policy_name: str = "block",
        dlt_topic: str | None = None,
        dlt_producer_started: bool = False,
    ) -> None:
        self._consumer = consumer
        self._handler = handler
        self._retry_backoff_seconds = max(0.1, float(retry_backoff_seconds))
        self._rejection_policy = rejection_policy or BlockingRejectionPolicy()
        self._tp_factory = topic_partition_factory or _default_topic_partition
        self._shutdown_grace_seconds = shutdown_grace_seconds
        self._consumer_group = consumer_group
        self._task: asyncio.Task[None] | None = None
        self._stop_requested = False
        self._in_flight = False
        self._started = False
        self._blocked: set[str] = set()
        self.health = ReportWorkerHealth(
            rejection_policy=rejection_policy_name,
            dlt_topic=dlt_topic,
            dlt_producer_started=dlt_producer_started,
        )

    async def start(self) -> None:
        if self._started:
            return
        try:
            await self._consumer.start()
            self._started = True
            self.health.consumer_started = True
            self._stop_requested = False
            self._refresh_assignment()
            self._task = asyncio.create_task(
                self._consume(),
                name="report-kafka-consumer",
            )
            self.health.running = True
            logger.info("report kafka worker started")
        except Exception:
            self._task = None
            self._started = False
            self.health.running = False
            self.health.consumer_started = False
            with contextlib.suppress(Exception):
                await self._consumer.stop()
            raise

    async def stop(self) -> None:
        # Always best-effort stop consumer even if start() failed mid-way.
        if not self._started and self._task is None:
            with contextlib.suppress(Exception):
                await self._consumer.stop()
            self.health.running = False
            self.health.consumer_started = False
            return
        self._stop_requested = True
        if self._task is not None:
            deadline = asyncio.get_running_loop().time() + self._shutdown_grace_seconds
            while self._in_flight and asyncio.get_running_loop().time() < deadline:
                await asyncio.sleep(0.05)
            self._task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._task
            self._task = None
        with contextlib.suppress(Exception):
            await self._consumer.stop()
        self._started = False
        self.health.running = False
        self.health.consumer_started = False
        logger.info("report kafka worker stopped")

    def is_running(self) -> bool:
        return self._task is not None and not self._task.done()

    def is_healthy(self) -> bool:
        if not self.health.consumer_started or not self.is_running():
            return False
        if self._blocked or self.health.blocked_partitions:
            return False
        if self.health.last_error_code in {
            COMMIT_FAILED,
            RUNTIME_UNEXPECTED,
            DLT_PUBLISH_FAILED,
        }:
            return False
        if (
            self.health.rejection_policy == "dlt"
            and not self.health.dlt_producer_started
        ):
            return False
        return True

    async def _consume(self) -> None:
        try:
            async for record in self._consumer:
                if self._stop_requested:
                    break
                self.health.last_poll_at = _utc_now().isoformat()
                self._refresh_assignment()
                partition_key = f"{record.topic}:{record.partition}"
                if partition_key in self._blocked:
                    await self._block_partition(
                        record,
                        error_code=self.health.last_error_code or "POLICY_PENDING",
                        resume_after_backoff=False,
                    )
                    continue
                self._in_flight = True
                try:
                    await self.handle_record(record)
                except asyncio.CancelledError:
                    raise
                except Exception:
                    if partition_key in self._blocked:
                        continue
                    logger.info(
                        "report kafka record handling failed "
                        "topic=%s partition=%s offset=%s errorCode=%s",
                        getattr(record, "topic", "-"),
                        getattr(record, "partition", "-"),
                        getattr(record, "offset", "-"),
                        RUNTIME_UNEXPECTED,
                    )
                    await self._block_partition(
                        record,
                        error_code=RUNTIME_UNEXPECTED,
                        resume_after_backoff=False,
                    )
                finally:
                    self._in_flight = False
        except asyncio.CancelledError:
            raise

    async def handle_record(self, record: Any) -> None:
        like = KafkaRecordLike(
            key=record.key,
            value=record.value,
            topic=getattr(record, "topic", "field-visit.report.request.v1"),
            partition=int(getattr(record, "partition", 0)),
            offset=int(getattr(record, "offset", 0)),
        )
        try:
            result = await self._handler.handle(like)
        except Exception:
            raise

        worker = result.worker_result
        if worker is not None:
            if worker.attempted_stage is not None:
                self.health.attempted_stage = worker.attempted_stage.value
            if worker.last_completed_stage is not None:
                self.health.last_completed_stage = worker.last_completed_stage.value
        if result.error_code:
            self.health.last_error_code = result.error_code

        logger.info(
            "report kafka handled topic=%s partition=%s offset=%s "
            "outcome=%s commitDecision=%s errorCode=%s "
            "attemptedStage=%s lastCompletedStage=%s reportId=%s "
            "processingAttempt=%s",
            like.topic,
            like.partition,
            like.offset,
            result.outcome.value,
            result.commit_decision.value,
            result.error_code or "-",
            self.health.attempted_stage or "-",
            self.health.last_completed_stage or "-",
            worker.report_id if worker and worker.report_id is not None else "-",
            (
                worker.processing_attempt
                if worker and worker.processing_attempt is not None
                else "-"
            ),
        )

        if result.commit_decision == CommitDecision.COMMIT:
            await self._commit(record)
            self.health.last_success_at = _utc_now().isoformat()
            return

        if result.commit_decision == CommitDecision.POLICY_PENDING:
            safe_code = dlt_safe_error_code(
                outcome=result.outcome,
                error_code=result.error_code,
                conflict_code=result.conflict_code,
            )
            if not should_publish_to_dlt(
                result.outcome,
                result.error_code,
                result.conflict_code,
            ):
                # Fail-closed: auth / generic contract / stale / unknown → block.
                await self._block_partition(
                    record,
                    error_code=safe_code or result.outcome.value,
                    resume_after_backoff=False,
                )
                return
            try:
                rejection = await self._rejection_policy.handle_policy_pending(
                    outcome=result.outcome,
                    topic=like.topic,
                    partition=like.partition,
                    offset=like.offset,
                    raw_value=like.value,
                    consumer_group=self._consumer_group,
                    study_id=result.study_id,
                    session_id=result.session_id,
                    apartment_id=result.apartment_id,
                    occurred_at=result.occurred_at,
                    error_code=safe_code,
                )
            except DltPublishError:
                self.health.dlt_failure_count += 1
                self.health.last_error_code = DLT_PUBLISH_FAILED
                await self._block_partition(
                    record,
                    error_code=DLT_PUBLISH_FAILED,
                    resume_after_backoff=False,
                )
                return
            except Exception:
                logger.info(
                    "report rejection policy failed "
                    "topic=%s partition=%s offset=%s errorCode=%s",
                    like.topic,
                    like.partition,
                    like.offset,
                    RUNTIME_UNEXPECTED,
                )
                await self._block_partition(
                    record,
                    error_code=RUNTIME_UNEXPECTED,
                    resume_after_backoff=False,
                )
                return

            if rejection.published:
                self.health.dlt_publish_count += 1
                self.health.last_dlt_at = _utc_now().isoformat()
                await self._commit(record)
                self.health.last_success_at = _utc_now().isoformat()
                return

            await self._block_partition(
                record,
                error_code=result.error_code or result.outcome.value,
                resume_after_backoff=False,
            )
            return

        # DO_NOT_COMMIT: RETRY_LATER / ALREADY_PROCESSING
        await self._block_partition(
            record,
            error_code=result.error_code or result.outcome.value,
            resume_after_backoff=True,
        )

    async def _commit(self, record: Any) -> None:
        partition = self._tp_factory(record.topic, record.partition)
        try:
            await self._consumer.commit({partition: record.offset + 1})
        except Exception:
            logger.info(
                "report kafka commit failed topic=%s partition=%s offset=%s",
                record.topic,
                record.partition,
                record.offset,
            )
            self.health.last_error_code = COMMIT_FAILED
            await self._block_partition(
                record,
                error_code=COMMIT_FAILED,
                resume_after_backoff=False,
            )
            raise

    async def _block_partition(
        self,
        record: Any,
        *,
        error_code: str,
        resume_after_backoff: bool,
    ) -> None:
        partition = self._tp_factory(record.topic, record.partition)
        partition_key = f"{record.topic}:{record.partition}"
        self._blocked.add(partition_key)
        self.health.blocked_partitions = sorted(self._blocked)
        self.health.last_error_code = error_code
        with contextlib.suppress(Exception):
            self._consumer.seek(partition, record.offset)
        with contextlib.suppress(Exception):
            self._consumer.pause(partition)
        await asyncio.sleep(self._retry_backoff_seconds)
        if resume_after_backoff and not self._stop_requested:
            with contextlib.suppress(Exception):
                self._consumer.resume(partition)
            self._blocked.discard(partition_key)
            self.health.blocked_partitions = sorted(self._blocked)

    def _refresh_assignment(self) -> None:
        with contextlib.suppress(Exception):
            assignment = self._consumer.assignment()
            labels: list[str] = []
            for item in assignment or ():
                if hasattr(item, "topic"):
                    labels.append(f"{item.topic}:{item.partition}")
                else:
                    topic, part = item
                    labels.append(f"{topic}:{part}")
            self.health.assigned_partitions = sorted(labels)


def _default_topic_partition(topic: str, partition: int) -> Any:
    try:
        from aiokafka import TopicPartition

        return TopicPartition(topic, partition)
    except ImportError:  # pragma: no cover
        return (topic, partition)


def create_report_kafka_consumer(settings: Any) -> Any:
    """Create AIOKafkaConsumer for report worker (enabled path only)."""

    try:
        from aiokafka import AIOKafkaConsumer
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("Kafka client dependency is unavailable") from exc

    return AIOKafkaConsumer(
        settings.kafka_topic,
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id=settings.kafka_group_id,
        client_id=f"{settings.kafka_group_id}-consumer",
        enable_auto_commit=False,
        auto_offset_reset=settings.kafka_auto_offset_reset,
        max_poll_records=1,
        max_poll_interval_ms=settings.kafka_max_poll_interval_ms,
    )


def create_report_kafka_dlt_producer(settings: Any) -> Any:
    """Create AIOKafkaProducer for report DLT (policy=dlt only)."""

    try:
        from aiokafka import AIOKafkaProducer
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("Kafka client dependency is unavailable") from exc

    return AIOKafkaProducer(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        client_id=f"{settings.kafka_group_id}-dlt-producer",
        acks="all",
    )
