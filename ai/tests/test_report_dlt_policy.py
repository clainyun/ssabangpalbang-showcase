"""INF-007 Phase 2 DLT DTO, config, and rejection-policy unit tests."""

from __future__ import annotations

from datetime import datetime, timezone

import pytest

from app.config import ReportWorkerSettings
from app.messaging.report_dlt import (
    build_dead_letter_event,
    dlt_message_key,
    payload_sha256,
)
from app.messaging.report_rejection_policy import (
    BlockingRejectionPolicy,
    KafkaDltRejectionPolicy,
)
from app.schemas.report_dlt import ReportDeadLetterEvent
from app.schemas.report_worker import WorkerOutcome


def test_payload_hash_is_sha256_hex() -> None:
    digest = payload_sha256(b'{"studyId":7}')
    assert len(digest) == 64
    assert digest == payload_sha256(b'{"studyId":7}')
    assert digest != payload_sha256(b'{"studyId":8}')


def test_dead_letter_event_forbids_extra_and_raw_fields() -> None:
    event = build_dead_letter_event(
        original_topic="field-visit.report.request.v1",
        original_partition=0,
        original_offset=3,
        consumer_group="ai.report-worker.v1",
        outcome=WorkerOutcome.INVALID_EVENT.value,
        error_code="INVALID_EVENT",
        payload_hash=payload_sha256(b"{}"),
        study_id=7,
    )
    dumped = event.model_dump()
    assert "raw" not in dumped
    assert "value" not in dumped
    assert "processingToken" not in dumped
    assert dumped["payloadHash"] == payload_sha256(b"{}")
    assert dumped["studyId"] == 7


def test_dlt_key_uses_study_or_offset_fallback() -> None:
    assert dlt_message_key(study_id=7, topic="t", partition=0, offset=1) == "7"
    assert (
        dlt_message_key(study_id=None, topic="t", partition=0, offset=9)
        == "t-0-9"
    )


def test_dead_letter_rejects_non_hex_hash() -> None:
    with pytest.raises(Exception):
        ReportDeadLetterEvent(
            originalTopic="t",
            originalPartition=0,
            originalOffset=0,
            consumerGroup="g",
            outcome="INVALID_EVENT",
            failedAt=datetime.now(timezone.utc),
            payloadHash="not-a-hash",
        )


def test_config_rejects_same_request_and_dlt_topic(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "false")
    monkeypatch.setenv("REPORT_KAFKA_TOPIC", "same.topic")
    monkeypatch.setenv("REPORT_KAFKA_DLT_TOPIC", "same.topic")
    with pytest.raises(ValueError, match="must differ"):
        ReportWorkerSettings.from_env()


def test_config_rejects_unknown_rejection_policy(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "false")
    monkeypatch.setenv("REPORT_KAFKA_REJECTION_POLICY", "drop")
    with pytest.raises(ValueError, match="block|dlt"):
        ReportWorkerSettings.from_env()


def test_config_dlt_requires_topic_when_enabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "true")
    monkeypatch.setenv("REPORT_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    monkeypatch.setenv("REPORT_BACKEND_BASE_URL", "http://backend")
    monkeypatch.setenv("REPORT_INTERNAL_TOKEN", "tok")
    monkeypatch.setenv("REPORT_KAFKA_REJECTION_POLICY", "dlt")
    monkeypatch.setenv("REPORT_KAFKA_DLT_TOPIC", "")
    with pytest.raises(ValueError, match="REPORT_KAFKA_DLT_TOPIC"):
        ReportWorkerSettings.from_env()


def test_token_not_in_repr(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "true")
    monkeypatch.setenv("REPORT_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    monkeypatch.setenv("REPORT_BACKEND_BASE_URL", "http://backend")
    monkeypatch.setenv("REPORT_INTERNAL_TOKEN", "super-secret-token")
    settings = ReportWorkerSettings.from_env()
    assert "super-secret-token" not in repr(settings)


@pytest.mark.asyncio
async def test_blocking_policy_never_publishes() -> None:
    result = await BlockingRejectionPolicy().handle_policy_pending(
        outcome=WorkerOutcome.INVALID_EVENT,
        topic="t",
        partition=0,
        offset=1,
        raw_value=b"{}",
        consumer_group="g",
        study_id=None,
        session_id=None,
        apartment_id=None,
        occurred_at=None,
        error_code="INVALID_EVENT",
    )
    assert result.published is False


@pytest.mark.asyncio
async def test_dlt_policy_acks_then_reports_published() -> None:
    class FakeProducer:
        def __init__(self) -> None:
            self.calls: list[tuple[str, bytes, bytes]] = []

        async def send_and_wait(
            self, topic: str, *, value: bytes, key: bytes
        ) -> None:
            self.calls.append((topic, value, key))

    producer = FakeProducer()
    policy = KafkaDltRejectionPolicy(
        producer=producer, dlt_topic="field-visit.report.request.dlq.v1"
    )
    result = await policy.handle_policy_pending(
        outcome=WorkerOutcome.CONTRACT_CONFLICT,
        topic="field-visit.report.request.v1",
        partition=0,
        offset=4,
        raw_value=b'{"studyId":7}',
        consumer_group="ai.report-worker.v1",
        study_id=7,
        session_id=3,
        apartment_id=100,
        occurred_at=datetime(2026, 8, 1, 9, 15, 30, tzinfo=timezone.utc),
        error_code="CONTRACT_CONFLICT",
    )
    assert result.published is True
    assert len(producer.calls) == 1
    topic, value, key = producer.calls[0]
    assert topic.endswith(".dlq.v1")
    assert key == b"7"
    body = value.decode("utf-8")
    assert "studyId" in body
    assert "processingToken" not in body
    assert '{"studyId":7}' not in body


@pytest.mark.asyncio
async def test_dlt_policy_propagates_publish_failure() -> None:
    class BoomProducer:
        async def send_and_wait(self, *args, **kwargs):  # noqa: ANN002,ANN003
            raise TimeoutError("broker timeout")

    policy = KafkaDltRejectionPolicy(
        producer=BoomProducer(), dlt_topic="dlt.topic"
    )
    with pytest.raises(Exception) as exc:
        await policy.handle_policy_pending(
            outcome=WorkerOutcome.INVALID_EVENT,
            topic="t",
            partition=0,
            offset=1,
            raw_value=b"{}",
            consumer_group="g",
            study_id=None,
            session_id=None,
            apartment_id=None,
            occurred_at=None,
            error_code="INVALID_EVENT",
        )
    assert "DLT_PUBLISH_FAILED" in str(exc.value)
