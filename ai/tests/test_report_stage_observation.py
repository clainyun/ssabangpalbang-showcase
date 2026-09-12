"""attemptedStage / lastCompletedStage observation tests (Gate 3B-A)."""

from __future__ import annotations

import pytest

from app.schemas.report_worker import WorkerOutcome, WorkerStage
from app.services.report_backend_port import ReportBackendError
from app.services.report_worker import ReportWorker
from tests.fixtures.report_worker.builders import (
    claimed_generation,
    sample_evidence_result,
    sample_payload,
    sufficient_normalized,
)
from tests.fixtures.report_worker.fakes import (
    FakeEvidenceService,
    FakeGenerationService,
    FakeReportBackend,
    FakeReportInput,
    RecordingSleeper,
)


def _worker(backend: FakeReportBackend) -> ReportWorker:
    source = sufficient_normalized()
    return ReportWorker(
        backend=backend,
        input_port=FakeReportInput(normalized=source),
        generation=FakeGenerationService(results=[claimed_generation(source)]),
        evidence=FakeEvidenceService(results=[sample_evidence_result()]),
        sleeper=RecordingSleeper(),
        provider_retry_delay_seconds=0.01,
    )


@pytest.mark.asyncio
async def test_first_progress_failure_attempted_and_last_completed() -> None:
    backend = FakeReportBackend(
        progress_error=ReportBackendError(
            "BACKEND_TRANSIENT", "first progress failed", retryable=True
        )
    )
    result = await _worker(backend).process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert result.attempted_stage == WorkerStage.RECORD_COLLECTION
    assert result.last_completed_stage is None
    assert result.stages == []
    assert backend.fail_calls == []


@pytest.mark.asyncio
async def test_mid_progress_failure_keeps_last_completed() -> None:
    backend = FakeReportBackend(
        progress_error=ReportBackendError(
            "BACKEND_TRANSIENT", "mid progress failed", retryable=True
        ),
        progress_error_after=2,
    )
    result = await _worker(backend).process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert result.attempted_stage == WorkerStage.STT_VALIDATION
    assert result.last_completed_stage == WorkerStage.RECORD_COLLECTION
    assert result.stages == [WorkerStage.RECORD_COLLECTION]
    assert backend.fail_calls == []


@pytest.mark.asyncio
async def test_report_generation_progress_failure() -> None:
    backend = FakeReportBackend(
        progress_error=ReportBackendError(
            "BACKEND_TRANSIENT", "gen progress failed", retryable=True
        ),
        progress_error_after=4,
    )
    result = await _worker(backend).process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert result.attempted_stage == WorkerStage.REPORT_GENERATION
    assert result.last_completed_stage == WorkerStage.NORMALIZATION
    assert backend.fail_calls == []


@pytest.mark.asyncio
async def test_result_saving_progress_failure() -> None:
    backend = FakeReportBackend(
        progress_error=ReportBackendError(
            "BACKEND_TRANSIENT", "save progress failed", retryable=True
        ),
        progress_error_after=6,
    )
    result = await _worker(backend).process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert result.attempted_stage == WorkerStage.RESULT_SAVING
    assert result.last_completed_stage == WorkerStage.EVIDENCE_MAPPING
    assert backend.complete_calls == []
    assert backend.fail_calls == []
