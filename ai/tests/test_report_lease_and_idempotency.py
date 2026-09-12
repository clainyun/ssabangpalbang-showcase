"""Lease re-claim, fail idempotency, and complete 409 mapping tests."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

import httpx
import pytest

from app.adapters.report_backend_http import ReportBackendHttpAdapter
from app.schemas.report_worker import (
    AcquireStatus,
    CommitDecision,
    WorkerOutcome,
    WorkerStage,
    commit_decision_for,
)
from app.services.report_backend_port import (
    AcquireRequest,
    CompleteCommand,
    FailCommand,
    ProgressUpdate,
    ReportBackendError,
)
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
from tests.support.report_contract_stub import create_report_contract_stub

TOKEN = "lease-test-token"
FIXED_NOW = datetime(2026, 8, 2, 4, 0, 0, tzinfo=timezone.utc)


@pytest.fixture
async def stub():
    clock = {"now": FIXED_NOW}

    def now_fn() -> datetime:
        return clock["now"]

    app, state = create_report_contract_stub(
        internal_token=TOKEN,
        lease_seconds=30,
        now_fn=now_fn,
    )
    state.reset()
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://stub") as client:
        backend = ReportBackendHttpAdapter(
            client=client,
            base_url="http://stub",
            internal_token=TOKEN,
        )
        yield backend, state, clock


def _req(study_id: int = 7) -> AcquireRequest:
    return AcquireRequest(
        study_id=study_id,
        session_id=3,
        apartment_id=100,
        occurred_at=FIXED_NOW,
    )


@pytest.mark.asyncio
async def test_lease_active_returns_already_processing(stub) -> None:
    backend, state, _ = stub
    first = await backend.acquire(_req())
    assert first.status == AcquireStatus.ACQUIRED
    assert first.processing_attempt == 1
    report = state.reports_by_id[first.report_id]
    assert report.status == "IN_PROGRESS"
    assert report.lease_expires_at is not None

    second = await backend.acquire(_req())
    assert second.status == AcquireStatus.ALREADY_PROCESSING
    assert second.report_id == first.report_id


@pytest.mark.asyncio
async def test_successful_progress_renews_lease_to_full_ttl(stub) -> None:
    backend, state, clock = stub
    acquired = await backend.acquire(_req(study_id=70))
    report = state.reports_by_id[acquired.report_id]
    original_expiry = report.lease_expires_at

    clock["now"] = FIXED_NOW + timedelta(seconds=20)
    await backend.update_progress(
        ProgressUpdate(
            report_id=acquired.report_id,
            processing_token=acquired.processing_token,
            processing_attempt=acquired.processing_attempt,
            stage=WorkerStage.RECORD_COLLECTION,
        )
    )

    assert report.lease_expires_at == FIXED_NOW + timedelta(seconds=50)
    assert report.lease_expires_at > original_expiry


@pytest.mark.asyncio
async def test_lease_expired_reclaims_with_new_token_and_attempt(stub) -> None:
    backend, state, clock = stub
    first = await backend.acquire(_req())
    old_token = first.processing_token
    clock["now"] = FIXED_NOW + timedelta(seconds=31)

    second = await backend.acquire(_req())
    assert second.status == AcquireStatus.ACQUIRED
    assert second.processing_attempt == 2
    assert second.processing_token != old_token
    assert second.processing_token == state.reports_by_id[first.report_id].processing_token


@pytest.mark.asyncio
async def test_stale_token_progress_complete_fail_rejected(stub) -> None:
    backend, state, clock = stub
    first = await backend.acquire(_req(study_id=8))
    old_token = first.processing_token
    old_attempt = first.processing_attempt
    clock["now"] = FIXED_NOW + timedelta(seconds=31)
    second = await backend.acquire(_req(study_id=8))
    assert second.processing_attempt == 2

    with pytest.raises(ReportBackendError) as progress_err:
        await backend.update_progress(
            ProgressUpdate(
                report_id=first.report_id,
                processing_token=old_token,
                processing_attempt=old_attempt,
                stage=WorkerStage.RECORD_COLLECTION,
            )
        )
    assert progress_err.value.retryable is False
    assert progress_err.value.conflict_code == "STALE_PROCESSING_TOKEN"

    with pytest.raises(ReportBackendError) as complete_err:
        await backend.complete(
            CompleteCommand(
                report_id=first.report_id,
                processing_token=old_token,
                processing_attempt=old_attempt,
                generation_result=claimed_generation(sufficient_normalized()),
                evidence_result=sample_evidence_result(),
            )
        )
    assert complete_err.value.retryable is False

    # Active token can still fail.
    await backend.fail(
        FailCommand(
            report_id=second.report_id,
            processing_token=second.processing_token,
            processing_attempt=second.processing_attempt,
            failed_stage=WorkerStage.NORMALIZATION,
            error_code="NORMALIZATION_FAILED",
            message="리포트 입력 정규화에 실패했습니다.",
            retryable=False,
        )
    )


@pytest.mark.asyncio
async def test_fail_idempotent_and_conflict(stub) -> None:
    backend, _, _ = stub
    acquired = await backend.acquire(_req(study_id=9))
    command = FailCommand(
        report_id=acquired.report_id,
        processing_token=acquired.processing_token,
        processing_attempt=acquired.processing_attempt,
        failed_stage=WorkerStage.REPORT_GENERATION,
        error_code="PROVIDER_FAILED",
        message="AI 제공자 호출에 실패했습니다.",
        retryable=False,
    )
    await backend.fail(command)
    await backend.fail(command)  # idempotent

    with pytest.raises(ReportBackendError) as error:
        await backend.fail(
            FailCommand(
                report_id=acquired.report_id,
                processing_token=acquired.processing_token,
                processing_attempt=acquired.processing_attempt,
                failed_stage=WorkerStage.EVIDENCE_MAPPING,
                error_code="MISSING_EVIDENCE",
                message="리포트 근거 연결에 실패했습니다.",
                retryable=False,
            )
        )
    assert error.value.retryable is False
    assert error.value.conflict_code == "FAIL_PAYLOAD_CONFLICT"


@pytest.mark.asyncio
async def test_failed_acquire_is_already_failed_not_auto_restart(stub) -> None:
    backend, _, _ = stub
    acquired = await backend.acquire(_req(study_id=10))
    await backend.fail(
        FailCommand(
            report_id=acquired.report_id,
            processing_token=acquired.processing_token,
            processing_attempt=acquired.processing_attempt,
            failed_stage=WorkerStage.NORMALIZATION,
            error_code="NORMALIZATION_FAILED",
            message="리포트 입력 정규화에 실패했습니다.",
            retryable=False,
        )
    )
    again = await backend.acquire(_req(study_id=10))
    assert again.status == AcquireStatus.ALREADY_FAILED
    assert again.report_id == acquired.report_id
    assert again.processing_token is None


@pytest.mark.asyncio
async def test_backend_authorized_retry_pending_can_be_acquired(stub) -> None:
    backend, state, _ = stub
    acquired = await backend.acquire(_req(study_id=12))
    await backend.fail(
        FailCommand(
            report_id=acquired.report_id,
            processing_token=acquired.processing_token,
            processing_attempt=acquired.processing_attempt,
            failed_stage=WorkerStage.NORMALIZATION,
            error_code="NORMALIZATION_FAILED",
            message="리포트 입력 정규화에 실패했습니다.",
            retryable=True,
        )
    )
    report = state.reports_by_id[acquired.report_id]
    old_token = report.processing_token

    # The public Backend retry endpoint owns this transition and event publication.
    report.status = "PENDING"
    report.processing_token = None
    report.lease_expires_at = None

    retried = await backend.acquire(_req(study_id=12))
    assert retried.status == AcquireStatus.ACQUIRED
    assert retried.report_id == acquired.report_id
    assert retried.processing_attempt == 2
    assert retried.processing_token != old_token


@pytest.mark.asyncio
async def test_worker_already_failed_commits_terminal() -> None:
    backend = FakeReportBackend(status=AcquireStatus.ALREADY_FAILED)
    worker = ReportWorker(
        backend=backend,
        input_port=FakeReportInput(normalized=sufficient_normalized()),
        generation=FakeGenerationService(results=[claimed_generation()]),
        evidence=FakeEvidenceService(results=[sample_evidence_result()]),
        sleeper=RecordingSleeper(),
    )
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert commit_decision_for(result.outcome) == CommitDecision.COMMIT


@pytest.mark.asyncio
async def test_complete_nonretryable_conflict_is_backend_rejected_not_retry_loop() -> None:
    backend = FakeReportBackend(
        complete_error=ReportBackendError(
            "BACKEND_CONTRACT_ERROR",
            "Backend contract error",
            retryable=False,
            conflict_code="COMPLETE_PAYLOAD_CONFLICT",
        )
    )
    source = sufficient_normalized()
    worker = ReportWorker(
        backend=backend,
        input_port=FakeReportInput(normalized=source),
        generation=FakeGenerationService(results=[claimed_generation(source)]),
        evidence=FakeEvidenceService(results=[sample_evidence_result()]),
        sleeper=RecordingSleeper(),
    )
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.BACKEND_REJECTED
    assert commit_decision_for(result.outcome) == CommitDecision.POLICY_PENDING
    assert result.outcome != WorkerOutcome.RETRY_LATER
    assert result.error_code == "BACKEND_CONTRACT_ERROR"
    assert result.conflict_code == "COMPLETE_PAYLOAD_CONFLICT"


@pytest.mark.asyncio
async def test_complete_payload_conflict_via_stub(stub) -> None:
    backend, _, _ = stub
    acquired = await backend.acquire(_req(study_id=11))
    first = claimed_generation(sufficient_normalized())
    await backend.complete(
        CompleteCommand(
            report_id=acquired.report_id,
            processing_token=acquired.processing_token,
            processing_attempt=acquired.processing_attempt,
            generation_result=first,
            evidence_result=sample_evidence_result(),
        )
    )
    from app.schemas.report_evidence import EvidenceLinkResult

    with pytest.raises(ReportBackendError) as error:
        await backend.complete(
            CompleteCommand(
                report_id=acquired.report_id,
                processing_token=acquired.processing_token,
                processing_attempt=acquired.processing_attempt,
                generation_result=first,
                evidence_result=EvidenceLinkResult(claims=[]),
            )
        )
    assert error.value.retryable is False
    assert error.value.conflict_code == "COMPLETE_PAYLOAD_CONFLICT"
    assert error.value.message == "Backend contract error"
