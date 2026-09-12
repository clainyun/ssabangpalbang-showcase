"""AI-007 Gate 3A report Worker orchestration (no HTTP/Kafka runtime)."""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any, Protocol

from app.exceptions.report_evidence import ReportEvidenceLinkError
from app.exceptions.report_generation import ReportGenerationError
from app.exceptions.report_worker import ReportInputError
from app.schemas.report_evidence import EvidenceLinkRequest, EvidenceLinkResult
from app.schemas.report_generation import ReportGenerationResult
from app.schemas.report_input import NormalizedReportInput
from app.schemas.report_request import ReportRequestedPayload
from app.schemas.report_worker import (
    WORKER_ERROR_MESSAGES,
    AcquireStatus,
    WorkerErrorCode,
    WorkerOutcome,
    WorkerStage,
)
from app.services.report_backend_port import (
    AcquireRequest,
    AcquireResult,
    CompleteCommand,
    FailCommand,
    ProgressUpdate,
    ReportBackendError,
    ReportBackendPort,
    normalize_backend_conflict_code,
)
from app.services.report_generation import (
    build_insufficient_result,
    compute_metrics,
    ordered_categories,
)

logger = logging.getLogger(__name__)

MAX_PROVIDER_ATTEMPTS = 3
DEFAULT_PROVIDER_RETRY_DELAY_SECONDS = 0.0

# INVALID_REFERENCE/SCHEMA/PYDANTIC 도 재시도 후보다: 한 응답 안에서 참조 키가
# 어긋나는(예: opinionCandidates 와 commonOpinionSummaries 의 label 불일치) 일시적
# LLM 출력 불량이 대부분이라, 다시 물으면 통과할 수 있다. 단 같은 코드라도 입력이
# 원인인 실패는 재시도해도 결과가 같으므로, 발생 지점이 붙인 error.retryable 과의
# AND 로만 재시도한다(에비던스 단계 _run_evidence 와 같은 규칙).
_RETRYABLE_GENERATION_CODES = frozenset(
    {
        WorkerErrorCode.PROVIDER_FAILED,
        WorkerErrorCode.INVALID_REFERENCE,
        WorkerErrorCode.SCHEMA_VALIDATION_FAILED,
        WorkerErrorCode.PYDANTIC_VALIDATION_FAILED,
    }
)
_RETRYABLE_EVIDENCE_CODES = frozenset(
    {WorkerErrorCode.MISSING_EVIDENCE, WorkerErrorCode.PROVIDER_FAILED}
)
_BACKEND_ERROR_CODES = frozenset(
    {
        WorkerErrorCode.BACKEND_TRANSIENT,
        WorkerErrorCode.BACKEND_CONTRACT_ERROR,
        WorkerErrorCode.BACKEND_AUTH_ERROR,
    }
)
_GENERATION_ERROR_CODES = frozenset(
    {
        WorkerErrorCode.PROVIDER_FAILED,
        WorkerErrorCode.INPUT_TOO_LARGE,
        WorkerErrorCode.INVALID_REFERENCE,
        WorkerErrorCode.SCHEMA_VALIDATION_FAILED,
        WorkerErrorCode.PYDANTIC_VALIDATION_FAILED,
    }
)
_EVIDENCE_ERROR_CODES = frozenset(
    {
        WorkerErrorCode.MISSING_EVIDENCE,
        WorkerErrorCode.PROVIDER_FAILED,
        WorkerErrorCode.INPUT_TOO_LARGE,
        WorkerErrorCode.INVALID_REFERENCE,
        WorkerErrorCode.SCHEMA_VALIDATION_FAILED,
        WorkerErrorCode.PYDANTIC_VALIDATION_FAILED,
    }
)


class ReportInputPort(Protocol):
    """AI-004 load/normalize boundary without modifying AI-004 modules."""

    async def load_source(self, report_id: int) -> Any:
        """Load authoritative snapshot (async for Gate 3B-A HTTP)."""

    def normalize(self, source: Any) -> NormalizedReportInput:
        """Run AI-004 normalization for the loaded source."""


