"""Typed errors for AI-007 Gate 3A report Worker input boundaries."""

from __future__ import annotations


class ReportInputError(Exception):
    """Source load / normalize boundary failure with retry classification.

    The exception string is only a stable code so accidental logging cannot
    leak TEXT/STT originals, provider bodies, or secrets.
    """

    def __init__(self, code: str, *, retryable: bool) -> None:
        super().__init__(code)
        self.code = code
        self.retryable = retryable
