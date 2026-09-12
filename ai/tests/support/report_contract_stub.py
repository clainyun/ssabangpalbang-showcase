"""In-memory Contract Stub for AI-007 Gate 3B-A (test-only, not deployable Backend)."""

from __future__ import annotations

import asyncio
import json
import math
import secrets
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable
from zoneinfo import ZoneInfo

from fastapi import FastAPI, Header, HTTPException, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.schemas.report_backend_http import (
    AcquireHttpRequest,
    AcquireHttpResponse,
    ApiSuccessHttpResponse,
    CompleteHttpRequest,
    FailHttpRequest,
    ProgressHttpRequest,
)
from app.schemas.report_input import ReportNormalizationSource
from app.schemas.report_worker import AcquireStatus, WorkerStage

SOURCE_FIXTURE = (
    Path(__file__).resolve().parents[3]
    / "contracts"
    / "ai-004"
    / "report_normalization_source.json"
)

# Safe machine-readable conflict codes (no secrets / bodies).
ERROR_STALE_PROCESSING_TOKEN = "STALE_PROCESSING_TOKEN"
ERROR_COMPLETE_PAYLOAD_CONFLICT = "COMPLETE_PAYLOAD_CONFLICT"
ERROR_FAIL_PAYLOAD_CONFLICT = "FAIL_PAYLOAD_CONFLICT"
ERROR_INVALID_INPUT_VALUE = "INVALID_INPUT_VALUE"
ERROR_REPORT_NOT_FOUND = "REPORT_NOT_FOUND"
ERROR_UNAUTHORIZED = "UNAUTHORIZED"
ERROR_FORBIDDEN = "FORBIDDEN"

SEOUL_ZONE = ZoneInfo("Asia/Seoul")


@dataclass
class StubReport:
    report_id: int
    study_id: int
    session_id: int
    apartment_id: int
    status: str = "IN_PROGRESS"
    processing_token: str | None = None
    processing_attempt: int = 0
    lease_expires_at: datetime | None = None
    stage: WorkerStage | None = None
    complete_payload: dict[str, Any] | None = None
    fail_payload: dict[str, Any] | None = None


@dataclass
class ReportContractStubState:
    token: str
    lease_seconds: float = 1_800.0
    now_fn: Callable[[], datetime] = field(
        default_factory=lambda: (lambda: datetime.now(timezone.utc))
    )
    reports_by_study: dict[int, StubReport] = field(default_factory=dict)
    reports_by_id: dict[int, StubReport] = field(default_factory=dict)
    next_report_id: int = 1
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)
    source_override: dict[str, Any] | None = None

    def reset(self) -> None:
        self.reports_by_study.clear()
        self.reports_by_id.clear()
        self.next_report_id = 1
        self.source_override = None

    def _now(self) -> datetime:
        return self.now_fn()

    def _lease_expired(self, report: StubReport) -> bool:
        if report.lease_expires_at is None:
            return True
        return self._now() >= report.lease_expires_at

    def _issue_lease(self, report: StubReport) -> None:
        report.processing_token = uuid.uuid4().hex
        report.processing_attempt += 1
        self._renew_lease(report)
        report.status = "IN_PROGRESS"
        report.fail_payload = None

    def _renew_lease(self, report: StubReport) -> None:
        report.lease_expires_at = self._now() + timedelta(seconds=self.lease_seconds)

    def _retry_after_seconds(self, report: StubReport) -> int | None:
        if report.lease_expires_at is None:
            return None
        remaining = (report.lease_expires_at - self._now()).total_seconds()
        return max(0, int(math.ceil(remaining)))


def load_canonical_source_payload() -> dict[str, Any]:
    return json.loads(SOURCE_FIXTURE.read_text(encoding="utf-8"))


def _api_success(data: Any, *, code: str, message: str) -> dict[str, Any]:
    return {
        "success": True,
        "code": code,
        "message": message,
        "data": data,
        "timestamp": datetime.now(SEOUL_ZONE).isoformat(),
    }


def _api_error(*, code: str, message: str) -> dict[str, Any]:
    return {
        "success": False,
        "code": code,
        "message": message,
        "data": None,
        "timestamp": datetime.now(SEOUL_ZONE).isoformat(),
    }


def _http_error(status_code: int, code: str, message: str) -> HTTPException:
    return HTTPException(
        status_code=status_code,
        detail={"code": code, "message": message},
    )


def _conflict(error_code: str) -> HTTPException:
    return _http_error(409, error_code, "리포트 처리 상태가 충돌합니다.")


