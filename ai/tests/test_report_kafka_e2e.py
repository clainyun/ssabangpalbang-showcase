"""Opt-in isolated real-Kafka E2E for INF-007 Phase 2 Report Worker.

Requires:
  RUN_REPORT_KAFKA_E2E=1
  reachable Kafka bootstrap (default localhost:19092 from report-kafka-e2e compose)

Uses Contract Stub + Fake LLM only. Never touches ops Backend/DB/LLM.
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import secrets

import httpx
import pytest

from app.adapters.report_backend_http import ReportBackendHttpAdapter
from app.adapters.report_input_http import ReportInputHttpAdapter
from app.messaging.kafka_report import ReportKafkaWorker
from app.messaging.report_message_handler import ReportMessageHandler
from app.messaging.report_rejection_policy import KafkaDltRejectionPolicy
from app.services.report_worker import ReportWorker
from tests.fixtures.report_worker.builders import (
    claimed_generation,
    sample_evidence_result,
    sufficient_normalized,
)
from tests.fixtures.report_worker.fakes import (
    FakeEvidenceService,
    FakeGenerationService,
    RecordingSleeper,
)
from tests.support.report_contract_stub import create_report_contract_stub
from tests.support.report_kafka_e2e_isolation import (
    IsolationManifest,
    assert_isolation_safe,
)

pytestmark = pytest.mark.skipif(
    os.getenv("RUN_REPORT_KAFKA_E2E") != "1",
    reason="set RUN_REPORT_KAFKA_E2E=1 with isolated Kafka broker",
)

TOKEN = "e2e-test-report-internal-token-only"
logger = logging.getLogger(__name__)


def _suffix() -> str:
    return secrets.token_hex(4)


def _build_manifest(suffix: str, bootstrap: str) -> IsolationManifest:
    return IsolationManifest(
        compose_file="infra/docker-compose.report-kafka-e2e.yml",
        compose_project=f"inf007-report-e2e-{suffix}",
        services=("kafka",),
        volumes=("kafka-e2e-data",),
        kafka_bootstrap=bootstrap,
        request_topic=f"field-visit.report.request.v1.e2e.{suffix}",
        dlt_topic=f"field-visit.report.request.dlq.v1.e2e.{suffix}",
        consumer_group=f"ai.report-worker.e2e.{suffix}",
        backend_target="contract-stub",
        uses_postgresql=False,
        token_is_test_only=True,
        contains_ops_address=False,
    )


async def _ensure_topics(bootstrap: str, *topics: str) -> None:
    from aiokafka.admin import AIOKafkaAdminClient, NewTopic
    from aiokafka.errors import TopicAlreadyExistsError

    admin = AIOKafkaAdminClient(bootstrap_servers=bootstrap)
    await admin.start()
    try:
        try:
            await admin.create_topics(
                [
                    NewTopic(name=t, num_partitions=1, replication_factor=1)
                    for t in topics
                ],
                validate_only=False,
            )
        except TopicAlreadyExistsError:
            return
        except Exception as exc:
            # Some aiokafka versions wrap per-topic already-exists differently.
            name = type(exc).__name__
            if "TopicAlreadyExists" in name or "TopicAlreadyExistsError" in name:
                return
            # Do not swallow auth/connectivity/config failures.
            raise RuntimeError("REPORT_E2E_TOPIC_CREATE_FAILED") from None
    finally:
        await admin.close()


def _payload(study_id: int = 7) -> bytes:
    return json.dumps(
        {
            "studyId": study_id,
            "sessionId": 3,
            "apartmentId": 100,
            "occurredAt": "2026-08-01T09:15:30.123Z",
        }
    ).encode("utf-8")


@pytest.mark.asyncio
async def test_real_kafka_happy_path_and_dlt_policy() -> None:
    from aiokafka import AIOKafkaConsumer, AIOKafkaProducer, TopicPartition

    bootstrap = os.getenv("REPORT_KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:19092")
    suffix = _suffix()
    manifest = _build_manifest(suffix, bootstrap)
    assert_isolation_safe(manifest)
    logger.info("INF-007 isolation manifest: %s", manifest.as_log_dict())

    request_topic = manifest.request_topic
    dlt_topic = manifest.dlt_topic
    group = manifest.consumer_group
    await _ensure_topics(bootstrap, request_topic, dlt_topic)

    stub_app, stub_state = create_report_contract_stub(internal_token=TOKEN)
    transport = httpx.ASGITransport(app=stub_app)
    source = sufficient_normalized()

    async with httpx.AsyncClient(
        transport=transport, base_url="http://stub"
    ) as http_client:
        backend = ReportBackendHttpAdapter(
            client=http_client,
            base_url="http://stub",
            internal_token=TOKEN,
        )
        input_port = ReportInputHttpAdapter(
            client=http_client,
            base_url="http://stub",
            internal_token=TOKEN,
        )
        report_worker = ReportWorker(
            backend=backend,
            input_port=input_port,
            generation=FakeGenerationService(
                results=[claimed_generation(source), claimed_generation(source)]
            ),
            evidence=FakeEvidenceService(
                results=[sample_evidence_result(), sample_evidence_result()]
            ),
            sleeper=RecordingSleeper(),
            provider_retry_delay_seconds=0.01,
        )
        handler = ReportMessageHandler(report_worker)

        consumer = AIOKafkaConsumer(
            request_topic,
            bootstrap_servers=bootstrap,
            group_id=group,
            enable_auto_commit=False,
            auto_offset_reset="earliest",
            max_poll_records=1,
        )
        dlt_producer = AIOKafkaProducer(bootstrap_servers=bootstrap, acks="all")
        dlt_reader = AIOKafkaConsumer(
            dlt_topic,
            bootstrap_servers=bootstrap,
            group_id=f"{group}-dlt-reader",
            auto_offset_reset="earliest",
            enable_auto_commit=True,
        )
        producer = AIOKafkaProducer(bootstrap_servers=bootstrap, acks="all")

        await dlt_producer.start()
        await dlt_reader.start()
        await producer.start()
        worker = ReportKafkaWorker(
            consumer=consumer,
            handler=handler,
            rejection_policy=KafkaDltRejectionPolicy(
                producer=dlt_producer, dlt_topic=dlt_topic
            ),
            retry_backoff_seconds=0.2,
            consumer_group=group,
            rejection_policy_name="dlt",
            dlt_topic=dlt_topic,
            dlt_producer_started=True,
            topic_partition_factory=lambda t, p: TopicPartition(t, p),
        )
        await worker.start()
        try:
            # 1) happy path
            await producer.send_and_wait(
                request_topic, key=b"7", value=_payload(7)
            )
            async with asyncio.timeout(30):
                while not stub_state.reports_by_id or next(
                    iter(stub_state.reports_by_id.values())
                ).status != "DONE":
                    await asyncio.sleep(0.1)
            assert worker.is_healthy() is True

            # 2) duplicate → ALREADY_COMPLETED → commit
            await producer.send_and_wait(
                request_topic, key=b"7", value=_payload(7)
            )
            await asyncio.sleep(1.0)
            assert (
                next(iter(stub_state.reports_by_id.values())).status == "DONE"
            )

            # 3) malformed → DLT → commit
            bad = b'{"not":"valid-report-event"}'
            await producer.send_and_wait(request_topic, key=b"x", value=bad)
            dlt_msg = None
            async with asyncio.timeout(30):
                async for record in dlt_reader:
                    dlt_msg = json.loads(record.value.decode("utf-8"))
                    break
            assert dlt_msg is not None
            assert dlt_msg["originalTopic"] == request_topic
            assert "raw" not in dlt_msg
            assert "processingToken" not in dlt_msg
            assert "Authorization" not in json.dumps(dlt_msg)
            assert len(dlt_msg["payloadHash"]) == 64
            assert worker.health.dlt_publish_count >= 1
            assert worker.health.last_dlt_at is not None
            assert worker.is_healthy() is True
        finally:
            await worker.stop()
            await producer.stop()
            await dlt_producer.stop()
            await dlt_reader.stop()


@pytest.mark.asyncio
async def test_real_kafka_backend_401_blocks_without_dlt() -> None:
    from aiokafka import AIOKafkaConsumer, AIOKafkaProducer, TopicPartition

    bootstrap = os.getenv("REPORT_KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:19092")
    suffix = _suffix()
    manifest = _build_manifest(suffix, bootstrap)
    assert_isolation_safe(manifest)
    await _ensure_topics(bootstrap, manifest.request_topic, manifest.dlt_topic)

    dlt_calls = {"n": 0}

    class CountingProducer:
        async def send_and_wait(self, *args, **kwargs):  # noqa: ANN002,ANN003
            dlt_calls["n"] += 1
            return None

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            401,
            json={
                "success": False,
                "code": "UNAUTHORIZED",
                "message": "auth",
                "data": None,
                "timestamp": "2026-08-02T12:00:00+09:00",
            },
        )

    transport = httpx.MockTransport(handler)
    async with httpx.AsyncClient(transport=transport) as http_client:
        backend = ReportBackendHttpAdapter(
            client=http_client,
            base_url="http://backend",
            internal_token=TOKEN,
        )
        input_port = ReportInputHttpAdapter(
            client=http_client,
            base_url="http://backend",
            internal_token=TOKEN,
        )
        source = sufficient_normalized()
        report_worker = ReportWorker(
            backend=backend,
            input_port=input_port,
            generation=FakeGenerationService(results=[claimed_generation(source)]),
            evidence=FakeEvidenceService(results=[sample_evidence_result()]),
            sleeper=RecordingSleeper(),
            provider_retry_delay_seconds=0.01,
        )
        consumer = AIOKafkaConsumer(
            manifest.request_topic,
            bootstrap_servers=bootstrap,
            group_id=manifest.consumer_group,
            enable_auto_commit=False,
            auto_offset_reset="earliest",
            max_poll_records=1,
        )
        producer = AIOKafkaProducer(bootstrap_servers=bootstrap, acks="all")
        await producer.start()
        worker = ReportKafkaWorker(
            consumer=consumer,
            handler=ReportMessageHandler(report_worker),
            rejection_policy=KafkaDltRejectionPolicy(
                producer=CountingProducer(), dlt_topic=manifest.dlt_topic
            ),
            retry_backoff_seconds=0.2,
            consumer_group=manifest.consumer_group,
            rejection_policy_name="dlt",
            dlt_topic=manifest.dlt_topic,
            dlt_producer_started=True,
            topic_partition_factory=lambda t, p: TopicPartition(t, p),
        )
        await worker.start()
        try:
            await producer.send_and_wait(
                manifest.request_topic, key=b"7", value=_payload(7)
            )
            async with asyncio.timeout(20):
                while not worker.health.blocked_partitions:
                    await asyncio.sleep(0.1)
            assert dlt_calls["n"] == 0
            assert worker.health.last_error_code == "BACKEND_AUTH_ERROR"
            assert worker.is_healthy() is False
            tp = TopicPartition(manifest.request_topic, 0)
            committed = await consumer.committed(tp)
            assert committed is None or committed == 0
        finally:
            await worker.stop()
            await producer.stop()


@pytest.mark.asyncio
async def test_real_kafka_dlt_publish_failure_blocks_commit() -> None:
    from aiokafka import AIOKafkaConsumer, AIOKafkaProducer, TopicPartition

    bootstrap = os.getenv("REPORT_KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:19092")
    suffix = _suffix()
    manifest = _build_manifest(suffix, bootstrap)
    assert_isolation_safe(manifest)

    request_topic = manifest.request_topic
    # Intentionally do not create DLT topic and point producer at missing topic
    # with a closed producer to force publish failure.
    await _ensure_topics(bootstrap, request_topic)

    stub_app, _stub_state = create_report_contract_stub(internal_token=TOKEN)
    transport = httpx.ASGITransport(app=stub_app)
    source = sufficient_normalized()

    class ClosedProducer:
        async def send_and_wait(self, *args, **kwargs):  # noqa: ANN002,ANN003
            raise TimeoutError("forced dlt ack timeout")

    async with httpx.AsyncClient(
        transport=transport, base_url="http://stub"
    ) as http_client:
        backend = ReportBackendHttpAdapter(
            client=http_client,
            base_url="http://stub",
            internal_token=TOKEN,
        )
        input_port = ReportInputHttpAdapter(
            client=http_client,
            base_url="http://stub",
            internal_token=TOKEN,
        )
        report_worker = ReportWorker(
            backend=backend,
            input_port=input_port,
            generation=FakeGenerationService(results=[claimed_generation(source)]),
            evidence=FakeEvidenceService(results=[sample_evidence_result()]),
            sleeper=RecordingSleeper(),
            provider_retry_delay_seconds=0.01,
        )
        handler = ReportMessageHandler(report_worker)
        consumer = AIOKafkaConsumer(
            request_topic,
            bootstrap_servers=bootstrap,
            group_id=manifest.consumer_group,
            enable_auto_commit=False,
            auto_offset_reset="earliest",
            max_poll_records=1,
        )
        producer = AIOKafkaProducer(bootstrap_servers=bootstrap, acks="all")
        await producer.start()
        worker = ReportKafkaWorker(
            consumer=consumer,
            handler=handler,
            rejection_policy=KafkaDltRejectionPolicy(
                producer=ClosedProducer(),
                dlt_topic=manifest.dlt_topic,
            ),
            retry_backoff_seconds=0.2,
            consumer_group=manifest.consumer_group,
            rejection_policy_name="dlt",
            dlt_topic=manifest.dlt_topic,
            dlt_producer_started=True,
            topic_partition_factory=lambda t, p: TopicPartition(t, p),
        )
        await worker.start()
        try:
            await producer.send_and_wait(
                request_topic, key=b"x", value=b'{"bad":true}'
            )
            async with asyncio.timeout(20):
                while worker.health.dlt_failure_count < 1:
                    await asyncio.sleep(0.1)
            assert worker.health.last_error_code == "DLT_PUBLISH_FAILED"
            assert worker.is_healthy() is False
            # committed offsets should remain empty / not advance past blocked msg
            tp = TopicPartition(request_topic, 0)
            committed = await consumer.committed(tp)
            assert committed is None or committed == 0
        finally:
            await worker.stop()
            await producer.stop()

