"""INF-007 Phase 2 Kafka runtime DLT offset and N/N+1 safety tests."""

from __future__ import annotations

import asyncio
import json

import pytest

from app.messaging.kafka_report import DLT_PUBLISH_FAILED, ReportKafkaWorker
from app.messaging.report_message_handler import ReportMessageHandler
from app.messaging.report_rejection_policy import (
    KafkaDltRejectionPolicy,
    RejectionHandleResult,
)
from app.schemas.report_worker import AcquireStatus
from app.services.report_worker import ReportWorker
from tests.fixtures.report_worker.builders import (
    claimed_generation,
    sample_evidence_result,
    sufficient_normalized,
)
from tests.fixtures.report_worker.fakes import (
    FakeEvidenceService,
    FakeGenerationService,
    FakeReportBackend,
    FakeReportInput,
    RecordingSleeper,
)
from tests.support.fake_kafka_consumer import FakeKafkaConsumer, FakeRecord

TOPIC = "field-visit.report.request.v1"
DLT = "field-visit.report.request.dlq.v1"


def _payload_bytes(study_id: int = 7) -> bytes:
    return json.dumps(
        {
            "studyId": study_id,
            "sessionId": 3,
            "apartmentId": 100,
            "occurredAt": "2026-08-01T09:15:30.123Z",
        }
    ).encode("utf-8")


def _handler() -> ReportMessageHandler:
    source = sufficient_normalized()
    worker = ReportWorker(
        backend=FakeReportBackend(),
        input_port=FakeReportInput(normalized=source),
        generation=FakeGenerationService(results=[claimed_generation(source)]),
        evidence=FakeEvidenceService(results=[sample_evidence_result()]),
        sleeper=RecordingSleeper(),
        provider_retry_delay_seconds=0.01,
    )
    return ReportMessageHandler(worker)


@pytest.mark.asyncio
async def test_dlt_success_commits_original_offset_plus_one() -> None:
    class OkProducer:
        async def send_and_wait(self, *args, **kwargs):  # noqa: ANN002,ANN003
            return None

    handler = _handler()
    record = FakeRecord(TOPIC, 0, 10, b"7", b'{"bad":true}')
    consumer = FakeKafkaConsumer(records=[record])
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        rejection_policy=KafkaDltRejectionPolicy(
            producer=OkProducer(), dlt_topic=DLT
        ),
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
        rejection_policy_name="dlt",
        dlt_topic=DLT,
        dlt_producer_started=True,
    )
    await worker.start()
    await asyncio.sleep(0.05)
    await worker.stop()
    assert consumer.committed == {(TOPIC, 0): 11}
    assert worker.health.dlt_publish_count == 1
    assert worker.health.last_dlt_at is not None
    assert worker.health.blocked_partitions == []


@pytest.mark.asyncio
async def test_dlt_failure_blocks_and_skips_n_plus_one() -> None:
    class BoomProducer:
        async def send_and_wait(self, *args, **kwargs):  # noqa: ANN002,ANN003
            raise TimeoutError("ack timeout")

    handler = _handler()
    records = [
        FakeRecord(TOPIC, 0, 2, b"7", b'{"bad":true}'),
        FakeRecord(TOPIC, 0, 3, b"7", _payload_bytes()),
    ]
    consumer = FakeKafkaConsumer(records=records)
    handled: list[int] = []
    original = handler.handle

    async def tracking_handle(rec):  # noqa: ANN001
        handled.append(rec.offset)
        return await original(rec)

    handler.handle = tracking_handle  # type: ignore[method-assign]
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        rejection_policy=KafkaDltRejectionPolicy(
            producer=BoomProducer(), dlt_topic=DLT
        ),
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
        rejection_policy_name="dlt",
        dlt_topic=DLT,
        dlt_producer_started=True,
    )
    await worker.start()
    await asyncio.sleep(0.1)
    await worker.stop()
    assert consumer.committed == {}
    assert 3 not in handled
    assert worker.health.last_error_code == DLT_PUBLISH_FAILED
    assert worker.health.dlt_failure_count == 1
    assert worker.is_healthy() is False


@pytest.mark.asyncio
async def test_dlt_success_allows_n_plus_one() -> None:
    class OkProducer:
        async def send_and_wait(self, *args, **kwargs):  # noqa: ANN002,ANN003
            return None

    handler = _handler()
    records = [
        FakeRecord(TOPIC, 0, 2, b"7", b'{"bad":true}'),
        FakeRecord(TOPIC, 0, 3, b"7", _payload_bytes()),
    ]
    consumer = FakeKafkaConsumer(records=records)
    handled: list[int] = []
    original = handler.handle

    async def tracking_handle(rec):  # noqa: ANN001
        handled.append(rec.offset)
        return await original(rec)

    handler.handle = tracking_handle  # type: ignore[method-assign]
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        rejection_policy=KafkaDltRejectionPolicy(
            producer=OkProducer(), dlt_topic=DLT
        ),
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
        rejection_policy_name="dlt",
        dlt_topic=DLT,
        dlt_producer_started=True,
    )
    await worker.start()
    await asyncio.sleep(0.15)
    await worker.stop()
    assert 2 in handled
    assert 3 in handled
    assert consumer.committed.get((TOPIC, 0)) == 4


@pytest.mark.asyncio
async def test_retry_later_does_not_invoke_dlt() -> None:
    calls = {"n": 0}

    class CountingPolicy:
        async def handle_policy_pending(self, **kwargs):  # noqa: ANN003
            calls["n"] += 1
            return RejectionHandleResult(published=False)

    backend = FakeReportBackend(
        status=AcquireStatus.ALREADY_PROCESSING,
    )
    source = sufficient_normalized()
    worker = ReportWorker(
        backend=backend,
        input_port=FakeReportInput(normalized=source),
        generation=FakeGenerationService(results=[claimed_generation(source)]),
        evidence=FakeEvidenceService(results=[sample_evidence_result()]),
        sleeper=RecordingSleeper(),
        provider_retry_delay_seconds=0.01,
    )
    handler = ReportMessageHandler(worker)
    record = FakeRecord(TOPIC, 0, 5, b"7", _payload_bytes())
    consumer = FakeKafkaConsumer(records=[record])
    kafka_worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        rejection_policy=CountingPolicy(),  # type: ignore[arg-type]
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
    )
    await kafka_worker.start()
    await asyncio.sleep(0.05)
    await kafka_worker.stop()
    assert calls["n"] == 0
    assert consumer.committed == {}


@pytest.mark.asyncio
async def test_block_policy_malformed_does_not_commit() -> None:
    handler = _handler()
    record = FakeRecord(TOPIC, 0, 1, b"7", b'{"x":1}')
    consumer = FakeKafkaConsumer(records=[record])
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
        rejection_policy_name="block",
    )
    await worker.start()
    await asyncio.sleep(0.05)
    await worker.stop()
    assert consumer.committed == {}
    assert worker.health.dlt_publish_count == 0
    assert worker.health.blocked_partitions