class ReportGenerationPort(Protocol):
    async def generate_report(
        self, normalized_input: NormalizedReportInput
    ) -> ReportGenerationResult:
        """AI-005 generation entrypoint."""


class ReportEvidencePort(Protocol):
    async def link_evidence(
        self, request: EvidenceLinkRequest
    ) -> EvidenceLinkResult:
        """AI-006 evidence linking entrypoint."""


Sleeper = Callable[[float], Awaitable[None]]


@dataclass
class StageTracker:
    """Tracks attempted vs last-successfully-persisted progress stages."""

    attempted: WorkerStage | None = None
    last_completed: WorkerStage | None = None
    stages: list[WorkerStage] = field(default_factory=list)


@dataclass
class WorkerResult:
    outcome: WorkerOutcome
    report_id: int | None = None
    error_code: str | None = None
    # Allowlisted Backend 409 detail only (never HTTP body text).
    conflict_code: str | None = None
    failed_stage: WorkerStage | None = None
    attempted_stage: WorkerStage | None = None
    last_completed_stage: WorkerStage | None = None
    processing_attempt: int | None = None
    stages: list[WorkerStage] = field(default_factory=list)


async def _noop_sleeper(_seconds: float) -> None:
    return None


def has_semantic_claims(result: ReportGenerationResult) -> bool:
    """Whether AI-006 would build at least one claimSpec target.

    Mirrors AI-006 claim targets without importing private helpers:
    topPositive/topCaution/common/conflict + dataSufficient participantOpinions.
    title/summary/category.summary are not claims.
    """

    if result.topPositiveFeatures:
        return True
    if result.topCautionFeatures:
        return True
    if result.commonOpinions:
        return True
    if result.conflictingOpinions:
        return True
    for category in result.categories:
        if category.dataSufficient and category.participantOpinions:
            return True
    return False


def is_deterministic_data_insufficient(
    normalized: NormalizedReportInput,
    result: ReportGenerationResult,
) -> bool:
    """Accept only the canonical insufficient result for the current input."""

    expected = build_insufficient_result(
        metrics=compute_metrics(normalized),
        categories=ordered_categories(normalized),
    )
    return result.model_dump_json() == expected.model_dump_json()


def _fixed_message(code: WorkerErrorCode) -> str:
    return WORKER_ERROR_MESSAGES[code]


def _safe_info(message: str, **fields: object) -> None:
    """Log only explicitly allow-listed fields. Never pass exception objects."""

    parts = [message]
    for key, value in fields.items():
        parts.append(f"{key}={value}")
    logger.info("%s", " ".join(parts))


# INVALID_REFERENCE 하나에 검증 지점이 10곳이라 errorCode 만으로는 원인을 못 좁힌다.
# 그렇다고 예외 메시지를 그대로 찍으면 provider 오류 문구에 실린 원문·토큰이 새어 나간다
# (test_worker_logs_omit_secret_and_raw_text 가 이걸 막는다).
# 그래서 코드베이스에 하드코딩된 고정 문구만 허용목록으로 통과시키고, 그 외에는 버린다.
_KNOWN_FAILURE_DETAILS = frozenset(
    {
        # app/services/report_generation.py
        "LLM draft failed JSON Schema validation",
        "LLM draft references out-of-range sourceIndex",
        "LLM draft references non-opinion usable source",
        "LLM draft participantRef does not match sourceIndex",
        "LLM draft category does not match sourceIndex",
        "LLM draft references unknown category",
        "LLM category summary references unknown category",
        "LLM category summary contains duplicate category",
        "LLM common summary references unknown candidate key",
        "LLM common summary contains duplicate key",
        "LLM conflict summary references unknown candidate key",
        "LLM conflict summary contains duplicate key",
        # app/exceptions/report_evidence.py
        "required semantic claim evidence is missing",
        "usable source count or prompt size exceeds configured limit",
        "LLM evidence draft contains an invalid reference",
        "LLM evidence draft failed JSON Schema validation",
        "Pydantic validation failed for LLM evidence draft",
        "claimKey collision detected for distinct claims",
        # 양쪽 공통
        "LLM provider call failed",
    }
)


