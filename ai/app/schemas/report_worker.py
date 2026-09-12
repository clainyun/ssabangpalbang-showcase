"""Worker stage, outcome, and commit decision types for AI-007 Gate 3A."""

from __future__ import annotations

from enum import StrEnum


class WorkerStage(StrEnum):
    RECORD_COLLECTION = "RECORD_COLLECTION"
    STT_VALIDATION = "STT_VALIDATION"
    NORMALIZATION = "NORMALIZATION"
    REPORT_GENERATION = "REPORT_GENERATION"
    EVIDENCE_MAPPING = "EVIDENCE_MAPPING"
    RESULT_SAVING = "RESULT_SAVING"
    COMPLETED = "COMPLETED"


class AcquireStatus(StrEnum):
    ACQUIRED = "ACQUIRED"
    ALREADY_COMPLETED = "ALREADY_COMPLETED"
    ALREADY_PROCESSING = "ALREADY_PROCESSING"
    ALREADY_FAILED = "ALREADY_FAILED"
    CONTRACT_CONFLICT = "CONTRACT_CONFLICT"


class WorkerOutcome(StrEnum):
    COMPLETED = "COMPLETED"
    ALREADY_COMPLETED = "ALREADY_COMPLETED"
    TERMINAL_FAILED = "TERMINAL_FAILED"
    RETRY_LATER = "RETRY_LATER"
    ALREADY_PROCESSING = "ALREADY_PROCESSING"
    CONTRACT_CONFLICT = "CONTRACT_CONFLICT"
    INVALID_EVENT = "INVALID_EVENT"
    BACKEND_REJECTED = "BACKEND_REJECTED"


class CommitDecision(StrEnum):
    COMMIT = "COMMIT"
    DO_NOT_COMMIT = "DO_NOT_COMMIT"
    POLICY_PENDING = "POLICY_PENDING"


class WorkerErrorCode(StrEnum):
    INVALID_EVENT = "INVALID_EVENT"
    CONTRACT_CONFLICT = "CONTRACT_CONFLICT"
    GENERATION_RESULT_INVALID = "GENERATION_RESULT_INVALID"
    NORMALIZATION_FAILED = "NORMALIZATION_FAILED"
    SOURCE_LOAD_FAILED = "SOURCE_LOAD_FAILED"
    PROVIDER_FAILED = "PROVIDER_FAILED"
    MISSING_EVIDENCE = "MISSING_EVIDENCE"
    INPUT_TOO_LARGE = "INPUT_TOO_LARGE"
    INVALID_REFERENCE = "INVALID_REFERENCE"
    SCHEMA_VALIDATION_FAILED = "SCHEMA_VALIDATION_FAILED"
    PYDANTIC_VALIDATION_FAILED = "PYDANTIC_VALIDATION_FAILED"
    BACKEND_TRANSIENT = "BACKEND_TRANSIENT"
    BACKEND_CONTRACT_ERROR = "BACKEND_CONTRACT_ERROR"
    BACKEND_AUTH_ERROR = "BACKEND_AUTH_ERROR"
    UNKNOWN_FAILURE = "UNKNOWN_FAILURE"


# Fixed user-facing messages. Must never embed raw payload/provider text.
WORKER_ERROR_MESSAGES: dict[WorkerErrorCode, str] = {
    WorkerErrorCode.INVALID_EVENT: "리포트 요청 이벤트가 올바르지 않습니다.",
    WorkerErrorCode.CONTRACT_CONFLICT: "리포트 요청 계약이 충돌합니다.",
    WorkerErrorCode.GENERATION_RESULT_INVALID: (
        "리포트 생성 결과가 유효하지 않습니다."
    ),
    WorkerErrorCode.NORMALIZATION_FAILED: "리포트 입력 정규화에 실패했습니다.",
    WorkerErrorCode.SOURCE_LOAD_FAILED: "리포트 원본 데이터 조회에 실패했습니다.",
    WorkerErrorCode.PROVIDER_FAILED: "AI 제공자 호출에 실패했습니다.",
    WorkerErrorCode.MISSING_EVIDENCE: "리포트 근거 연결에 실패했습니다.",
    WorkerErrorCode.INPUT_TOO_LARGE: "리포트 입력 크기가 제한을 초과했습니다.",
    WorkerErrorCode.INVALID_REFERENCE: "리포트 참조가 올바르지 않습니다.",
    WorkerErrorCode.SCHEMA_VALIDATION_FAILED: (
        "AI 응답 스키마 검증에 실패했습니다."
    ),
    WorkerErrorCode.PYDANTIC_VALIDATION_FAILED: (
        "AI 응답 데이터 검증에 실패했습니다."
    ),
    WorkerErrorCode.BACKEND_TRANSIENT: "Backend 일시 오류가 발생했습니다.",
    WorkerErrorCode.BACKEND_CONTRACT_ERROR: "Backend 계약 오류가 발생했습니다.",
    WorkerErrorCode.BACKEND_AUTH_ERROR: "Backend 인증에 실패했습니다.",
    WorkerErrorCode.UNKNOWN_FAILURE: "리포트 생성 중 오류가 발생했습니다.",
}


def commit_decision_for(outcome: WorkerOutcome) -> CommitDecision:
    """Map WorkerOutcome to Gate 3A CommitDecision."""

    if outcome in {
        WorkerOutcome.COMPLETED,
        WorkerOutcome.ALREADY_COMPLETED,
        WorkerOutcome.TERMINAL_FAILED,
    }:
        return CommitDecision.COMMIT
    if outcome in {
        WorkerOutcome.RETRY_LATER,
        WorkerOutcome.ALREADY_PROCESSING,
    }:
        return CommitDecision.DO_NOT_COMMIT
    if outcome in {
        WorkerOutcome.CONTRACT_CONFLICT,
        WorkerOutcome.INVALID_EVENT,
        WorkerOutcome.BACKEND_REJECTED,
    }:
        return CommitDecision.POLICY_PENDING
    return CommitDecision.POLICY_PENDING
