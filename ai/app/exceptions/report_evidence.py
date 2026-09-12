"""Exceptions for AI-006 report evidence linking."""

from __future__ import annotations


class ReportEvidenceLinkError(Exception):
    """Typed failure for AI-006 evidence linking (consumed by AI-007)."""

    def __init__(self, code: str, message: str, *, retryable: bool) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retryable = retryable


MISSING_EVIDENCE_MESSAGE = "required semantic claim evidence is missing"
PROVIDER_FAILED_MESSAGE = "LLM provider call failed"
INPUT_TOO_LARGE_MESSAGE = "usable source count or prompt size exceeds configured limit"
INVALID_REFERENCE_MESSAGE = "LLM evidence draft contains an invalid reference"
SCHEMA_VALIDATION_FAILED_MESSAGE = "LLM evidence draft failed JSON Schema validation"
PYDANTIC_VALIDATION_FAILED_MESSAGE = "Pydantic validation failed for LLM evidence draft"
CLAIM_KEY_COLLISION_MESSAGE = "claimKey collision detected for distinct claims"
