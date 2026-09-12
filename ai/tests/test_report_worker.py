"""ReportWorker orchestration tests (AI-007 Gate 3A)."""

from __future__ import annotations

import logging

import pytest

from app.exceptions.report_evidence import ReportEvidenceLinkError
from app.exceptions.report_generation import ReportGenerationError
from app.schemas.report_evidence import EvidenceLinkResult
from app.schemas.report_generation import (
    OpinionType,
    ParticipantOpinion,
    ReportGenerationResult,
)
from app.schemas.report_input import NormalizedReportInput
from app.schemas.report_worker import (
    AcquireStatus,
    CommitDecision,
    WorkerErrorCode,
    WorkerOutcome,
    WorkerStage,
    commit_decision_for,
)
from app.exceptions.report_worker import ReportInputError
from app.services.report_backend_port import ReportBackendError
from app.services.report_evidence import build_claim_specs
from app.services.report_worker import (
    ReportWorker,
    _safe_backend_error_code,
    _safe_normalization_error_code,
    _safe_source_error_code,
    has_semantic_claims,
    is_deterministic_data_insufficient,
)
from tests.fixtures.report_worker.builders import (
    claimed_generation,
    empty_claims_with_usable_sources,
    insufficient_generation,
    insufficient_normalized,
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


def _worker(
    *,
    backend: FakeReportBackend | None = None,
    normalized=None,
    generation: FakeGenerationService | None = None,
    evidence: FakeEvidenceService | None = None,
    sleeper: RecordingSleeper | None = None,
) -> tuple[ReportWorker, FakeReportBackend, FakeReportInput, FakeGenerationService, FakeEvidenceService, RecordingSleeper]:
    source = normalized if normalized is not None else sufficient_normalized()
    backend = backend or FakeReportBackend()
    input_port = FakeReportInput(normalized=source)
    if generation is None:
        generation = FakeGenerationService(results=[claimed_generation(source)])
    if evidence is None:
        evidence = FakeEvidenceService(results=[sample_evidence_result()])
    sleeper = sleeper or RecordingSleeper()
    worker = ReportWorker(
        backend=backend,
        input_port=input_port,
        generation=generation,
        evidence=evidence,
        sleeper=sleeper,
        provider_retry_delay_seconds=0.25,
    )
    return worker, backend, input_port, generation, evidence, sleeper


@pytest.mark.asyncio
async def test_happy_path_stage_order_and_full_result_passthrough() -> None:
    worker, backend, input_port, generation, evidence, _ = _worker()
    result = await worker.process(sample_payload())

    assert result.outcome == WorkerOutcome.COMPLETED
    assert backend.stages == [
        WorkerStage.RECORD_COLLECTION,
        WorkerStage.STT_VALIDATION,
        WorkerStage.NORMALIZATION,
        WorkerStage.REPORT_GENERATION,
        WorkerStage.EVIDENCE_MAPPING,
        WorkerStage.RESULT_SAVING,
    ]
    assert WorkerStage.COMPLETED not in backend.stages
    assert input_port.load_calls == 1
    assert input_port.normalize_calls == 1
    assert generation.calls == 1
    assert evidence.calls == 1
    assert len(backend.complete_calls) == 1
    command = backend.complete_calls[0]
    assert command.processing_token == "tok-1"
    assert command.processing_attempt == 1
    assert command.generation_result.title == "정상 리포트"
    assert command.evidence_result.claims
    assert all(
        call.processing_token == "tok-1" for call in backend.progress_calls
    )


@pytest.mark.asyncio
async def test_already_completed_skips_ai_and_commits() -> None:
    backend = FakeReportBackend(status=AcquireStatus.ALREADY_COMPLETED)
    worker, backend, input_port, generation, evidence, _ = _worker(backend=backend)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.ALREADY_COMPLETED
    assert commit_decision_for(result.outcome) == CommitDecision.COMMIT
    assert input_port.load_calls == 0
    assert generation.calls == 0
    assert evidence.calls == 0


@pytest.mark.asyncio
async def test_already_processing_does_not_commit() -> None:
    backend = FakeReportBackend(status=AcquireStatus.ALREADY_PROCESSING)
    worker, backend, input_port, generation, evidence, _ = _worker(backend=backend)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.ALREADY_PROCESSING
    assert commit_decision_for(result.outcome) == CommitDecision.DO_NOT_COMMIT
    assert backend.progress_calls == []
    assert input_port.load_calls == 0
    assert generation.calls == 0
    assert evidence.calls == 0


@pytest.mark.asyncio
async def test_contract_conflict_is_policy_pending_without_fail() -> None:
    backend = FakeReportBackend(status=AcquireStatus.CONTRACT_CONFLICT)
    worker, backend, *_ = _worker(backend=backend)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.CONTRACT_CONFLICT
    assert commit_decision_for(result.outcome) == CommitDecision.POLICY_PENDING
    assert backend.fail_calls == []


@pytest.mark.asyncio
async def test_acquired_requires_processing_token_and_attempt() -> None:
    backend = FakeReportBackend(omit_token=True)
    worker, *_ = _worker(backend=backend)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.BACKEND_REJECTED
    assert result.error_code == WorkerErrorCode.BACKEND_CONTRACT_ERROR.value
    assert commit_decision_for(result.outcome) == CommitDecision.POLICY_PENDING

    backend = FakeReportBackend(omit_attempt=True)
    worker, *_ = _worker(backend=backend)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.BACKEND_REJECTED


@pytest.mark.asyncio
async def test_deterministic_insufficient_skips_evidence_and_completes() -> None:
    normalized = insufficient_normalized()
    generation = FakeGenerationService(results=[insufficient_generation(normalized)])
    evidence = FakeEvidenceService(results=[sample_evidence_result()])
    worker, backend, _, generation, evidence, _ = _worker(
        normalized=normalized,
        generation=generation,
        evidence=evidence,
    )
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.COMPLETED
    assert evidence.calls == 0
    assert WorkerStage.EVIDENCE_MAPPING not in backend.stages
    assert backend.complete_calls[0].evidence_result == EvidenceLinkResult(claims=[])
    assert is_deterministic_data_insufficient(
        normalized, insufficient_generation(normalized)
    )


@pytest.mark.asyncio
async def test_canonical_insufficient_with_usable_sources_completes() -> None:
    normalized = sufficient_normalized()
    canonical = insufficient_generation(normalized)
    generation = FakeGenerationService(results=[canonical])
    evidence = FakeEvidenceService(results=[sample_evidence_result()])
    worker, backend, _, _, evidence, _ = _worker(
        normalized=normalized,
        generation=generation,
        evidence=evidence,
    )

    result = await worker.process(sample_payload())

    assert result.outcome == WorkerOutcome.COMPLETED
    assert evidence.calls == 0
    assert WorkerStage.EVIDENCE_MAPPING not in backend.stages
    assert backend.complete_calls[0].generation_result == canonical
    assert backend.complete_calls[0].evidence_result == EvidenceLinkResult(claims=[])
    assert canonical.metrics.fieldRecordCount == 2
    assert commit_decision_for(result.outcome) == CommitDecision.COMMIT


def _alter_canonical_insufficient(
    field: str,
    normalized: NormalizedReportInput,
) -> ReportGenerationResult:
    canonical = insufficient_generation(normalized)
    if field == "title":
        return canonical.model_copy(update={"title": "변조된 제목"})
    if field == "summary":
        return canonical.model_copy(update={"summary": "변조된 요약"})
    if field == "metrics":
        metrics = canonical.metrics.model_copy(
            update={"fieldRecordCount": canonical.metrics.fieldRecordCount + 1}
        )
        return canonical.model_copy(update={"metrics": metrics})
    if field == "metric_encoding":
        metrics = canonical.metrics.model_copy(
            update={"fieldRecordCount": float(canonical.metrics.fieldRecordCount)}
        )
        return canonical.model_copy(update={"metrics": metrics})
    if field == "categories":
        categories = list(reversed(canonical.categories))
        return canonical.model_copy(update={"categories": categories})
    if field == "counts":
        category = canonical.categories[0].model_copy(
            update={"positiveOpinionCount": 1}
        )
        return canonical.model_copy(
            update={"categories": [category, *canonical.categories[1:]]}
        )
    if field == "opinions":
        opinion = ParticipantOpinion(
            participantRef="P1",
            participantLabel="참여자 1",
            opinionType=OpinionType.POSITIVE,
            summary="변조된 의견",
        )
        category = canonical.categories[0].model_copy(
            update={"participantOpinions": [opinion]}
        )
        return canonical.model_copy(
            update={"categories": [category, *canonical.categories[1:]]}
        )
    raise AssertionError(f"unknown field: {field}")


@pytest.mark.parametrize(
    "altered_field",
    [
        "title",
        "summary",
        "metrics",
        "metric_encoding",
        "categories",
        "counts",
        "opinions",
    ],
)
@pytest.mark.asyncio
async def test_altered_insufficient_result_fails_before_evidence(
    altered_field: str,
) -> None:
    normalized = sufficient_normalized()
    altered = _alter_canonical_insufficient(altered_field, normalized)
    generation = FakeGenerationService(results=[altered])
    evidence = FakeEvidenceService(results=[sample_evidence_result()])
    worker, backend, _, _, evidence, _ = _worker(
        normalized=normalized,
        generation=generation,
        evidence=evidence,
    )

    result = await worker.process(sample_payload())

    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert result.error_code == WorkerErrorCode.GENERATION_RESULT_INVALID.value
    assert evidence.calls == 0
    assert backend.complete_calls == []
    assert backend.fail_calls[0].retryable is False
    assert commit_decision_for(result.outcome) == CommitDecision.COMMIT


@pytest.mark.asyncio
async def test_abnormal_empty_claims_with_usable_sources_fails() -> None:
    normalized = sufficient_normalized()
    generation = FakeGenerationService(
        results=[empty_claims_with_usable_sources(normalized)]
    )
    evidence = FakeEvidenceService(results=[sample_evidence_result()])
    worker, backend, _, generation, evidence, _ = _worker(
        normalized=normalized,
        generation=generation,
        evidence=evidence,
    )
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert result.error_code == WorkerErrorCode.GENERATION_RESULT_INVALID.value
    assert evidence.calls == 0
    assert backend.complete_calls == []
    assert backend.fail_calls
    assert backend.fail_calls[0].retryable is False
    assert commit_decision_for(result.outcome) == CommitDecision.COMMIT


@pytest.mark.asyncio
async def test_generation_retryable_then_success_without_sleep_side_effect() -> None:
    normalized = sufficient_normalized()
    sleeper = RecordingSleeper()
    generation = FakeGenerationService(
        results=[
            ReportGenerationError("PROVIDER_FAILED", "LLM provider call failed", retryable=True),
            ReportGenerationError("PROVIDER_FAILED", "LLM provider call failed", retryable=True),
            claimed_generation(normalized),
        ]
    )
    worker, backend, input_port, generation, evidence, sleeper = _worker(
        normalized=normalized,
        generation=generation,
        sleeper=sleeper,
    )
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.COMPLETED
    assert generation.calls == 3
    assert input_port.normalize_calls == 1
    assert evidence.calls == 1
    assert sleeper.delays == [0.25, 0.25]


@pytest.mark.asyncio
async def test_generation_retry_exhausted_fails_terminal() -> None:
    generation = FakeGenerationService(
        results=[
            ReportGenerationError("PROVIDER_FAILED", "LLM provider call failed", retryable=True),
            ReportGenerationError("PROVIDER_FAILED", "LLM provider call failed", retryable=True),
            ReportGenerationError("PROVIDER_FAILED", "LLM provider call failed", retryable=True),
        ]
    )
    worker, backend, *_ = _worker(generation=generation)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert result.error_code == WorkerErrorCode.PROVIDER_FAILED.value
    assert backend.fail_calls[0].retryable is True
    assert backend.complete_calls == []


@pytest.mark.asyncio
async def test_generation_nonretryable_fails_immediately() -> None:
    generation = FakeGenerationService(
        results=[
            ReportGenerationError("INPUT_TOO_LARGE", "usable source text exceeds"),
            claimed_generation(),
        ]
    )
    worker, backend, _, generation, *_ = _worker(generation=generation)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert generation.calls == 1
    assert result.error_code == WorkerErrorCode.INPUT_TOO_LARGE.value


@pytest.mark.asyncio
async def test_generation_invalid_reference_retryable_then_success() -> None:
    """참조 키 불일치(label 등)는 일시적 LLM 출력 불량이라 재시도로 회복해야 한다."""
    normalized = sufficient_normalized()
    generation = FakeGenerationService(
        results=[
            ReportGenerationError(
                "INVALID_REFERENCE",
                "LLM common summary references unknown candidate key",
                retryable=True,
            ),
            claimed_generation(normalized),
        ]
    )
    worker, backend, _, generation, evidence, _ = _worker(
        normalized=normalized,
        generation=generation,
    )
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.COMPLETED
    assert generation.calls == 2
    assert evidence.calls == 1
    assert backend.fail_calls == []


@pytest.mark.asyncio
async def test_generation_invalid_reference_without_flag_fails_immediately() -> None:
    """같은 코드라도 발생 지점이 retryable 을 켜지 않았으면 재시도하지 않는다."""
    generation = FakeGenerationService(
        results=[
            ReportGenerationError(
                "INVALID_REFERENCE",
                "usable source references unknown checklist item",
            ),
            claimed_generation(),
        ]
    )
    worker, backend, _, generation, *_ = _worker(generation=generation)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert generation.calls == 1
    assert result.error_code == WorkerErrorCode.INVALID_REFERENCE.value
    assert backend.fail_calls[0].retryable is False


@pytest.mark.asyncio
async def test_evidence_missing_retries_then_success_without_regenerating() -> None:
    normalized = sufficient_normalized()
    generation = FakeGenerationService(results=[claimed_generation(normalized)])
    evidence = FakeEvidenceService(
        results=[
            ReportEvidenceLinkError(
                "MISSING_EVIDENCE", "required semantic claim evidence is missing",
                retryable=True,
            ),
            ReportEvidenceLinkError(
                "MISSING_EVIDENCE", "required semantic claim evidence is missing",
                retryable=True,
            ),
            sample_evidence_result(),
        ]
    )
    worker, _, _, generation, evidence, sleeper = _worker(
        normalized=normalized,
        generation=generation,
        evidence=evidence,
    )
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.COMPLETED
    assert generation.calls == 1
    assert evidence.calls == 3
    assert sleeper.delays == [0.25, 0.25]


@pytest.mark.asyncio
async def test_evidence_missing_exhausted_and_provider_success_paths() -> None:
    evidence = FakeEvidenceService(
        results=[
            ReportEvidenceLinkError(
                "MISSING_EVIDENCE", "missing", retryable=True
            ),
            ReportEvidenceLinkError(
                "MISSING_EVIDENCE", "missing", retryable=True
            ),
            ReportEvidenceLinkError(
                "MISSING_EVIDENCE", "missing", retryable=True
            ),
        ]
    )
    worker, backend, *_ = _worker(evidence=evidence)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert result.error_code == WorkerErrorCode.MISSING_EVIDENCE.value

    evidence = FakeEvidenceService(
        results=[
            ReportEvidenceLinkError(
                "PROVIDER_FAILED", "LLM provider call failed", retryable=True
            ),
            sample_evidence_result(),
        ]
    )
    worker, *_ = _worker(evidence=evidence)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.COMPLETED


@pytest.mark.asyncio
async def test_evidence_nonretryable_fails_immediately() -> None:
    evidence = FakeEvidenceService(
        results=[
            ReportEvidenceLinkError(
                "INVALID_REFERENCE", "invalid", retryable=False
            ),
            sample_evidence_result(),
        ]
    )
    worker, backend, _, generation, evidence, _ = _worker(evidence=evidence)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert evidence.calls == 1
    assert generation.calls == 1
    assert result.error_code == WorkerErrorCode.INVALID_REFERENCE.value


@pytest.mark.parametrize(
    ("retryable", "expected_outcome", "expected_commit_decision"),
    [
        (True, WorkerOutcome.RETRY_LATER, CommitDecision.DO_NOT_COMMIT),
        (False, WorkerOutcome.BACKEND_REJECTED, CommitDecision.POLICY_PENDING),
    ],
)
@pytest.mark.asyncio
async def test_progress_failure_follows_backend_retry_policy_without_fail(
    retryable: bool,
    expected_outcome: WorkerOutcome,
    expected_commit_decision: CommitDecision,
) -> None:
    backend = FakeReportBackend(
        progress_error=ReportBackendError(
            "BACKEND_CONTRACT_ERROR" if not retryable else "BACKEND_TRANSIENT",
            "progress failed with secret=SHOULD_NOT_LEAK",
            retryable=retryable,
        )
    )
    worker, backend, input_port, generation, evidence, _ = _worker(backend=backend)
    result = await worker.process(sample_payload())
    assert result.outcome == expected_outcome
    assert commit_decision_for(result.outcome) == expected_commit_decision
    assert result.outcome != WorkerOutcome.TERMINAL_FAILED
    assert input_port.load_calls == 0
    assert generation.calls == 0
    assert evidence.calls == 0
    assert backend.fail_calls == []
    assert backend.complete_calls == []


@pytest.mark.asyncio
async def test_mid_progress_failure_stops_pipeline() -> None:
    backend = FakeReportBackend(
        progress_error=ReportBackendError(
            "BACKEND_TRANSIENT", "mid progress failed", retryable=True
        ),
        progress_error_after=2,
    )
    worker, backend, input_port, generation, evidence, _ = _worker(backend=backend)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert backend.stages == [
        WorkerStage.RECORD_COLLECTION,
        WorkerStage.STT_VALIDATION,
    ]
    assert input_port.load_calls == 1
    assert input_port.normalize_calls == 0
    assert generation.calls == 0
    assert evidence.calls == 0
    assert backend.complete_calls == []
    assert backend.fail_calls == []


@pytest.mark.asyncio
async def test_complete_failure_does_not_call_fail() -> None:
    backend = FakeReportBackend(
        complete_error=ReportBackendError(
            "BACKEND_TRANSIENT", "complete failed", retryable=True
        )
    )
    worker, backend, *_ = _worker(backend=backend)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert backend.complete_calls
    assert backend.fail_calls == []
    assert commit_decision_for(result.outcome) == CommitDecision.DO_NOT_COMMIT


@pytest.mark.asyncio
async def test_fail_persist_failure_returns_retry_later() -> None:
    generation = FakeGenerationService(
        results=[ReportGenerationError("INPUT_TOO_LARGE", "too large")]
    )
    backend = FakeReportBackend(
        fail_error=ReportBackendError(
            "BACKEND_TRANSIENT", "fail failed", retryable=True
        )
    )
    worker, backend, *_ = _worker(backend=backend, generation=generation)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert backend.fail_calls
    assert commit_decision_for(result.outcome) == CommitDecision.DO_NOT_COMMIT


@pytest.mark.asyncio
async def test_provider_attempt_not_sent_to_backend_fail() -> None:
    generation = FakeGenerationService(
        results=[
            ReportGenerationError("PROVIDER_FAILED", "LLM provider call failed", retryable=True),
            ReportGenerationError("PROVIDER_FAILED", "LLM provider call failed", retryable=True),
            ReportGenerationError("PROVIDER_FAILED", "LLM provider call failed", retryable=True),
        ]
    )
    worker, backend, *_ = _worker(generation=generation)
    await worker.process(sample_payload())
    assert backend.fail_calls[0].processing_attempt == 1
    assert backend.fail_calls[0].error_code == "PROVIDER_FAILED"


def test_has_semantic_claims_matches_ai006_claim_specs() -> None:
    normalized = sufficient_normalized()
    claimed = claimed_generation(normalized)
    empty = empty_claims_with_usable_sources(normalized)
    insufficient = insufficient_generation(insufficient_normalized())

    assert has_semantic_claims(claimed) is True
    assert len(build_claim_specs(claimed, normalized)) >= 1
    assert has_semantic_claims(empty) is False
    assert build_claim_specs(empty, normalized) == []
    assert has_semantic_claims(insufficient) is False
    assert build_claim_specs(insufficient, insufficient_normalized()) == []


def test_commit_decision_matrix() -> None:
    assert commit_decision_for(WorkerOutcome.COMPLETED) == CommitDecision.COMMIT
    assert commit_decision_for(WorkerOutcome.ALREADY_COMPLETED) == CommitDecision.COMMIT
    assert commit_decision_for(WorkerOutcome.TERMINAL_FAILED) == CommitDecision.COMMIT
    assert commit_decision_for(WorkerOutcome.RETRY_LATER) == CommitDecision.DO_NOT_COMMIT
    assert (
        commit_decision_for(WorkerOutcome.ALREADY_PROCESSING)
        == CommitDecision.DO_NOT_COMMIT
    )
    assert (
        commit_decision_for(WorkerOutcome.CONTRACT_CONFLICT)
        == CommitDecision.POLICY_PENDING
    )
    assert (
        commit_decision_for(WorkerOutcome.INVALID_EVENT)
        == CommitDecision.POLICY_PENDING
    )
    assert (
        commit_decision_for(WorkerOutcome.BACKEND_REJECTED)
        == CommitDecision.POLICY_PENDING
    )


def _assert_log_safe(caplog: pytest.LogCaptureFixture, *forbidden: str) -> None:
    text = caplog.text
    messages = "\n".join(caplog.messages)
    for item in forbidden:
        assert item not in text
        assert item not in messages
    assert "Traceback" not in text
    assert "exc_info" not in text


@pytest.mark.asyncio
async def test_worker_logs_omit_secret_and_raw_text(
    caplog: pytest.LogCaptureFixture,
) -> None:
    generation = FakeGenerationService(
        results=[
            ReportGenerationError(
                "PROVIDER_FAILED",
                "secret-token-should-not-leak semanticText=원문",
                retryable=True,
            )
        ]
        + [
            ReportGenerationError("PROVIDER_FAILED", "x", retryable=True),
            ReportGenerationError("PROVIDER_FAILED", "x", retryable=True),
        ]
    )
    with caplog.at_level(logging.DEBUG):
        worker, *_ = _worker(generation=generation)
        await worker.process(sample_payload())
    _assert_log_safe(
        caplog,
        "secret-token-should-not-leak",
        "semanticText",
        "원문",
    )


@pytest.mark.asyncio
async def test_source_load_retryable_retries_later_without_fail(
    caplog: pytest.LogCaptureFixture,
) -> None:
    worker, backend, input_port, generation, evidence, _ = _worker()
    dirty = "SOURCE_LOAD_FAILED secret=TOP-SECRET-LOAD TEXT=현관에서 지하철"
    input_port.load_error = ReportInputError(dirty, retryable=True)
    with caplog.at_level(logging.DEBUG):
        result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert result.error_code == WorkerErrorCode.SOURCE_LOAD_FAILED.value
    assert commit_decision_for(result.outcome) == CommitDecision.DO_NOT_COMMIT
    assert backend.fail_calls == []
    assert input_port.normalize_calls == 0
    assert generation.calls == 0
    assert evidence.calls == 0
    _assert_log_safe(caplog, "TOP-SECRET-LOAD", "현관에서 지하철", "TEXT=", dirty)


@pytest.mark.asyncio
async def test_source_load_nonretryable_fails_terminal() -> None:
    worker, backend, input_port, generation, evidence, _ = _worker()
    input_port.load_error = ReportInputError("SOURCE_LOAD_FAILED", retryable=False)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert backend.fail_calls
    assert backend.fail_calls[0].error_code == "SOURCE_LOAD_FAILED"
    assert generation.calls == 0
    assert evidence.calls == 0
    assert backend.complete_calls == []


@pytest.mark.asyncio
async def test_source_load_unknown_exception_fail_closes_without_logging_raw(
    caplog: pytest.LogCaptureFixture,
) -> None:
    worker, backend, input_port, generation, evidence, _ = _worker()
    input_port.load_error = RuntimeError(
        "timeout secret=LOAD-SECRET semanticText=원문STT"
    )
    with caplog.at_level(logging.DEBUG):
        result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert backend.fail_calls == []
    assert generation.calls == 0
    assert evidence.calls == 0
    _assert_log_safe(caplog, "LOAD-SECRET", "원문STT", "semanticText", "timeout")


@pytest.mark.asyncio
async def test_normalize_permanent_error_fails_without_complete(
    caplog: pytest.LogCaptureFixture,
) -> None:
    worker, backend, input_port, generation, evidence, _ = _worker()
    input_port.normalize_error = ReportInputError(
        "NORMALIZATION_FAILED",
        retryable=False,
    )
    with caplog.at_level(logging.DEBUG):
        result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert backend.complete_calls == []
    assert backend.fail_calls
    assert generation.calls == 0
    assert evidence.calls == 0
    assert "NORMALIZATION_FAILED" in caplog.text


@pytest.mark.asyncio
async def test_normalize_exception_with_secret_not_logged(
    caplog: pytest.LogCaptureFixture,
) -> None:
    worker, backend, input_port, generation, evidence, _ = _worker()
    input_port.normalize_error = RuntimeError(
        "invalid value secret=NORM-SECRET STT=출퇴근소음원문"
    )
    with caplog.at_level(logging.DEBUG):
        result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert backend.fail_calls == []
    assert generation.calls == 0
    _assert_log_safe(caplog, "NORM-SECRET", "출퇴근소음원문", "invalid value")


@pytest.mark.asyncio
async def test_unexpected_exception_fail_closes_without_traceback(
    caplog: pytest.LogCaptureFixture,
) -> None:
    class BoomGeneration(FakeGenerationService):
        async def generate_report(self, normalized_input):  # type: ignore[no-untyped-def]
            self.calls += 1
            raise RuntimeError("provider body=PROVIDER-SECRET claimText=유출텍스트")

    generation = BoomGeneration(results=[])
    with caplog.at_level(logging.DEBUG):
        worker, backend, *_ = _worker(generation=generation)
        result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert backend.fail_calls == []
    assert backend.complete_calls == []
    assert commit_decision_for(result.outcome) == CommitDecision.DO_NOT_COMMIT
    _assert_log_safe(
        caplog,
        "PROVIDER-SECRET",
        "유출텍스트",
        "claimText",
        "provider body",
    )
    assert "report worker unexpected failure" in caplog.text
    assert f"reportId={backend.report_id}" in caplog.text or "reportId=48" in caplog.text


@pytest.mark.asyncio
async def test_acquire_retryable_true_retries_later_without_side_effects() -> None:
    backend = FakeReportBackend(
        acquire_error=ReportBackendError(
            "BACKEND_TRANSIENT", "acquire timeout", retryable=True
        )
    )
    worker, backend, input_port, generation, evidence, _ = _worker(backend=backend)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert commit_decision_for(result.outcome) == CommitDecision.DO_NOT_COMMIT
    assert input_port.load_calls == 0
    assert generation.calls == 0
    assert evidence.calls == 0
    assert backend.progress_calls == []
    assert backend.complete_calls == []
    assert backend.fail_calls == []


@pytest.mark.asyncio
async def test_acquire_retryable_false_is_backend_rejected_policy_pending() -> None:
    backend = FakeReportBackend(
        acquire_error=ReportBackendError(
            "BACKEND_AUTH_ERROR", "unauthorized", retryable=False
        )
    )
    worker, backend, input_port, generation, evidence, _ = _worker(backend=backend)
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.BACKEND_REJECTED
    assert commit_decision_for(result.outcome) == CommitDecision.POLICY_PENDING
    assert result.error_code == "BACKEND_AUTH_ERROR"
    assert input_port.load_calls == 0
    assert generation.calls == 0
    assert evidence.calls == 0
    assert backend.progress_calls == []
    assert backend.complete_calls == []
    assert backend.fail_calls == []


@pytest.mark.asyncio
async def test_dirty_backend_and_input_codes_are_normalized(
    caplog: pytest.LogCaptureFixture,
) -> None:
    dirty_backend = "401 body=secret-token-XYZ BackendResponse={leak:1}"
    backend = FakeReportBackend(
        acquire_error=ReportBackendError(
            dirty_backend, "should-not-appear", retryable=False
        )
    )
    with caplog.at_level(logging.DEBUG):
        worker, backend, input_port, generation, evidence, _ = _worker(backend=backend)
        result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.BACKEND_REJECTED
    assert result.error_code == WorkerErrorCode.BACKEND_CONTRACT_ERROR.value
    assert commit_decision_for(result.outcome) == CommitDecision.POLICY_PENDING
    assert input_port.load_calls == 0
    _assert_log_safe(caplog, dirty_backend, "secret-token-XYZ", "should-not-appear")

    caplog.clear()
    dirty_progress = "500 body=progress-secret"
    backend = FakeReportBackend(
        progress_error=ReportBackendError(
            dirty_progress, "progress-body", retryable=False
        )
    )
    with caplog.at_level(logging.DEBUG):
        worker, backend, input_port, generation, evidence, _ = _worker(backend=backend)
        result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.BACKEND_REJECTED
    assert result.error_code == WorkerErrorCode.BACKEND_CONTRACT_ERROR.value
    assert backend.fail_calls == []
    assert generation.calls == 0
    _assert_log_safe(caplog, dirty_progress, "progress-secret", "progress-body")

    caplog.clear()
    dirty_source = "db timeout secret=SRC-SECRET TEXT=원문메모"
    worker, backend, input_port, generation, evidence, _ = _worker()
    input_port.load_error = ReportInputError(dirty_source, retryable=False)
    with caplog.at_level(logging.DEBUG):
        result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert result.error_code == WorkerErrorCode.SOURCE_LOAD_FAILED.value
    assert backend.fail_calls[0].error_code == "SOURCE_LOAD_FAILED"
    _assert_log_safe(caplog, dirty_source, "SRC-SECRET", "원문메모")

    caplog.clear()
    dirty_norm = "schema boom secret=NORM-CODE STT=소음원문"
    worker, backend, input_port, generation, evidence, _ = _worker()
    input_port.normalize_error = ReportInputError(dirty_norm, retryable=False)
    with caplog.at_level(logging.DEBUG):
        result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert result.error_code == WorkerErrorCode.NORMALIZATION_FAILED.value
    assert backend.fail_calls[0].error_code == "NORMALIZATION_FAILED"
    _assert_log_safe(caplog, dirty_norm, "NORM-CODE", "소음원문")


def test_safe_error_code_helpers_allow_list_only() -> None:
    assert (
        _safe_backend_error_code("BACKEND_AUTH_ERROR")
        == WorkerErrorCode.BACKEND_AUTH_ERROR
    )
    assert (
        _safe_backend_error_code("401 body=secret")
        == WorkerErrorCode.BACKEND_CONTRACT_ERROR
    )
    assert (
        _safe_source_error_code("SOURCE_LOAD_FAILED")
        == WorkerErrorCode.SOURCE_LOAD_FAILED
    )
    assert (
        _safe_source_error_code("TEXT=원문")
        == WorkerErrorCode.SOURCE_LOAD_FAILED
    )
    assert (
        _safe_normalization_error_code("NORMALIZATION_FAILED")
        == WorkerErrorCode.NORMALIZATION_FAILED
    )
    assert (
        _safe_normalization_error_code("invalid=원문")
        == WorkerErrorCode.NORMALIZATION_FAILED
    )


@pytest.mark.asyncio
async def test_async_load_source_is_awaited_once() -> None:
    worker, backend, input_port, generation, evidence, _ = _worker()
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.COMPLETED
    assert input_port.load_calls == 1
    assert input_port.normalize_calls == 1
    assert generation.calls == 1
    assert evidence.calls == 1
