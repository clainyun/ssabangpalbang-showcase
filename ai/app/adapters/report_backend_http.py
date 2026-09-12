"""ReportBackendPort HTTP adapter (Gate 3B-A). Never logs bodies or tokens."""

from __future__ import annotations

import logging
from typing import Any

import httpx

from app.schemas.report_backend_http import (
    AcquireHttpRequest,
    AcquireHttpResponse,
    ApiErrorHttpResponse,
    ApiSuccessHttpResponse,
    CompleteHttpRequest,
    FailHttpRequest,
    ProgressHttpRequest,
)
from app.schemas.report_worker import WorkerErrorCode
from app.services.report_backend_port import (
    AcquireRequest,
    AcquireResult,
    CompleteCommand,
    FailCommand,
    ProgressUpdate,
    ReportBackendError,
)

logger = logging.getLogger(__name__)


class ReportBackendHttpAdapter:
    """Maps Backend internal REST endpoints to ReportBackendPort."""

    def __init__(
        self,
        *,
        client: httpx.AsyncClient,
        base_url: str,
        internal_token: str,
    ) -> None:
        if not base_url.strip():
            raise ValueError("REPORT_BACKEND_BASE_URL is required")
        if not internal_token.strip():
            raise ValueError("REPORT_INTERNAL_TOKEN is required")
        self._client = client
        self._base_url = base_url.rstrip("/")
        self._internal_token = internal_token

    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self._internal_token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    async def acquire(self, request: AcquireRequest) -> AcquireResult:
        body = AcquireHttpRequest(
            studyId=request.study_id,
            sessionId=request.session_id,
            apartmentId=request.apartment_id,
            occurredAt=request.occurred_at,
        )
        response = await self._request(
            "POST",
            "/internal/v1/reports/acquire",
            json=body.model_dump(mode="json"),
        )
        try:
            envelope = ApiSuccessHttpResponse[AcquireHttpResponse].model_validate(
                response.json()
            )
            parsed = envelope.data
        except Exception as exc:
            raise ReportBackendError(
                WorkerErrorCode.BACKEND_CONTRACT_ERROR.value,
                "Backend acquire response did not match contract",
                retryable=False,
            ) from exc
        return AcquireResult(
            status=parsed.status,
            report_id=parsed.reportId,
            processing_token=parsed.processingToken,
            processing_attempt=parsed.processingAttempt,
        )

    async def update_progress(self, update: ProgressUpdate) -> None:
        body = ProgressHttpRequest(
            processingToken=update.processing_token,
            processingAttempt=update.processing_attempt,
            stage=update.stage.value,  # type: ignore[arg-type]
        )
        await self._request(
            "PATCH",
            f"/internal/v1/reports/{update.report_id}/progress",
            json=body.model_dump(mode="json"),
            expect_no_content=True,
        )

    async def complete(self, command: CompleteCommand) -> None:
        body = CompleteHttpRequest(
            processingToken=command.processing_token,
            processingAttempt=command.processing_attempt,
            generationResult=command.generation_result,
            evidenceResult=command.evidence_result,
        )
        await self._request(
            "PUT",
            f"/internal/v1/reports/{command.report_id}/complete",
            json=body.model_dump(mode="json"),
            expect_no_content=True,
        )

    async def fail(self, command: FailCommand) -> None:
        body = FailHttpRequest(
            processingToken=command.processing_token,
            processingAttempt=command.processing_attempt,
            failedStage=command.failed_stage,
            errorCode=command.error_code,
            message=command.message,
            retryable=command.retryable,
        )
        await self._request(
            "PUT",
            f"/internal/v1/reports/{command.report_id}/fail",
            json=body.model_dump(mode="json"),
            expect_no_content=True,
        )

    async def _request(
        self,
        method: str,
        path: str,
        *,
        json: dict[str, Any] | None = None,
        expect_no_content: bool = False,
    ) -> httpx.Response:
        url = f"{self._base_url}{path}"
        try:
            response = await self._client.request(
                method,
                url,
                headers=self._headers(),
                json=json,
            )
        except httpx.TimeoutException as exc:
            logger.info(
                "report backend timeout method=%s path=%s",
                method,
                path,
            )
            raise ReportBackendError(
                WorkerErrorCode.BACKEND_TRANSIENT.value,
                "Backend request timed out",
                retryable=True,
            ) from exc
        except httpx.HTTPError as exc:
            logger.info(
                "report backend network error method=%s path=%s",
                method,
                path,
            )
            raise ReportBackendError(
                WorkerErrorCode.BACKEND_TRANSIENT.value,
                "Backend network error",
                retryable=True,
            ) from exc

        status = response.status_code
        if expect_no_content and status == 204:
            if response.content:
                logger.info(
                    "report backend unexpected response body method=%s path=%s "
                    "status=%s",
                    method,
                    path,
                    status,
                )
                raise ReportBackendError(
                    WorkerErrorCode.BACKEND_CONTRACT_ERROR.value,
                    "Backend 204 response must not contain a body",
                    retryable=False,
                )
            return response
        if not expect_no_content and status == 200:
            return response
        if status in {401, 403}:
            logger.info(
                "report backend auth error method=%s path=%s status=%s",
                method,
                path,
                status,
            )
            raise ReportBackendError(
                WorkerErrorCode.BACKEND_AUTH_ERROR.value,
                "Backend authentication failed",
                retryable=False,
            )
        if status >= 500:
            logger.info(
                "report backend transient error method=%s path=%s status=%s",
                method,
                path,
                status,
            )
            raise ReportBackendError(
                WorkerErrorCode.BACKEND_TRANSIENT.value,
                "Backend transient error",
                retryable=True,
            )
        if status in {400, 404, 409, 422}:
            conflict_code = _extract_safe_error_code(response)
            logger.info(
                "report backend contract error method=%s path=%s status=%s "
                "conflictCode=%s",
                method,
                path,
                status,
                conflict_code or "-",
            )
            raise ReportBackendError(
                WorkerErrorCode.BACKEND_CONTRACT_ERROR.value,
                "Backend contract error",
                retryable=False,
                conflict_code=conflict_code,
            )
        logger.info(
            "report backend unexpected status method=%s path=%s status=%s",
            method,
            path,
            status,
        )
        raise ReportBackendError(
            WorkerErrorCode.BACKEND_CONTRACT_ERROR.value,
            "Backend unexpected status",
            retryable=False,
        )


def _extract_safe_error_code(response: httpx.Response) -> str | None:
    """Read an allowlisted top-level ApiResponse code without logging its body."""

    try:
        payload = ApiErrorHttpResponse.model_validate(response.json())
    except Exception:
        return None
    if payload.code in {
        "STALE_PROCESSING_TOKEN",
        "COMPLETE_PAYLOAD_CONFLICT",
        "FAIL_PAYLOAD_CONFLICT",
    }:
        return payload.code
    return None
