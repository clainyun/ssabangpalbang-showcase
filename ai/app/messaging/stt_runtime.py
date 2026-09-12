"""Lifecycle owner for the STT Kafka worker and its resources."""

from __future__ import annotations

import asyncio
import contextlib
import logging

from app.config import SttSettings
from app.messaging.idempotency import (
    RedisSttIdempotencyStore,
    create_idempotency_store,
)
from app.messaging.kafka_stt import (
    KafkaTopics,
    SttKafkaWorker,
    create_kafka_clients,
)
from app.providers.audio_store import create_audio_normalizer, create_audio_store
from app.providers.stt_provider import create_stt_provider
from app.services.stt_service import SttService

logger = logging.getLogger(__name__)


class SttRuntime:
    def __init__(
        self,
        *,
        worker: SttKafkaWorker,
        service: SttService,
        idempotency: RedisSttIdempotencyStore,
    ) -> None:
        self._worker = worker
        self._service = service
        self._idempotency = idempotency

    async def start(self) -> None:
        try:
            await self._idempotency.ping()
            await asyncio.to_thread(self._service.warmup)
            await self._worker.start()
        except BaseException:
            with contextlib.suppress(BaseException):
                await self._worker.stop()
            with contextlib.suppress(BaseException):
                await self._idempotency.close()
            self._service.close()
            raise
        logger.info("STT Kafka runtime started")

    async def stop(self) -> None:
        try:
            await self._worker.stop()
        finally:
            try:
                await self._idempotency.close()
            finally:
                self._service.close()
        logger.info("STT Kafka runtime stopped")

    def is_healthy(self) -> bool:
        return self._worker.is_running() and self._service.is_ready()


def create_stt_runtime(settings: SttSettings) -> SttRuntime:
    service = SttService(
        audio_store=create_audio_store(settings),
        normalizer=create_audio_normalizer(settings),
        provider=create_stt_provider(settings),
        timeout_seconds=settings.timeout_seconds,
        max_concurrency=settings.max_concurrency,
    )
    idempotency = create_idempotency_store(settings)
    consumer, producer = create_kafka_clients(settings)
    worker = SttKafkaWorker(
        consumer=consumer,
        producer=producer,
        service=service,
        idempotency=idempotency,
        topics=KafkaTopics(
            request=settings.request_topic,
            result=settings.result_topic,
            dlq=settings.dlq_topic,
        ),
        lease_seconds=settings.redis_lease_seconds,
        result_ttl_seconds=settings.redis_result_ttl_seconds,
        retry_delay_seconds=settings.retry_delay_seconds,
    )
    return SttRuntime(
        worker=worker,
        service=service,
        idempotency=idempotency,
    )