def _safe_failure_detail(raw: object) -> str:
    """Return the validation message only when it is a known fixed string.

    Provider errors carry upstream text that may include record content or
    credentials, so anything outside the allow-list is reported as OTHER.
    """

    if isinstance(raw, str) and raw in _KNOWN_FAILURE_DETAILS:
        return raw.replace(" ", "_")
    return "OTHER"


def _parse_worker_error_code(raw: object) -> WorkerErrorCode | None:
    if not isinstance(raw, str):
        return None
    try:
        return WorkerErrorCode(raw)
    except ValueError:
        return None


def _safe_backend_error_code(raw: object) -> WorkerErrorCode:
    """Normalize Backend Port codes; never trust arbitrary Adapter strings."""

    code = _parse_worker_error_code(raw)
    if code in _BACKEND_ERROR_CODES:
        return code
    return WorkerErrorCode.BACKEND_CONTRACT_ERROR


def _safe_source_error_code(raw: object) -> WorkerErrorCode:
    code = _parse_worker_error_code(raw)
    if code in {
        WorkerErrorCode.SOURCE_LOAD_FAILED,
        WorkerErrorCode.BACKEND_TRANSIENT,
        WorkerErrorCode.BACKEND_AUTH_ERROR,
        WorkerErrorCode.BACKEND_CONTRACT_ERROR,
    }:
        return code
    return WorkerErrorCode.SOURCE_LOAD_FAILED


def _safe_normalization_error_code(raw: object) -> WorkerErrorCode:
    code = _parse_worker_error_code(raw)
    if code == WorkerErrorCode.NORMALIZATION_FAILED:
        return code
    return WorkerErrorCode.NORMALIZATION_FAILED


def _safe_generation_error_code(raw: object) -> WorkerErrorCode:
    code = _parse_worker_error_code(raw)
    if code in _GENERATION_ERROR_CODES:
        return code
    return WorkerErrorCode.UNKNOWN_FAILURE


def _safe_evidence_error_code(raw: object) -> WorkerErrorCode:
    code = _parse_worker_error_code(raw)
    if code in _EVIDENCE_ERROR_CODES:
        return code
    return WorkerErrorCode.UNKNOWN_FAILURE


