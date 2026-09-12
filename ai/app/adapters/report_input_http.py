"""ReportInputPort HTTP adapter: load Source via Backend, normalize locally."""

from __future__ import annotations

import logging
from typing import Any

import httpx
from pydantic import ValidationError

from app.exceptions.report_worker import ReportInputError
from app.schemas.report_backend_http import ApiSuccessHttpResponse
from app.schemas.report_input import (
    NormalizedReportInput,
    ReportNormalizationSource,
)
from app.schemas.report_worker import WorkerErrorCode
from app.services.report_input_normalizer import (
    ReportInputNormalizationError,
    normalize_report_input,
)

logger = logging.getLogger(__name__)


class ReportInputHttpAdapter:
    """Async HTTP load of ReportNormalizationSource; local AI-004 normalize."""

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
            "Accept": "application/json",
        }

    async def load_source(self, report_id: int) -> ReportNormalizationSource:
        path = f"/internal/v1/reports/{report_id}/input"
        url = f"{self._base_url}{path}"
        try:
            response = await self._client.get(url, headers=self._headers())
        except httpx.TimeoutException as exc:
            logger.info(
                "report input timeout reportId=%s",
                report_id,
            )
            raise ReportInputError(
                WorkerErrorCode.BACKEND_TRANSIENT.value,
                retryable=True,
            ) from exc
        except httpx.HTTPError as exc:
            logger.info(
                "report input network error reportId=%s",
                report_id,
            )
            raise ReportInputError(
                WorkerErrorCode.BACKEND_TRANSIENT.value,
                retryable=True,
            ) from exc

        status = response.status_code
        if status in {401, 403}:
            logger.info(
                "report input auth error reportId=%s status=%s",
                report_id,
                status,
            )
            raise ReportInputError(
                WorkerErrorCode.BACKEND_AUTH_ERROR.value,
                retryable=False,
            )
        if status >= 500:
            logger.info(
                "report input transient error reportId=%s status=%s",
                report_id,
                status,
            )
            raise ReportInputError(
                WorkerErrorCode.BACKEND_TRANSIENT.value,
                retryable=True,
            )
        if status == 404:
            logger.info(
                "report input missing source reportId=%s status=%s",
                report_id,
                status,
            )
            raise ReportInputError(
                WorkerErrorCode.SOURCE_LOAD_FAILED.value,
                retryable=False,
            )
        if status in {400, 409, 422}:
            logger.info(
                "report input contract error reportId=%s status=%s",
                report_id,
                status,
            )
            raise ReportInputError(
                WorkerErrorCode.BACKEND_CONTRACT_ERROR.value,
                retryable=False,
            )
        if status != 200:
            logger.info(
                "report input unexpected status reportId=%s status=%s",
                report_id,
                status,
            )
            raise ReportInputError(
                WorkerErrorCode.BACKEND_CONTRACT_ERROR.value,
                retryable=False,
            )

        try:
            payload: Any = response.json()
        except Exception as exc:
            logger.info(
                "report input invalid json reportId=%s",
                report_id,
            )
            raise ReportInputError(
                WorkerErrorCode.BACKEND_CONTRACT_ERROR.value,
                retryable=False,
            ) from exc

        try:
            envelope = ApiSuccessHttpResponse[
                ReportNormalizationSource
            ].model_validate(payload)
            return envelope.data
        except ValidationError as exc:
            logger.info(
                "report input schema mismatch reportId=%s",
                report_id,
            )
            raise ReportInputError(
                WorkerErrorCode.BACKEND_CONTRACT_ERROR.value,
                retryable=False,
            ) from exc

    def normalize(self, source: Any) -> NormalizedReportInput:
        if not isinstance(source, ReportNormalizationSource):
            try:
                source = ReportNormalizationSource.model_validate(source)
            except ValidationError as exc:
                raise ReportInputError(
                    WorkerErrorCode.NORMALIZATION_FAILED.value,
                    retryable=False,
                ) from exc
        try:
            return normalize_report_input(source)
        except ReportInputNormalizationError as exc:
            # Deterministic AI-004 input contract failures only.
            raise ReportInputError(
                WorkerErrorCode.NORMALIZATION_FAILED.value,
                retryable=False,
            ) from exc
        # Unknown RuntimeError/Exception must reach Worker for RETRY_LATER.
