"""Pure Kafka message handler tests (AI-007 Gate 3A)."""

from __future__ import annotations

import json
import logging

import pytest

from app.messaging.report_message_handler import (
    KafkaRecordLike,
    ReportMessageHandler,
)
from app.schemas.report_worker import CommitDecision, WorkerOutcome
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
)
from app.services.report_worker import ReportWorker


def _handler() -> ReportMessageHandler:
    normalized = sufficient_normalized()
    worker = ReportWorker(
        backend=FakeReportBackend(),
        input_port=FakeReportInput(normalized=normalized),
        generation=FakeGenerationService(results=[claimed_generation(normalized)]),
        evidence=FakeEvidenceService(results=[sample_evidence_result()]),
    )
    return ReportMessageHandler(worker)


def _record(
    *,
    key: bytes | str | None = b"7",
    body: dict | None = None,
) -> KafkaRecordLike:
    payload = body if body is not None else {
        "studyId": 7,
        "sessionId": 3,
        "apartmentId": 100,
        "occurredAt": "2026-08-01T09:15:30.123Z",
    }
    return KafkaRecordLike(
        key=key,
        value=json.dumps(payload).encode("utf-8"),
        offset=11,
        partition=0,
    )


@pytest.mark.asyncio
async def test_handler_happy_path_commits() -> None:
    result = await _handler().handle(_record())
    assert result.outcome == WorkerOutcome.COMPLETED
    assert result.commit_decision == CommitDecision.COMMIT


@pytest.mark.asyncio
async def test_handler_key_mismatch_is_invalid_event_policy_pending() -> None:
    result = await _handler().handle(_record(key=b"999"))
    assert result.outcome == WorkerOutcome.INVALID_EVENT
    assert result.commit_decision == CommitDecision.POLICY_PENDING


@pytest.mark.asyncio
async def test_handler_invalid_payload_is_policy_pending() -> None:
    result = await _handler().handle(
        _record(
            body={
                "studyId": 7,
                "sessionId": 3,
                "apartmentId": 100,
                "occurredAt": "2026-08-01T09:15:30",
            }
        )
    )
    assert result.outcome == WorkerOutcome.INVALID_EVENT
    assert result.commit_decision == CommitDecision.POLICY_PENDING


@pytest.mark.asyncio
async def test_handler_does_not_log_raw_payload_or_invalid_values(
    caplog: pytest.LogCaptureFixture,
) -> None:
    secret = "TOP-SECRET-PAYLOAD-VALUE"
    with caplog.at_level(logging.DEBUG):
        result = await _handler().handle(
            KafkaRecordLike(
                key=b"7",
                value=json.dumps(
                    {
                        "studyId": 7,
                        "sessionId": 3,
                        "apartmentId": 100,
                        "occurredAt": "2026-08-01T09:15:30",
                        "leak": secret,
                    }
                ).encode("utf-8"),
            )
        )
    assert result.outcome == WorkerOutcome.INVALID_EVENT
    joined = "\n".join(caplog.messages)
    text = caplog.text
    for haystack in (joined, text):
        assert secret not in haystack
        assert "leak" not in haystack
        assert "TOP-SECRET" not in haystack
        assert "2026-08-01T09:15:30" not in haystack
        assert "Traceback" not in haystack
        assert "ValidationError" not in haystack


@pytest.mark.asyncio
async def test_handler_accepts_string_key() -> None:
    result = await _handler().handle(_record(key="7"))
    assert result.outcome == WorkerOutcome.COMPLETED
