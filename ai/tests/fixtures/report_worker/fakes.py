"""Fake Backend / AI ports for AI-007 Gate 3A tests."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
from typing import Any

from app.schemas.report_evidence import EvidenceLinkRequest, EvidenceLinkResult
from app.schemas.report_generation import ReportGenerationResult
from app.schemas.report_input import NormalizedReportInput
from app.schemas.report_worker import AcquireStatus, WorkerStage
from app.services.report_backend_port import (
    AcquireRequest,
    AcquireResult,
    CompleteCommand,
    FailCommand,
    ProgressUpdate,
    ReportBackendError,
)


@dataclass
class FakeReportBackend:
    status: AcquireStatus = AcquireStatus.ACQUIRED
    report_id: int = 48
    processing_token: str = "tok-1"
    processing_attempt: int = 1
    progress_error: ReportBackendError | None = None
    progress_error_after: int | None = None
    complete_error: ReportBackendError | None = None
    fail_error: ReportBackendError | None = None
    acquire_error: ReportBackendError | None = None
    omit_token: bool = False
    omit_attempt: bool = False

    progress_calls: list[ProgressUpdate] = field(default_factory=list)
    complete_calls: list[CompleteCommand] = field(default_factory=list)
    fail_calls: list[FailCommand] = field(default_factory=list)
    acquire_calls: list[AcquireRequest] = field(default_factory=list)

    async def acquire(self, request: AcquireRequest) -> AcquireResult:
        self.acquire_calls.append(request)
        if self.acquire_error is not None:
            raise self.acquire_error
        if self.status != AcquireStatus.ACQUIRED:
            return AcquireResult(
                status=self.status,
                report_id=self.report_id,
            )
        return AcquireResult(
            status=AcquireStatus.ACQUIRED,
            report_id=self.report_id,
            processing_token=None if self.omit_token else self.processing_token,
            processing_attempt=(
                None if self.omit_attempt else self.processing_attempt
            ),
        )

    async def update_progress(self, update: ProgressUpdate) -> None:
        next_index = len(self.progress_calls) + 1
        self.progress_calls.append(update)
        if self.progress_error is None:
            return
        # None => fail on first call; N => fail when call count reaches N.
        threshold = (
            1 if self.progress_error_after is None else self.progress_error_after
        )
        if next_index >= threshold:
            raise self.progress_error

    async def complete(self, command: CompleteCommand) -> None:
        self.complete_calls.append(command)
        if self.complete_error is not None:
            raise self.complete_error

    async def fail(self, command: FailCommand) -> None:
        self.fail_calls.append(command)
        if self.fail_error is not None:
            raise self.fail_error

    @property
    def stages(self) -> list[WorkerStage]:
        return [item.stage for item in self.progress_calls]


@dataclass
class FakeReportInput:
    normalized: NormalizedReportInput
    load_error: Exception | None = None
    normalize_error: Exception | None = None
    load_calls: int = 0
    normalize_calls: int = 0
    last_source: Any = None

    async def load_source(self, report_id: int) -> Any:
        self.load_calls += 1
        if self.load_error is not None:
            raise self.load_error
        source = {"reportId": report_id}
        self.last_source = source
        return source

    def normalize(self, source: Any) -> NormalizedReportInput:
        self.normalize_calls += 1
        self.last_source = source
        if self.normalize_error is not None:
            raise self.normalize_error
        return self.normalized


@dataclass
class FakeGenerationService:
    results: Sequence[ReportGenerationResult | Exception]
    calls: int = 0
    _index: int = 0

    async def generate_report(
        self, normalized_input: NormalizedReportInput
    ) -> ReportGenerationResult:
        del normalized_input
        self.calls += 1
        if self._index >= len(self.results):
            item = self.results[-1]
        else:
            item = self.results[self._index]
            self._index += 1
        if isinstance(item, Exception):
            raise item
        return item


@dataclass
class FakeEvidenceService:
    results: Sequence[EvidenceLinkResult | Exception]
    calls: int = 0
    _index: int = 0
    last_request: EvidenceLinkRequest | None = None

    async def link_evidence(
        self, request: EvidenceLinkRequest
    ) -> EvidenceLinkResult:
        self.last_request = request
        self.calls += 1
        if self._index >= len(self.results):
            item = self.results[-1]
        else:
            item = self.results[self._index]
            self._index += 1
        if isinstance(item, Exception):
            raise item
        return item


@dataclass
class RecordingSleeper:
    delays: list[float] = field(default_factory=list)

    async def __call__(self, seconds: float) -> None:
        self.delays.append(seconds)


def always(result: ReportGenerationResult) -> FakeGenerationService:
    return FakeGenerationService(results=[result])


def evidence_always(
    result: EvidenceLinkResult | None = None,
) -> FakeEvidenceService:
    return FakeEvidenceService(
        results=[result if result is not None else EvidenceLinkResult(claims=[])]
    )
