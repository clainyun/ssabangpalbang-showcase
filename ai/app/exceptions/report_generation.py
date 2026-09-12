"""Exceptions for AI-005 report generation."""

from __future__ import annotations


class ReportGenerationError(Exception):
    """Technical failure propagated to AI-007 for FAILED/retry handling.

    ``retryable`` distinguishes the failure origin, mirroring
    ``ReportEvidenceLinkError``. ``True`` means the failure came from a single
    LLM response (draft that violates the reference contract, schema mismatch),
    so asking again can succeed. ``False`` means the failure is deterministic
    for this input (size limits, missing prerequisite data) and retrying the
    same request cannot change the outcome.
    """

    def __init__(self, code: str, message: str, *, retryable: bool = False) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retryable = retryable
