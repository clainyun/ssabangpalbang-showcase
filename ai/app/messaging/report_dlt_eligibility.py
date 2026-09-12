"""DLT allowlist for INF-007 Phase 2 (fail-closed by default)."""

from __future__ import annotations

from app.schemas.report_worker import WorkerErrorCode, WorkerOutcome
from app.services.report_backend_port import normalize_backend_conflict_code

# Only these permanent outcomes/codes may DLT-publish and then commit.
_DLT_ALLOWED_OUTCOMES = frozenset(
    {
        WorkerOutcome.INVALID_EVENT,
        WorkerOutcome.CONTRACT_CONFLICT,
    }
)

_DLT_ALLOWED_CONFLICT_CODES = frozenset(
    {
        "COMPLETE_PAYLOAD_CONFLICT",
        "FAIL_PAYLOAD_CONFLICT",
    }
)


def should_publish_to_dlt(
    outcome: WorkerOutcome,
    error_code: str | None,
    conflict_code: str | None,
) -> bool:
    """Return True only for explicitly allowlisted permanent rejections.

    BACKEND_REJECTED alone is never enough. Auth errors, stale tokens, generic
    contract errors, and unknown codes are fail-closed (no DLT / no commit).
    """

    if outcome in _DLT_ALLOWED_OUTCOMES:
        return True
    if (
        outcome == WorkerOutcome.BACKEND_REJECTED
        and conflict_code in _DLT_ALLOWED_CONFLICT_CODES
    ):
        return True
    del error_code  # used for documentation / future extension; default deny
    return False


def dlt_safe_error_code(
    *,
    outcome: WorkerOutcome,
    error_code: str | None,
    conflict_code: str | None,
) -> str | None:
    """Choose a safe code for DLT/health without leaking response bodies."""

    safe_conflict = normalize_backend_conflict_code(conflict_code)
    if safe_conflict is not None:
        return safe_conflict
    if outcome == WorkerOutcome.INVALID_EVENT:
        return WorkerErrorCode.INVALID_EVENT.value
    if outcome == WorkerOutcome.CONTRACT_CONFLICT:
        return WorkerErrorCode.CONTRACT_CONFLICT.value
    if error_code:
        return error_code
    return outcome.value
