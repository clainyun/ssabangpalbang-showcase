"""Fake Kafka consumer runtime tests for AI-007 Gate 3B-A."""

from __future__ import annotations

import asyncio
import json
import logging

import pytest

from app.messaging.kafka_report import RUNTIME_UNEXPECTED, ReportKafkaWorker
from app.messaging.report_message_handler import ReportMessageHandler
from app.schemas.report_worker import AcquireStatus
from app.services.report_backend_port import ReportBackendError
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


def _payload_bytes(study_id: int = 7) -> bytes:
    return json.dumps(
        {
            "studyId": study_id,
            "sessionId": 3,
            "apartmentId": 100,
            "occurredAt": "2026-08-01T09:15:30.123Z",
        }
    ).encode("utf-8")


def _handler(
    *,
    backend: FakeReportBackend | None = None,
    acquire_status: AcquireStatus | None = None,
) -> tuple[ReportMessageHandler, FakeReportBackend]:
    source = sufficient_normalized()
    backend = backend or FakeReportBackend()
    if acquire_status is not None:
        backend.status = acquire_status
    worker = ReportWorker(
        backend=backend,
        input_port=FakeReportInput(normalized=source),
        generation=FakeGenerationService(results=[claimed_generation(source)]),
        evidence=FakeEvidenceService(results=[sample_evidence_result()]),
        sleeper=RecordingSleeper(),
        provider_retry_delay_seconds=0.01,
    )
    return ReportMessageHandler(worker), backend


@pytest.mark.asyncio
async def test_commit_offset_plus_one_same_partition_only() -> None:
    handler, _ = _handler()
    record = FakeRecord(TOPIC, 0, 10, b"7", _payload_bytes())
    consumer = FakeKafkaConsumer(records=[record])
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
    )
    await worker.start()
    await asyncio.sleep(0.05)
    await worker.stop()
    assert consumer.committed == {(TOPIC, 0): 11}
    assert consumer.commit_calls == [{(TOPIC, 0): 11}]
    assert worker.health.assigned_partitions == [f"{TOPIC}:0"]


@pytest.mark.asyncio
async def test_retry_later_seek_pause_resume_no_commit() -> None:
    backend = FakeReportBackend(
        progress_error=ReportBackendError(
            "BACKEND_TRANSIENT", "retry", retryable=True
        )
    )
    handler, _ = _handler(backend=backend)
    record = FakeRecord(TOPIC, 0, 5, b"7", _payload_bytes())
    consumer = FakeKafkaConsumer(records=[record])
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
    )
    await worker.start()
    for _ in range(100):
        if consumer.resume_calls:
            break
        await asyncio.sleep(0.01)
    assert (TOPIC, 0) in consumer.resume_calls
    await worker.stop()
    assert consumer.committed == {}
    assert consumer.commit_calls == []
    assert any(offset == 5 for _, offset in consumer.seek_calls)
    assert (TOPIC, 0) in consumer.pause_calls


@pytest.mark.asyncio
async def test_already_processing_seek_pause_resume_no_commit() -> None:
    handler, _ = _handler(acquire_status=AcquireStatus.ALREADY_PROCESSING)
    record = FakeRecord(TOPIC, 0, 3, b"7", _payload_bytes())
    consumer = FakeKafkaConsumer(records=[record])
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
    )
    await worker.start()
    for _ in range(100):
        if consumer.resume_calls:
            break
        await asyncio.sleep(0.01)
    assert (TOPIC, 0) in consumer.resume_calls
    await worker.stop()
    assert consumer.committed == {}
    assert any(offset == 3 for _, offset in consumer.seek_calls)
    assert (TOPIC, 0) in consumer.pause_calls


@pytest.mark.asyncio
async def test_policy_pending_seek_pause_no_resume_blocked() -> None:
    handler, _ = _handler()
    record = FakeRecord(TOPIC, 0, 1, b"7", b'{"not":"valid"}')
    consumer = FakeKafkaConsumer(records=[record])
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
    )
    await worker.start()
    await asyncio.sleep(0.05)
    assert f"{TOPIC}:0" in worker.health.blocked_partitions
    assert worker.is_healthy() is False
    assert (TOPIC, 0) in consumer.pause_calls
    assert consumer.resume_calls == []
    assert consumer.committed == {}
    await worker.stop()


@pytest.mark.asyncio
async def test_unexpected_handler_exception_blocks_and_skips_next_offset() -> None:
    handled: list[int] = []

    class BoomHandler:
        async def handle(self, record):  # noqa: ANN001
            handled.append(record.offset)
            raise RuntimeError("boom secret=SHOULD_NOT_LEAK")

    records = [
        FakeRecord(TOPIC, 0, 10, b"7", _payload_bytes()),
        FakeRecord(TOPIC, 0, 11, b"7", _payload_bytes()),
    ]
    consumer = FakeKafkaConsumer(records=records)
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=BoomHandler(),  # type: ignore[arg-type]
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
    )
    await worker.start()
    await asyncio.sleep(0.12)
    await worker.stop()
    assert handled == [10]
    assert 11 not in handled
    assert consumer.committed == {}
    assert consumer.commit_calls == []
    assert f"{TOPIC}:0" in worker.health.blocked_partitions
    assert worker.health.last_error_code == RUNTIME_UNEXPECTED
    assert worker.is_healthy() is False
    assert any(offset == 10 for _, offset in consumer.seek_calls)
    assert (TOPIC, 0) in consumer.pause_calls
    assert consumer.resume_calls == []


