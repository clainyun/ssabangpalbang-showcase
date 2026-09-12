"""Backend capability Port for AI-007 Gate 3A (no HTTP URI/DTO)."""

from __future__ import annotations

from datetime import datetime
from typing import Protocol

from app.schemas.report_evidence import EvidenceLinkResult
from app.schemas.report_generation import ReportGenerationResult
from app.schemas.report_worker import AcquireStatus, WorkerStage


_ALLOWED_CONFLICT_CODES = frozenset(
    {
        "STALE_PROCESSING_TOKEN",
        "COMPLETE_PAYLOAD_CONFLICT",
        "FAIL_PAYLOAD_CONFLICT",
    }
)


def normalize_backend_conflict_code(raw: str | None) -> str | None:
    """Return allowlisted Backend conflict code only; never arbitrary strings."""

    if raw in _ALLOWED_CONFLICT_CODES:
        return raw
    return None


class ReportBackendError(Exception):
    """Typed Backend Port failure for Worker retry/fail decisions."""

    def __init__(
        self,
        code: str,
        message: str,
        *,
        retryable: bool,
        conflict_code: str | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retryable = retryable
        # Allowlisted 409 detail only. Never HTTP body / exception text.
        self.conflict_code = normalize_backend_conflict_code(conflict_code)


class AcquireRequest:
    __slots__ = ("study_id", "session_id", "apartment_id", "occurred_at")

    def __init__(
        self,
        *,
        study_id: int,
        session_id: int,
        apartment_id: int,
        occurred_at: datetime,
    ) -> None:
        self.study_id = study_id
        self.session_id = session_id
        self.apartment_id = apartment_id
        self.occurred_at = occurred_at


class AcquireResult:
    """Result of atomic create-or-get + claim attempt."""

    __slots__ = (
        "status",
        "report_id",
        "processing_token",
        "processing_attempt",
    )

    def __init__(
        self,
        *,
        status: AcquireStatus,
        report_id: int | None = None,
        processing_token: str | None = None,
        processing_attempt: int | None = None,
    ) -> None:
        self.status = status
        self.report_id = report_id
        self.processing_token = processing_token
        self.processing_attempt = processing_attempt


class ProgressUpdate:
    __slots__ = (
        "report_id",
        "processing_token",
        "processing_attempt",
        "stage",
    )

    def __init__(
        self,
        *,
        report_id: int,
        processing_token: str,
        processing_attempt: int,
        stage: WorkerStage,
    ) -> None:
        self.report_id = report_id
        self.processing_token = processing_token
        self.processing_attempt = processing_attempt
        self.stage = stage


class CompleteCommand:
    __slots__ = (
        "report_id",
        "processing_token",
        "processing_attempt",
        "generation_result",
        "evidence_result",
    )

    def __init__(
        self,
        *,
        report_id: int,
        processing_token: str,
        processing_attempt: int,
        generation_result: ReportGenerationResult,
        evidence_result: EvidenceLinkResult,
    ) -> None:
        self.report_id = report_id
        self.processing_token = processing_token
        self.processing_attempt = processing_attempt
        self.generation_result = generation_result
        self.evidence_result = evidence_result


class FailCommand:
    __slots__ = (
        "report_id",
        "processing_token",
        "processing_attempt",
        "failed_stage",
        "error_code",
        "message",
        "retryable",
    )

    def __init__(
        self,
        *,
        report_id: int,
        processing_token: str,
        processing_attempt: int,
        failed_stage: WorkerStage,
        error_code: str,
        message: str,
        retryable: bool,
    ) -> None:
        self.report_id = report_id
        self.processing_token = processing_token
        self.processing_attempt = processing_attempt
        self.failed_stage = failed_stage
        self.error_code = error_code
        self.message = message
        self.retryable = retryable


class ReportBackendPort(Protocol):
    """Functional Backend capabilities required by the Report Worker."""

    async def acquire(self, request: AcquireRequest) -> AcquireResult:
        """Create-or-get report and atomically claim processing rights."""

    async def update_progress(self, update: ProgressUpdate) -> None:
        """Persist logical WorkerStage under a valid processing token."""

    async def complete(self, command: CompleteCommand) -> None:
        """Atomically save result/evidence and mark DONE/COMPLETED."""

    async def fail(self, command: FailCommand) -> None:
        """Atomically persist failure details and mark FAILED."""