class ReportWorker:
    """Orchestrates acquire → AI-004/005/006 → complete/fail for one event."""

    def __init__(
        self,
        *,
        backend: ReportBackendPort,
        input_port: ReportInputPort,
        generation: ReportGenerationPort,
        evidence: ReportEvidencePort,
        sleeper: Sleeper | None = None,
        provider_retry_delay_seconds: float = DEFAULT_PROVIDER_RETRY_DELAY_SECONDS,
        max_provider_attempts: int = MAX_PROVIDER_ATTEMPTS,
    ) -> None:
        self._backend = backend
        self._input_port = input_port
        self._generation = generation
        self._evidence = evidence
        self._sleeper = sleeper or _noop_sleeper
        self._provider_retry_delay_seconds = provider_retry_delay_seconds
        self._max_provider_attempts = max_provider_attempts

    async def process(self, payload: ReportRequestedPayload) -> WorkerResult:
        try:
            acquire = await self._backend.acquire(
                AcquireRequest(
                    study_id=payload.studyId,
                    session_id=payload.sessionId,
                    apartment_id=payload.apartmentId,
                    occurred_at=payload.occurredAt,
                )
            )
        except ReportBackendError as error:
            safe_code = _safe_backend_error_code(error.code)
            conflict = normalize_backend_conflict_code(error.conflict_code)
            if error.retryable:
                _safe_info(
                    "report acquire failed",
                    reportId="-",
                    stage="ACQUIRE",
                    errorCode=safe_code.value,
                    conflictCode=conflict or "-",
                )
                return WorkerResult(
                    outcome=WorkerOutcome.RETRY_LATER,
                    error_code=safe_code.value,
                    conflict_code=conflict,
                )
            _safe_info(
                "report acquire rejected",
                reportId="-",
                stage="ACQUIRE",
                errorCode=safe_code.value,
                conflictCode=conflict or "-",
            )
            return WorkerResult(
                outcome=WorkerOutcome.BACKEND_REJECTED,
                error_code=safe_code.value,
                conflict_code=conflict,
            )

        if acquire.status == AcquireStatus.ALREADY_COMPLETED:
            return WorkerResult(
                outcome=WorkerOutcome.ALREADY_COMPLETED,
                report_id=acquire.report_id,
            )
        if acquire.status == AcquireStatus.ALREADY_FAILED:
            # Terminal failure already persisted. Kafka offset may be recommitted.
            # Original Kafka event path does not auto-restart FAILED reports.
            return WorkerResult(
                outcome=WorkerOutcome.TERMINAL_FAILED,
                report_id=acquire.report_id,
                error_code=WorkerErrorCode.UNKNOWN_FAILURE.value,
            )
        if acquire.status == AcquireStatus.ALREADY_PROCESSING:
            return WorkerResult(
                outcome=WorkerOutcome.ALREADY_PROCESSING,
                report_id=acquire.report_id,
            )
        if acquire.status == AcquireStatus.CONTRACT_CONFLICT:
            return WorkerResult(
                outcome=WorkerOutcome.CONTRACT_CONFLICT,
                report_id=acquire.report_id,
                error_code=WorkerErrorCode.CONTRACT_CONFLICT.value,
            )
        if acquire.status != AcquireStatus.ACQUIRED:
            return WorkerResult(
                outcome=WorkerOutcome.BACKEND_REJECTED,
                error_code=WorkerErrorCode.BACKEND_CONTRACT_ERROR.value,
                report_id=acquire.report_id,
            )

        validation_error = _validate_acquired(acquire)
        if validation_error is not None:
            return WorkerResult(
                outcome=WorkerOutcome.BACKEND_REJECTED,
                error_code=validation_error,
                report_id=acquire.report_id,
            )

        assert acquire.report_id is not None
        assert acquire.processing_token is not None
        assert acquire.processing_attempt is not None

        report_id = acquire.report_id
        token = acquire.processing_token
        processing_attempt = acquire.processing_attempt
        tracker = StageTracker()

        try:
            await self._progress(
                report_id,
                token,
                processing_attempt,
                WorkerStage.RECORD_COLLECTION,
                tracker,
            )
            try:
                source = await self._input_port.load_source(report_id)
            except ReportInputError as error:
                safe_code = _safe_source_error_code(error.code)
                _safe_info(
                    "report source load failed",
                    reportId=report_id,
                    stage=WorkerStage.RECORD_COLLECTION.value,
                    errorCode=safe_code.value,
                    processingAttempt=processing_attempt,
                    attemptedStage=tracker.attempted.value if tracker.attempted else "-",
                    lastCompletedStage=(
                        tracker.last_completed.value
                        if tracker.last_completed
                        else "-"
                    ),
                )
                # Backend auth/contract issues are not permanent report data failures.
                if safe_code == WorkerErrorCode.BACKEND_TRANSIENT:
                    return self._result(
                        WorkerOutcome.RETRY_LATER,
                        report_id=report_id,
                        error_code=safe_code.value,
                        failed_stage=WorkerStage.RECORD_COLLECTION,
                        processing_attempt=processing_attempt,
                        tracker=tracker,
                    )
                if safe_code in {
                    WorkerErrorCode.BACKEND_AUTH_ERROR,
                    WorkerErrorCode.BACKEND_CONTRACT_ERROR,
                }:
                    return self._result(
                        WorkerOutcome.BACKEND_REJECTED,
                        report_id=report_id,
                        error_code=safe_code.value,
                        failed_stage=WorkerStage.RECORD_COLLECTION,
                        processing_attempt=processing_attempt,
                        tracker=tracker,
                    )
                if safe_code == WorkerErrorCode.SOURCE_LOAD_FAILED and error.retryable:
                    return self._result(
                        WorkerOutcome.RETRY_LATER,
                        report_id=report_id,
                        error_code=safe_code.value,
                        failed_stage=WorkerStage.RECORD_COLLECTION,
                        processing_attempt=processing_attempt,
                        tracker=tracker,
                    )
                if safe_code == WorkerErrorCode.SOURCE_LOAD_FAILED:
                    return await self._fail(
                        report_id=report_id,
                        token=token,
                        processing_attempt=processing_attempt,
                        failed_stage=WorkerStage.RECORD_COLLECTION,
                        error_code=safe_code,
                        retryable=False,
                        tracker=tracker,
                    )
                # Unknown mapped codes: fail-closed without persisting FAILED.
                if error.retryable:
                    return self._result(
                        WorkerOutcome.RETRY_LATER,
                        report_id=report_id,
                        error_code=safe_code.value,
                        failed_stage=WorkerStage.RECORD_COLLECTION,
                        processing_attempt=processing_attempt,
                        tracker=tracker,
                    )
                return self._result(
                    WorkerOutcome.BACKEND_REJECTED,
                    report_id=report_id,
                    error_code=safe_code.value,
                    failed_stage=WorkerStage.RECORD_COLLECTION,
                    processing_attempt=processing_attempt,
                    tracker=tracker,
                )
            except Exception:
                _safe_info(
                    "report source load failed",
                    reportId=report_id,
                    stage=WorkerStage.RECORD_COLLECTION.value,
                    errorCode=WorkerErrorCode.SOURCE_LOAD_FAILED.value,
                    processingAttempt=processing_attempt,
                    attemptedStage=tracker.attempted.value if tracker.attempted else "-",
                    lastCompletedStage=(
                        tracker.last_completed.value
                        if tracker.last_completed
                        else "-"
                    ),
                )
                return self._result(
                    WorkerOutcome.RETRY_LATER,
                    report_id=report_id,
                    error_code=WorkerErrorCode.SOURCE_LOAD_FAILED.value,
                    failed_stage=WorkerStage.RECORD_COLLECTION,
                    processing_attempt=processing_attempt,
                    tracker=tracker,
                )

            await self._progress(
                report_id,
                token,
                processing_attempt,
                WorkerStage.STT_VALIDATION,
                tracker,
            )
            # STT DONE / exclusion rules remain inside AI-004 normalize.

            await self._progress(
                report_id,
                token,
                processing_attempt,
                WorkerStage.NORMALIZATION,
                tracker,
            )
            try:
                normalized = self._input_port.normalize(source)
            except ReportInputError as error:
                safe_code = _safe_normalization_error_code(error.code)
                _safe_info(
                    "report normalization failed",
                    reportId=report_id,
                    stage=WorkerStage.NORMALIZATION.value,
                    errorCode=safe_code.value,
                    processingAttempt=processing_attempt,
                )
                if error.retryable:
                    return self._result(
                        WorkerOutcome.RETRY_LATER,
                        report_id=report_id,
                        error_code=safe_code.value,
                        failed_stage=WorkerStage.NORMALIZATION,
                        processing_attempt=processing_attempt,
                        tracker=tracker,
                    )
                return await self._fail(
                    report_id=report_id,
                    token=token,
                    processing_attempt=processing_attempt,
                    failed_stage=WorkerStage.NORMALIZATION,
                    error_code=safe_code,
                    retryable=False,
                    tracker=tracker,
                )
            except Exception:
                _safe_info(
                    "report normalization failed",
                    reportId=report_id,
                    stage=WorkerStage.NORMALIZATION.value,
                    errorCode=WorkerErrorCode.NORMALIZATION_FAILED.value,
                    processingAttempt=processing_attempt,
                )
                return self._result(
                    WorkerOutcome.RETRY_LATER,
                    report_id=report_id,
                    error_code=WorkerErrorCode.NORMALIZATION_FAILED.value,
                    failed_stage=WorkerStage.NORMALIZATION,
                    processing_attempt=processing_attempt,
                    tracker=tracker,
                )

            await self._progress(
                report_id,
                token,
                processing_attempt,
                WorkerStage.REPORT_GENERATION,
                tracker,
            )
            generation_result, generation_failure = await self._run_generation(
                normalized
            )
            if generation_failure is not None:
                code, retryable = generation_failure
                return await self._fail(
                    report_id=report_id,
                    token=token,
                    processing_attempt=processing_attempt,
                    failed_stage=WorkerStage.REPORT_GENERATION,
                    error_code=code,
                    retryable=retryable,
                    tracker=tracker,
                )
            assert generation_result is not None

            if is_deterministic_data_insufficient(normalized, generation_result):
                evidence_result = EvidenceLinkResult(claims=[])
            elif has_semantic_claims(generation_result):
                await self._progress(
                    report_id,
                    token,
                    processing_attempt,
                    WorkerStage.EVIDENCE_MAPPING,
                    tracker,
                )
                evidence_result, evidence_failure = await self._run_evidence(
                    normalized,
                    generation_result,
                )
                if evidence_failure is not None:
                    code, retryable = evidence_failure
                    return await self._fail(
                        report_id=report_id,
                        token=token,
                        processing_attempt=processing_attempt,
                        failed_stage=WorkerStage.EVIDENCE_MAPPING,
                        error_code=code,
                        retryable=retryable,
                        tracker=tracker,
                    )
                assert evidence_result is not None
            else:
                return await self._fail(
                    report_id=report_id,
                    token=token,
                    processing_attempt=processing_attempt,
                    failed_stage=WorkerStage.REPORT_GENERATION,
                    error_code=WorkerErrorCode.GENERATION_RESULT_INVALID,
                    retryable=False,
                    tracker=tracker,
                )

            await self._progress(
                report_id,
                token,
                processing_attempt,
                WorkerStage.RESULT_SAVING,
                tracker,
            )
            try:
                await self._backend.complete(
                    CompleteCommand(
                        report_id=report_id,
                        processing_token=token,
                        processing_attempt=processing_attempt,
                        generation_result=generation_result,
                        evidence_result=evidence_result,
                    )
                )
            except ReportBackendError as error:
                safe_code = _safe_backend_error_code(error.code)
                conflict = normalize_backend_conflict_code(error.conflict_code)
                _safe_info(
                    "report complete failed",
                    reportId=report_id,
                    stage=WorkerStage.RESULT_SAVING.value,
                    errorCode=safe_code.value,
                    conflictCode=conflict or "-",
                    processingAttempt=processing_attempt,
                    attemptedStage=tracker.attempted.value if tracker.attempted else "-",
                    lastCompletedStage=(
                        tracker.last_completed.value
                        if tracker.last_completed
                        else "-"
                    ),
                )
                # Transient complete failures may retry. Permanent 409 conflicts
                # must not loop forever as RETRY_LATER (no fail overwrite).
                if error.retryable:
                    return self._result(
                        WorkerOutcome.RETRY_LATER,
                        report_id=report_id,
                        error_code=safe_code.value,
                        conflict_code=conflict,
                        failed_stage=WorkerStage.RESULT_SAVING,
                        processing_attempt=processing_attempt,
                        tracker=tracker,
                    )
                return self._result(
                    WorkerOutcome.BACKEND_REJECTED,
                    report_id=report_id,
                    error_code=safe_code.value,
                    conflict_code=conflict,
                    failed_stage=WorkerStage.RESULT_SAVING,
                    processing_attempt=processing_attempt,
                    tracker=tracker,
                )

            return self._result(
                WorkerOutcome.COMPLETED,
                report_id=report_id,
                processing_attempt=processing_attempt,
                tracker=tracker,
            )
        except ReportBackendError as error:
            # Progress/backend errors after acquire follow the Adapter retry policy.
            # Permanent auth/contract errors must not hot-loop as RETRY_LATER.
            safe_code = _safe_backend_error_code(error.code)
            conflict = normalize_backend_conflict_code(error.conflict_code)
            _safe_info(
                "report backend call failed",
                reportId=report_id,
                stage=(
                    tracker.attempted.value
                    if tracker.attempted
                    else WorkerStage.RECORD_COLLECTION.value
                ),
                errorCode=safe_code.value,
                conflictCode=conflict or "-",
                processingAttempt=processing_attempt,
                attemptedStage=tracker.attempted.value if tracker.attempted else "-",
                lastCompletedStage=(
                    tracker.last_completed.value if tracker.last_completed else "-"
                ),
            )
            return self._result(
                (
                    WorkerOutcome.RETRY_LATER
                    if error.retryable
                    else WorkerOutcome.BACKEND_REJECTED
                ),
                report_id=report_id,
                error_code=safe_code.value,
                conflict_code=conflict,
                failed_stage=tracker.attempted,
                processing_attempt=processing_attempt,
                tracker=tracker,
            )
        except Exception:
            _safe_info(
                "report worker unexpected failure",
                reportId=report_id,
                stage=(
                    tracker.attempted.value
                    if tracker.attempted
                    else WorkerStage.RECORD_COLLECTION.value
                ),
                errorCode=WorkerErrorCode.UNKNOWN_FAILURE.value,
                processingAttempt=processing_attempt,
                attemptedStage=tracker.attempted.value if tracker.attempted else "-",
                lastCompletedStage=(
                    tracker.last_completed.value if tracker.last_completed else "-"
                ),
            )
            return self._result(
                WorkerOutcome.RETRY_LATER,
                report_id=report_id,
                error_code=WorkerErrorCode.UNKNOWN_FAILURE.value,
                failed_stage=tracker.attempted,
                processing_attempt=processing_attempt,
                tracker=tracker,
            )

    def _result(
        self,
        outcome: WorkerOutcome,
        *,
        report_id: int | None = None,
        error_code: str | None = None,
        conflict_code: str | None = None,
        failed_stage: WorkerStage | None = None,
        processing_attempt: int | None = None,
        tracker: StageTracker | None = None,
    ) -> WorkerResult:
        tracker = tracker or StageTracker()
        return WorkerResult(
            outcome=outcome,
            report_id=report_id,
            error_code=error_code,
            conflict_code=normalize_backend_conflict_code(conflict_code),
            failed_stage=failed_stage,
            attempted_stage=tracker.attempted,
            last_completed_stage=tracker.last_completed,
            processing_attempt=processing_attempt,
            stages=list(tracker.stages),
        )

    async def _progress(
        self,
        report_id: int,
        token: str,
        processing_attempt: int,
        stage: WorkerStage,
        tracker: StageTracker,
    ) -> None:
        tracker.attempted = stage
        await self._backend.update_progress(
            ProgressUpdate(
                report_id=report_id,
                processing_token=token,
                processing_attempt=processing_attempt,
                stage=stage,
            )
        )
        tracker.stages.append(stage)
        tracker.last_completed = stage
        _safe_info(
            "report progress",
            reportId=report_id,
            stage=stage.value,
            processingAttempt=processing_attempt,
            attemptedStage=stage.value,
            lastCompletedStage=stage.value,
        )

    async def _run_generation(
        self,
        normalized: NormalizedReportInput,
    ) -> tuple[ReportGenerationResult | None, tuple[WorkerErrorCode, bool] | None]:
        last_code = WorkerErrorCode.PROVIDER_FAILED
        for provider_attempt in range(1, self._max_provider_attempts + 1):
            try:
                result = await self._generation.generate_report(normalized)
                return result, None
            except ReportGenerationError as error:
                last_code = _safe_generation_error_code(error.code)
                retryable = bool(error.retryable) and last_code in _RETRYABLE_GENERATION_CODES
                _safe_info(
                    "report generation attempt failed",
                    reportId=normalized.reportId,
                    stage=WorkerStage.REPORT_GENERATION.value,
                    errorCode=last_code.value,
                    providerAttempt=provider_attempt,
                    maxAttempts=self._max_provider_attempts,
                    # INVALID_REFERENCE 는 검증 지점이 10곳이라 코드만으로는 못 좁힌다.
                    failureDetail=_safe_failure_detail(
                        getattr(error, "message", None)
                    ),
                )
                if not retryable:
                    return None, (last_code, False)
                if provider_attempt >= self._max_provider_attempts:
                    return None, (last_code, True)
                await self._sleeper(self._provider_retry_delay_seconds)
        return None, (last_code, True)

    async def _run_evidence(
        self,
        normalized: NormalizedReportInput,
        generation_result: ReportGenerationResult,
    ) -> tuple[EvidenceLinkResult | None, tuple[WorkerErrorCode, bool] | None]:
        request = EvidenceLinkRequest(
            normalizedInput=normalized,
            generationResult=generation_result,
        )
        last_code = WorkerErrorCode.MISSING_EVIDENCE
        for provider_attempt in range(1, self._max_provider_attempts + 1):
            try:
                result = await self._evidence.link_evidence(request)
                return result, None
            except ReportEvidenceLinkError as error:
                last_code = _safe_evidence_error_code(error.code)
                retryable = bool(error.retryable) and last_code in _RETRYABLE_EVIDENCE_CODES
                _safe_info(
                    "report evidence attempt failed",
                    reportId=normalized.reportId,
                    stage=WorkerStage.EVIDENCE_MAPPING.value,
                    errorCode=last_code.value,
                    providerAttempt=provider_attempt,
                    maxAttempts=self._max_provider_attempts,
                    failureDetail=_safe_failure_detail(
                        getattr(error, "message", None)
                    ),
                )
                if not retryable:
                    return None, (last_code, False)
                if provider_attempt >= self._max_provider_attempts:
                    return None, (last_code, True)
                await self._sleeper(self._provider_retry_delay_seconds)
        return None, (last_code, True)

    async def _fail(
        self,
        *,
        report_id: int,
        token: str,
        processing_attempt: int,
        failed_stage: WorkerStage,
        error_code: WorkerErrorCode,
        retryable: bool,
        tracker: StageTracker,
    ) -> WorkerResult:
        try:
            await self._backend.fail(
                FailCommand(
                    report_id=report_id,
                    processing_token=token,
                    processing_attempt=processing_attempt,
                    failed_stage=failed_stage,
                    error_code=error_code.value,
                    message=_fixed_message(error_code),
                    retryable=retryable,
                )
            )
        except ReportBackendError as error:
            safe_code = _safe_backend_error_code(error.code)
            conflict = normalize_backend_conflict_code(error.conflict_code)
            _safe_info(
                "report fail persist failed",
                reportId=report_id,
                stage=failed_stage.value,
                errorCode=safe_code.value,
                conflictCode=conflict or "-",
                processingAttempt=processing_attempt,
                attemptedStage=tracker.attempted.value if tracker.attempted else "-",
                lastCompletedStage=(
                    tracker.last_completed.value if tracker.last_completed else "-"
                ),
            )
            if error.retryable:
                return self._result(
                    WorkerOutcome.RETRY_LATER,
                    report_id=report_id,
                    error_code=error_code.value,
                    conflict_code=conflict,
                    failed_stage=failed_stage,
                    processing_attempt=processing_attempt,
                    tracker=tracker,
                )
            # Stale token / permanent fail conflict: do not hot-loop.
            return self._result(
                WorkerOutcome.BACKEND_REJECTED,
                report_id=report_id,
                error_code=safe_code.value,
                conflict_code=conflict,
                failed_stage=failed_stage,
                processing_attempt=processing_attempt,
                tracker=tracker,
            )
        return self._result(
            WorkerOutcome.TERMINAL_FAILED,
            report_id=report_id,
            error_code=error_code.value,
            failed_stage=failed_stage,
            processing_attempt=processing_attempt,
            tracker=tracker,
        )


def _validate_acquired(acquire: AcquireResult) -> str | None:
    if acquire.report_id is None:
        return WorkerErrorCode.BACKEND_CONTRACT_ERROR.value
    if not acquire.processing_token:
        return WorkerErrorCode.BACKEND_CONTRACT_ERROR.value
    if acquire.processing_attempt is None or acquire.processing_attempt < 1:
        return WorkerErrorCode.BACKEND_CONTRACT_ERROR.value
    return None
