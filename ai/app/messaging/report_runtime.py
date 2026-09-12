"""Lifecycle owner for the AI-007 report Kafka worker (INF-007 Phase 2)."""

from __future__ import annotations

import asyncio
import contextlib
import logging
from typing import Any

import httpx

from app.adapters.report_backend_http import ReportBackendHttpAdapter
from app.adapters.report_input_http import ReportInputHttpAdapter
from app.config import ReportWorkerSettings
from app.messaging.kafka_report import (
    ReportKafkaWorker,
    ReportWorkerHealth,
    create_report_kafka_consumer,
    create_report_kafka_dlt_producer,
)
from app.messaging.report_message_handler import ReportMessageHandler
from app.messaging.report_rejection_policy import (
    BlockingRejectionPolicy,
    KafkaDltRejectionPolicy,
)
from app.providers.llm_provider import create_llm_provider
from app.services.report_evidence import ReportEvidenceLinkService
from app.services.report_generation import ReportGenerationService
from app.services.report_worker import ReportWorker

logger = logging.getLogger(__name__)


class ReportRuntime:
    def __init__(
        self,
        *,
        settings: ReportWorkerSettings,
        worker: ReportKafkaWorker,
        http_client: httpx.AsyncClient,
        dlt_producer: Any | None = None,
    ) -> None:
        self._settings = settings
        self._worker = worker
        self._http_client = http_client
        self._dlt_producer = dlt_producer
        self._started = False
        self._dlt_started = False

    async def start(self) -> None:
        if self._started:
            return
        try:
            if self._dlt_producer is not None:
                await self._dlt_producer.start()
                self._dlt_started = True
                self._worker.health.dlt_producer_started = True
            await self._worker.start()
            self._started = True
            logger.info("report runtime started")
        except Exception:
            await self._cleanup_partial_start()
            raise

    async def _cleanup_partial_start(self) -> None:
        with contextlib.suppress(Exception):
            await self._worker.stop()
        if self._dlt_started and self._dlt_producer is not None:
            with contextlib.suppress(Exception):
                await self._dlt_producer.stop()
            self._dlt_started = False
            self._worker.health.dlt_producer_started = False
        with contextlib.suppress(Exception):
            await self._http_client.aclose()
        self._started = False

    async def stop(self) -> None:
        if not self._started and not self._worker.is_running():
            with contextlib.suppress(Exception):
                await self._http_client.aclose()
            return
        try:
            await self._worker.stop()
        finally:
            if self._dlt_producer is not None:
                with contextlib.suppress(Exception):
                    if hasattr(self._dlt_producer, "flush"):
                        await self._dlt_producer.flush()
                with contextlib.suppress(Exception):
                    await self._dlt_producer.stop()
                self._dlt_started = False
                self._worker.health.dlt_producer_started = False
            with contextlib.suppress(Exception):
                await self._http_client.aclose()
            self._started = False
        logger.info("report runtime stopped")

    def is_healthy(self) -> bool:
        if not self._settings.enabled:
            return True
        return self._worker.is_healthy()

    def health_snapshot(self) -> ReportWorkerHealth:
        snap = self._worker.health
        snap.enabled = self._settings.enabled
        snap.running = self._worker.is_running() and self._settings.enabled
        snap.rejection_policy = self._settings.kafka_rejection_policy
        snap.dlt_topic = self._settings.kafka_dlt_topic or None
        snap.dlt_producer_started = self._dlt_started
        return snap


def create_report_runtime(
    settings: ReportWorkerSettings,
    *,
    generation: Any | None = None,
    evidence: Any | None = None,
    consumer: Any | None = None,
    dlt_producer: Any | None = None,
    rejection_policy: Any | None = None,
) -> ReportRuntime:
    """Wire HTTP adapters + Kafka worker. Call only when enabled."""

    settings.validate_for_enabled()
    timeout = httpx.Timeout(settings.backend_timeout_seconds)
    client = httpx.AsyncClient(
        base_url=settings.backend_base_url.rstrip("/"),
        timeout=timeout,
    )
    backend = ReportBackendHttpAdapter(
        client=client,
        base_url=settings.backend_base_url,
        internal_token=settings.internal_token,
    )
    input_port = ReportInputHttpAdapter(
        client=client,
        base_url=settings.backend_base_url,
        internal_token=settings.internal_token,
    )
    if generation is None or evidence is None:
        provider = create_llm_provider()
        generation = generation or ReportGenerationService(provider)
        evidence = evidence or ReportEvidenceLinkService(provider)
    report_worker = ReportWorker(
        backend=backend,
        input_port=input_port,
        generation=generation,
        evidence=evidence,
        sleeper=asyncio.sleep,
        provider_retry_delay_seconds=settings.retry_backoff_seconds,
    )
    handler = ReportMessageHandler(report_worker)
    kafka_consumer = consumer or create_report_kafka_consumer(settings)

    producer = dlt_producer
    policy = rejection_policy
    if policy is None:
        if settings.uses_dlt:
            producer = producer or create_report_kafka_dlt_producer(settings)
            policy = KafkaDltRejectionPolicy(
                producer=producer,
                dlt_topic=settings.kafka_dlt_topic,
            )
        else:
            producer = None
            policy = BlockingRejectionPolicy()

    kafka_worker = ReportKafkaWorker(
        consumer=kafka_consumer,
        handler=handler,
        retry_backoff_seconds=settings.retry_backoff_seconds,
        rejection_policy=policy,
        consumer_group=settings.kafka_group_id,
        rejection_policy_name=settings.kafka_rejection_policy,
        dlt_topic=settings.kafka_dlt_topic if settings.uses_dlt else None,
        dlt_producer_started=False,
    )
    return ReportRuntime(
        settings=settings,
        worker=kafka_worker,
        http_client=client,
        dlt_producer=producer if settings.uses_dlt else None,
    )