@pytest.mark.asyncio
async def test_unexpected_handler_exception_does_not_log_secrets(
    caplog: pytest.LogCaptureFixture,
) -> None:
    class BoomHandler:
        async def handle(self, record):  # noqa: ANN001
            raise RuntimeError("boom secret=SHOULD_NOT_LEAK")

    consumer = FakeKafkaConsumer(
        records=[FakeRecord(TOPIC, 0, 10, b"7", _payload_bytes())]
    )
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=BoomHandler(),  # type: ignore[arg-type]
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
    )
    with caplog.at_level(logging.INFO):
        await worker.start()
        await asyncio.sleep(0.05)
        await worker.stop()
    text = "\n".join(item.getMessage() for item in caplog.records)
    assert "SHOULD_NOT_LEAK" not in text
    assert "RuntimeError" not in text
    assert "Traceback" not in text


@pytest.mark.asyncio
async def test_rejection_policy_exception_blocks_without_commit() -> None:
    handler, _ = _handler()

    class BoomPolicy:
        async def handle_policy_pending(self, **kwargs):  # noqa: ANN003
            raise RuntimeError("policy boom")

    record = FakeRecord(TOPIC, 0, 2, b"7", b'{"bad":true}')
    next_record = FakeRecord(TOPIC, 0, 3, b"7", _payload_bytes())
    consumer = FakeKafkaConsumer(records=[record, next_record])
    handled: list[int] = []
    original = handler.handle

    async def tracking_handle(rec):  # noqa: ANN001
        handled.append(rec.offset)
        return await original(rec)

    handler.handle = tracking_handle  # type: ignore[method-assign]
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        rejection_policy=BoomPolicy(),  # type: ignore[arg-type]
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
    )
    await worker.start()
    await asyncio.sleep(0.1)
    await worker.stop()
    assert consumer.committed == {}
    assert 3 not in handled
    assert f"{TOPIC}:0" in worker.health.blocked_partitions
    assert worker.health.last_error_code == RUNTIME_UNEXPECTED
    assert worker.is_healthy() is False


@pytest.mark.asyncio
async def test_commit_failure_blocks_and_does_not_skip() -> None:
    handler, _ = _handler()
    records = [
        FakeRecord(TOPIC, 0, 2, b"7", _payload_bytes()),
        FakeRecord(TOPIC, 0, 3, b"7", _payload_bytes()),
    ]
    consumer = FakeKafkaConsumer(records=records)
    consumer.commit_error = RuntimeError("commit failed")
    handled: list[int] = []
    original = handler.handle

    async def tracking_handle(rec):  # noqa: ANN001
        handled.append(rec.offset)
        return await original(rec)

    handler.handle = tracking_handle  # type: ignore[method-assign]
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
    )
    await worker.start()
    await asyncio.sleep(0.1)
    await worker.stop()
    assert consumer.committed == {}
    assert worker.health.last_error_code == "COMMIT_FAILED"
    assert f"{TOPIC}:0" in worker.health.blocked_partitions
    assert worker.is_healthy() is False
    assert 3 not in handled


@pytest.mark.asyncio
async def test_stop_during_inflight_does_not_commit_incomplete_offset() -> None:
    handler, _ = _handler()

    class BlockingHandler(ReportMessageHandler):
        def __init__(self, worker) -> None:
            super().__init__(worker)
            self.entered = asyncio.Event()

        async def handle(self, record):  # type: ignore[override]
            self.entered.set()
            await asyncio.sleep(30)
            return await super().handle(record)

    blocking = BlockingHandler(handler._worker)
    record = FakeRecord(TOPIC, 0, 4, b"7", _payload_bytes())
    consumer = FakeKafkaConsumer(records=[record])
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=blocking,
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
        shutdown_grace_seconds=0.05,
    )
    await worker.start()
    await asyncio.wait_for(blocking.entered.wait(), timeout=1)
    await worker.stop()
    assert consumer.committed == {}


@pytest.mark.asyncio
async def test_health_degraded_when_blocked_partitions() -> None:
    handler, _ = _handler()
    consumer = FakeKafkaConsumer(
        records=[FakeRecord(TOPIC, 0, 1, b"7", b'{"x":1}')]
    )
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
    )
    await worker.start()
    await asyncio.sleep(0.05)
    assert worker.is_healthy() is False
    assert worker.health.blocked_partitions
    await worker.stop()