def create_report_contract_stub(
    *,
    internal_token: str | None = None,
    lease_seconds: float = 1_800.0,
    now_fn: Callable[[], datetime] | None = None,
) -> tuple[FastAPI, ReportContractStubState]:
    """Build a FastAPI Test App implementing the Gate 3B-A HTTP contract."""

    state = ReportContractStubState(
        token=internal_token or f"stub-{secrets.token_hex(8)}",
        lease_seconds=lease_seconds,
        now_fn=now_fn or (lambda: datetime.now(timezone.utc)),
    )
    app = FastAPI(title="AI-007 Report Contract Stub (TEST ONLY)")

    @app.exception_handler(HTTPException)
    async def http_exception_handler(
        _request: Request,
        exc: HTTPException,
    ) -> JSONResponse:
        detail = exc.detail if isinstance(exc.detail, dict) else {}
        code = detail.get("code")
        message = detail.get("message")
        if not isinstance(code, str) or not code:
            code = "INTERNAL_API_ERROR"
        if not isinstance(message, str) or not message:
            message = "내부 API 요청을 처리할 수 없습니다."
        return JSONResponse(
            status_code=exc.status_code,
            content=_api_error(code=code, message=message),
        )

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(
        _request: Request,
        _exc: RequestValidationError,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=422,
            content=_api_error(
                code=ERROR_INVALID_INPUT_VALUE,
                message="요청 값이 올바르지 않습니다.",
            ),
        )

    def _auth(authorization: str | None) -> None:
        if authorization is None or not authorization.startswith("Bearer "):
            raise _http_error(
                401,
                ERROR_UNAUTHORIZED,
                "내부 API 인증이 필요합니다.",
            )
        provided = authorization.removeprefix("Bearer ").strip()
        if provided != state.token:
            raise _http_error(
                403,
                ERROR_FORBIDDEN,
                "내부 API 접근 권한이 없습니다.",
            )

    def _require_active_token(report: StubReport, token: str, attempt: int) -> None:
        if (
            report.processing_token != token
            or report.processing_attempt != attempt
            or state._lease_expired(report)
        ):
            raise _conflict(ERROR_STALE_PROCESSING_TOKEN)

    @app.post(
        "/internal/v1/reports/acquire",
        response_model=ApiSuccessHttpResponse[AcquireHttpResponse],
    )
    async def acquire(
        body: AcquireHttpRequest,
        authorization: str | None = Header(default=None),
    ) -> dict[str, Any]:
        _auth(authorization)
        async with state.lock:
            existing = state.reports_by_study.get(body.studyId)
            if existing is None:
                report = StubReport(
                    report_id=state.next_report_id,
                    study_id=body.studyId,
                    session_id=body.sessionId,
                    apartment_id=body.apartmentId,
                )
                state._issue_lease(report)
                state.next_report_id += 1
                state.reports_by_study[body.studyId] = report
                state.reports_by_id[report.report_id] = report
                return _api_success(
                    AcquireHttpResponse(
                        status=AcquireStatus.ACQUIRED,
                        reportId=report.report_id,
                        processingToken=report.processing_token,
                        processingAttempt=report.processing_attempt,
                        leaseExpiresAt=report.lease_expires_at,
                    ),
                    code="REPORT_ACQUIRE_SUCCESS",
                    message="리포트 처리권을 확인했습니다.",
                )

            if (
                existing.session_id != body.sessionId
                or existing.apartment_id != body.apartmentId
            ):
                return _api_success(
                    AcquireHttpResponse(status=AcquireStatus.CONTRACT_CONFLICT),
                    code="REPORT_ACQUIRE_SUCCESS",
                    message="리포트 처리권을 확인했습니다.",
                )

            if existing.status == "DONE":
                return _api_success(
                    AcquireHttpResponse(
                        status=AcquireStatus.ALREADY_COMPLETED,
                        reportId=existing.report_id,
                    ),
                    code="REPORT_ACQUIRE_SUCCESS",
                    message="리포트 처리권을 확인했습니다.",
                )

            if existing.status == "FAILED":
                # Terminal FAILED is not auto-restarted by Kafka redelivery.
                # Backend retry first moves FAILED -> PENDING, then publishes an event.
                return _api_success(
                    AcquireHttpResponse(
                        status=AcquireStatus.ALREADY_FAILED,
                        reportId=existing.report_id,
                    ),
                    code="REPORT_ACQUIRE_SUCCESS",
                    message="리포트 처리권을 확인했습니다.",
                )

            # A Backend retry has already authorized FAILED -> PENDING.
            if existing.status == "PENDING":
                state._issue_lease(existing)
                return _api_success(
                    AcquireHttpResponse(
                        status=AcquireStatus.ACQUIRED,
                        reportId=existing.report_id,
                        processingToken=existing.processing_token,
                        processingAttempt=existing.processing_attempt,
                        leaseExpiresAt=existing.lease_expires_at,
                    ),
                    code="REPORT_ACQUIRE_SUCCESS",
                    message="리포트 처리권을 확인했습니다.",
                )

            # IN_PROGRESS: honor Backend-managed lease.
            if not state._lease_expired(existing):
                return _api_success(
                    AcquireHttpResponse(
                        status=AcquireStatus.ALREADY_PROCESSING,
                        reportId=existing.report_id,
                        retryAfterSeconds=state._retry_after_seconds(existing),
                    ),
                    code="REPORT_ACQUIRE_SUCCESS",
                    message="리포트 처리권을 확인했습니다.",
                )

            # Lease expired → re-claim with new token/attempt.
            state._issue_lease(existing)
            return _api_success(
                AcquireHttpResponse(
                    status=AcquireStatus.ACQUIRED,
                    reportId=existing.report_id,
                    processingToken=existing.processing_token,
                    processingAttempt=existing.processing_attempt,
                    leaseExpiresAt=existing.lease_expires_at,
                ),
                code="REPORT_ACQUIRE_SUCCESS",
                message="리포트 처리권을 확인했습니다.",
            )

    @app.get(
        "/internal/v1/reports/{report_id}/input",
        response_model=ApiSuccessHttpResponse[ReportNormalizationSource],
    )
    async def input_source(
        report_id: int,
        authorization: str | None = Header(default=None),
    ) -> dict[str, Any]:
        _auth(authorization)
        report = state.reports_by_id.get(report_id)
        if report is None:
            raise _http_error(
                404,
                ERROR_REPORT_NOT_FOUND,
                "리포트를 찾을 수 없습니다.",
            )
        payload = state.source_override or load_canonical_source_payload()
        adjusted = dict(payload)
        adjusted["reportId"] = report.report_id
        adjusted["studyId"] = report.study_id
        adjusted["apartmentId"] = report.apartment_id
        adjusted["fieldSessionId"] = report.session_id
        ReportNormalizationSource.model_validate(adjusted)
        return _api_success(
            adjusted,
            code="REPORT_INPUT_SUCCESS",
            message="리포트 입력을 조회했습니다.",
        )

    @app.patch("/internal/v1/reports/{report_id}/progress", status_code=204)
    async def progress(
        report_id: int,
        body: ProgressHttpRequest,
        authorization: str | None = Header(default=None),
    ) -> Response:
        _auth(authorization)
        report = state.reports_by_id.get(report_id)
        if report is None:
            raise _http_error(
                404,
                ERROR_REPORT_NOT_FOUND,
                "리포트를 찾을 수 없습니다.",
            )
        _require_active_token(
            report, body.processingToken, body.processingAttempt
        )
        report.stage = WorkerStage(body.stage)
        state._renew_lease(report)
        return Response(status_code=204)

    @app.put("/internal/v1/reports/{report_id}/complete", status_code=204)
    async def complete(
        report_id: int,
        body: CompleteHttpRequest,
        authorization: str | None = Header(default=None),
    ) -> Response:
        _auth(authorization)
        report = state.reports_by_id.get(report_id)
        if report is None:
            raise _http_error(
                404,
                ERROR_REPORT_NOT_FOUND,
                "리포트를 찾을 수 없습니다.",
            )
        payload = body.model_dump(mode="json")
        if report.status == "DONE":
            if report.complete_payload == payload:
                return Response(status_code=204)
            raise _conflict(ERROR_COMPLETE_PAYLOAD_CONFLICT)
        _require_active_token(
            report, body.processingToken, body.processingAttempt
        )
        report.status = "DONE"
        report.stage = WorkerStage.COMPLETED
        report.complete_payload = payload
        report.lease_expires_at = None
        return Response(status_code=204)

    @app.put("/internal/v1/reports/{report_id}/fail", status_code=204)
    async def fail(
        report_id: int,
        body: FailHttpRequest,
        authorization: str | None = Header(default=None),
    ) -> Response:
        _auth(authorization)
        report = state.reports_by_id.get(report_id)
        if report is None:
            raise _http_error(
                404,
                ERROR_REPORT_NOT_FOUND,
                "리포트를 찾을 수 없습니다.",
            )
        payload = {
            "failedStage": body.failedStage.value,
            "errorCode": body.errorCode,
            "message": body.message,
            "retryable": body.retryable,
            "processingToken": body.processingToken,
            "processingAttempt": body.processingAttempt,
        }
        if report.status == "DONE":
            # Frozen BE-019 contract: never overwrite DONE with FAILED.
            raise _conflict(ERROR_STALE_PROCESSING_TOKEN)
        if report.status == "FAILED":
            if report.fail_payload == payload:
                return Response(status_code=204)
            raise _conflict(ERROR_FAIL_PAYLOAD_CONFLICT)
        if report.status != "IN_PROGRESS":
            raise _conflict(ERROR_STALE_PROCESSING_TOKEN)
        _require_active_token(
            report, body.processingToken, body.processingAttempt
        )
        report.status = "FAILED"
        report.fail_payload = payload
        report.lease_expires_at = None
        return Response(status_code=204)

    return app, state
