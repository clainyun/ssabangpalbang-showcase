"""Manual-commit Kafka worker for the AI-003 STT event contract."""

from __future__ import annotations

import asyncio
import contextlib
import logging
import uuid
from dataclasses import dataclass
from typing import Any

from pydantic import ValidationError

from app.config import SttSettings
from app.messaging.idempotency import SttIdempotencyStore
from app.schemas.stt import (
    SttDoneResult,
    SttFailedResult,
    SttProcessingResult,
    SttRequest,
    SttRequestDlq,
    utc_now,
)
from app.services.stt_service import SttProcessingError, SttService

logger = logging.getLogger(__name__)


class LeaseBusyError(Exception):
    """Another consumer currently owns the same STT attempt lease."""


@dataclass(frozen=True)
class KafkaTopics:
    request: str
    result: str
    dlq: str


class SttKafkaWorker:
    def __init__(
        self,
        *,
        consumer: Any,
        producer: Any,
        service: SttService,
        idempotency: SttIdempotencyStore,
        topics: KafkaTopics,
        lease_seconds: int,
        result_ttl_seconds: int,
        retry_delay_seconds: int = 2,
    ) -> None:
        self._consumer = consumer
        self._producer = producer
        self._service = service
        self._idempotency = idempotency
        self._topics = topics
        self._lease_seconds = lease_seconds
        self._result_ttl_seconds = result_ttl_seconds
        self._retry_delay_seconds = retry_delay_seconds
        self._task: asyncio.Task[None] | None = None

    async def start(self) -> None:
        await self._producer.start()
        try:
            await self._consumer.start()
        except Exception:
            await self._producer.stop()
            raise
        self._task = asyncio.create_task(self._consume(), name="stt-kafka-consumer")

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._task
        await self._consumer.stop()
        await self._producer.stop()

    def is_running(self) -> bool:
        return self._task is not None and not self._task.done()

    async def _consume(self) -> None:
        async for record in self._consumer:
            try:
                await self.handle_record(record)
            except asyncio.CancelledError:
                raise
            except LeaseBusyError:
                self._consumer.seek(
                    self._topic_partition(record.topic, record.partition),
                    record.offset,
                )
                await asyncio.sleep(self._retry_delay_seconds)
            except Exception:
                logger.exception(
                    "STT Kafka record handling failed topic=%s partition=%s offset=%s",
                    record.topic,
                    record.partition,
                    record.offset,
                )
                self._consumer.seek(
                    self._topic_partition(record.topic, record.partition),
                    record.offset,
                )
                await asyncio.sleep(self._retry_delay_seconds)

    async def handle_record(self, record: Any) -> None:
        request = await self._parse_or_dlq(record)
        if request is None:
            return

        logger.info(
            "STT Kafka request received sttId=%s attemptNo=%s topic=%s partition=%s offset=%s",
            request.sttId,
            request.attemptNo,
            record.topic,
            record.partition,
            record.offset,
        )
        idempotency_key = f"{request.sttId}:{request.attemptNo}"
        cached = await self._idempotency.get_result(idempotency_key)
        if cached is not None:
            await self._publish(self._topics.result, request.sttId, cached)
            await self._commit(record)
            logger.info(
                "STT cached result republished sttId=%s attemptNo=%s status=%s",
                request.sttId,
                request.attemptNo,
                cached.status,
            )
            return

        token = uuid.uuid4().hex
        acquired = await self._idempotency.acquire(
            idempotency_key,
            token,
            self._lease_seconds,
        )
        if not acquired:
            raise LeaseBusyError(idempotency_key)

        heartbeat = asyncio.create_task(
            self._heartbeat(idempotency_key, token),
            name=f"stt-lease-{request.attemptNo}",
        )
        try:
            processing = SttProcessingResult(
                sttId=request.sttId,
                attemptNo=request.attemptNo,
                startedAt=utc_now(),
            )
            await self._publish(self._topics.result, request.sttId, processing)

            try:
                text = await self._service.transcribe(request)
                final: SttDoneResult | SttFailedResult = SttDoneResult(
                    sttId=request.sttId,
                    attemptNo=request.attemptNo,
                    textContent=text,
                    completedAt=utc_now(),
                )
            except SttProcessingError as exc:
                final = SttFailedResult(
                    sttId=request.sttId,
                    attemptNo=request.attemptNo,
                    failCode=exc.code,
                    failReason=exc.reason,
                    retryable=exc.retryable,
                    failedAt=utc_now(),
                )

            saved = await self._idempotency.save_result_if_owner(
                idempotency_key,
                token,
                final,
                self._result_ttl_seconds,
            )
            if not saved:
                raise LeaseBusyError(idempotency_key)
            await self._publish(self._topics.result, request.sttId, final)
            await self._commit(record)
            logger.info(
                "STT Kafka request completed sttId=%s attemptNo=%s status=%s",
                request.sttId,
                request.attemptNo,
                final.status,
            )
        finally:
            heartbeat.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await heartbeat
            await self._idempotency.release(idempotency_key, token)

    async def _parse_or_dlq(self, record: Any) -> SttRequest | None:
        try:
            key = self._decode_key(record.key)
            request = SttRequest.model_validate_json(record.value)
            if key != request.sttId:
                raise ValueError("Kafka key must equal sttId")
        except (ValidationError, ValueError, TypeError, UnicodeDecodeError):
            key = self._safe_dlq_key(record.key)
            event = SttRequestDlq(
                sourceTopic=record.topic,
                sourcePartition=record.partition,
                sourceOffset=record.offset,
                key=key[:100],
                failReason="STT 요청 메시지 계약을 검증하지 못했습니다.",
                failedAt=utc_now(),
            )
            await self._publish(self._topics.dlq, key, event)
            await self._commit(record)
            logger.warning(
                "Invalid STT Kafka request sent to DLQ topic=%s partition=%s offset=%s",
                record.topic,
                record.partition,
                record.offset,
            )
            return None
        return request

    @staticmethod
    def _decode_key(value: bytes | None) -> str:
        if value is None:
            return ""
        return value.decode("utf-8")

    @staticmethod
    def _safe_dlq_key(value: bytes | None) -> str:
        if value is None:
            return ""
        return value.decode("utf-8", errors="replace")

    async def _heartbeat(self, key: str, token: str) -> None:
        delay = max(1, self._lease_seconds // 3)
        while True:
            await asyncio.sleep(delay)
            extended = await self._idempotency.extend(
                key,
                token,
                self._lease_seconds,
            )
            if not extended:
                logger.warning("STT idempotency lease was lost")
                return

    async def _publish(self, topic: str, key: str, event: Any) -> None:
        payload = event.model_dump_json(exclude_none=True).encode("utf-8")
        await self._producer.send_and_wait(
            topic,
            key=key.encode("utf-8"),
            value=payload,
        )

    async def _commit(self, record: Any) -> None:
        partition = self._topic_partition(record.topic, record.partition)
        await self._consumer.commit({partition: record.offset + 1})

    @staticmethod
    def _topic_partition(topic: str, partition: int) -> Any:
        try:
            from aiokafka import TopicPartition
        except ImportError:  # pragma: no cover - only supports dependency-light tests
            return (topic, partition)
        return TopicPartition(topic, partition)


def create_kafka_clients(settings: SttSettings) -> tuple[Any, Any]:
    try:
        from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
    except ImportError as exc:  # pragma: no cover - deployment dependency guard
        raise RuntimeError("Kafka client dependency is unavailable") from exc

    consumer = AIOKafkaConsumer(
        settings.request_topic,
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id=settings.consumer_group,
        client_id=f"{settings.kafka_client_id}-consumer",
        enable_auto_commit=False,
        auto_offset_reset="earliest",
        max_poll_records=1,
        max_poll_interval_ms=settings.max_poll_interval_ms,
    )
    producer = AIOKafkaProducer(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        client_id=f"{settings.kafka_client_id}-producer",
        acks="all",
    )
    return consumer, producer
