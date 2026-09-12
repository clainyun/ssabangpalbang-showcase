"""HTTP Adapter error mapping and secret non-logging tests."""

from __future__ import annotations

import logging
from datetime import datetime, timezone

import httpx
import pytest

from app.adapters.report_backend_http import ReportBackendHttpAdapter
from app.adapters.report_input_http import ReportInputHttpAdapter
from app.exceptions.report_worker import ReportInputError
from app.schemas.report_worker import WorkerStage
from app.services.report_backend_port import (
    AcquireRequest,
    ProgressUpdate,
    ReportBackendError,
)


def _handler(status: int, body: bytes = b"{}"):
    def _inner(request: httpx.Request) -> httpx.Response:
        return httpx.Response(status, content=body)

    return _inner


def _success_envelope(data: object) -> dict[str, object]:
    return {
        "success": True,
        "code": "REPORT_ACQUIRE_SUCCESS",
        "message": "리포트 처리권을 확인했습니다.",
        "data": data,
        "timestamp": "2026-08-02T12:00:00+09:00",
    }


def _error_envelope(code: str) -> dict[str, object]:
    return {
        "success": False,
        "code": code,
        "message": "리포트 처리 상태가 충돌합니다.",
        "data": None,
        "timestamp": "2026-08-02T12:00:00+09:00",
    }


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("status", "code", "retryable"),
    [
        (500, "BACKEND_TRANSIENT", True),
        (401, "BACKEND_AUTH_ERROR", False),
        (403, "BACKEND_AUTH_ERROR", False),
        (400, "BACKEND_CONTRACT_ERROR", False),
        (404, "BACKEND_CONTRACT_ERROR", False),
        (409, "BACKEND_CONTRACT_ERROR", False),
        (422, "BACKEND_CONTRACT_ERROR", False),
    ],
)
async def test_backend_status_mapping(status, code, retryable) -> None:
    transport = httpx.MockTransport(_handler(status, b'{"secret":"LEAK"}'))
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportBackendHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="super-secret-token",
        )
        with pytest.raises(ReportBackendError) as error:
            await adapter.acquire(
                AcquireRequest(
                    study_id=1,
                    session_id=1,
                    apartment_id=1,
                    occurred_at=datetime(2026, 8, 1, tzinfo=timezone.utc),
                )
            )
        assert error.value.code == code
        assert error.value.retryable is retryable


@pytest.mark.asyncio
async def test_timeout_and_connect_errors() -> None:
    def timeout_handler(request: httpx.Request) -> httpx.Response:
        raise httpx.TimeoutException("timeout")

    transport = httpx.MockTransport(timeout_handler)
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportBackendHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        with pytest.raises(ReportBackendError) as error:
            await adapter.acquire(
                AcquireRequest(
                    study_id=1,
                    session_id=1,
                    apartment_id=1,
                    occurred_at=datetime(2026, 8, 1, tzinfo=timezone.utc),
                )
            )
        assert error.value.code == "BACKEND_TRANSIENT"
        assert error.value.retryable is True

    def connect_handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("boom")

    transport = httpx.MockTransport(connect_handler)
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportBackendHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        with pytest.raises(ReportBackendError) as error:
            await adapter.acquire(
                AcquireRequest(
                    study_id=1,
                    session_id=1,
                    apartment_id=1,
                    occurred_at=datetime(2026, 8, 1, tzinfo=timezone.utc),
                )
            )
        assert error.value.code == "BACKEND_TRANSIENT"


@pytest.mark.asyncio
async def test_invalid_json_and_schema_mismatch() -> None:
    transport = httpx.MockTransport(_handler(200, b"not-json"))
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportBackendHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        with pytest.raises(ReportBackendError) as error:
            await adapter.acquire(
                AcquireRequest(
                    study_id=1,
                    session_id=1,
                    apartment_id=1,
                    occurred_at=datetime(2026, 8, 1, tzinfo=timezone.utc),
                )
            )
        assert error.value.code == "BACKEND_CONTRACT_ERROR"

    invalid_inner = _success_envelope({"status": "ACQUIRED", "reportId": 1})
    transport = httpx.MockTransport(
        lambda request: httpx.Response(200, json=invalid_inner)
    )
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportBackendHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        with pytest.raises(ReportBackendError) as error:
            await adapter.acquire(
                AcquireRequest(
                    study_id=1,
                    session_id=1,
                    apartment_id=1,
                    occurred_at=datetime(2026, 8, 1, tzinfo=timezone.utc),
                )
            )
        assert error.value.code == "BACKEND_CONTRACT_ERROR"


@pytest.mark.asyncio
async def test_acquire_rejects_legacy_unwrapped_200_body() -> None:
    transport = httpx.MockTransport(
        lambda request: httpx.Response(
            200,
            json={
                "status": "ACQUIRED",
                "reportId": 1,
                "processingToken": "tok",
                "processingAttempt": 1,
            },
        )
    )
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportBackendHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        with pytest.raises(ReportBackendError) as error:
            await adapter.acquire(
                AcquireRequest(
                    study_id=1,
                    session_id=1,
                    apartment_id=1,
                    occurred_at=datetime(2026, 8, 1, tzinfo=timezone.utc),
                )
            )
    assert error.value.code == "BACKEND_CONTRACT_ERROR"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("status", "body"),
    [
        (200, b""),
        (204, b'{"unexpected":true}'),
    ],
)
async def test_write_requires_exact_empty_204(status: int, body: bytes) -> None:
    transport = httpx.MockTransport(_handler(status, body))
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportBackendHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        with pytest.raises(ReportBackendError) as error:
            await adapter.update_progress(
                ProgressUpdate(
                    report_id=1,
                    processing_token="processing-token",
                    processing_attempt=1,
                    stage=WorkerStage.RECORD_COLLECTION,
                )
            )
    assert error.value.code == "BACKEND_CONTRACT_ERROR"
    assert error.value.retryable is False


@pytest.mark.asyncio
async def test_conflict_reads_safe_code_from_top_level_envelope() -> None:
    transport = httpx.MockTransport(
        lambda request: httpx.Response(
            409,
            json=_error_envelope("STALE_PROCESSING_TOKEN"),
        )
    )
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportBackendHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        with pytest.raises(ReportBackendError) as error:
            await adapter.update_progress(
                ProgressUpdate(
                    report_id=1,
                    processing_token="processing-token",
                    processing_attempt=1,
                    stage=WorkerStage.RECORD_COLLECTION,
                )
            )
    assert error.value.message == "Backend contract error"
    assert error.value.conflict_code == "STALE_PROCESSING_TOKEN"


@pytest.mark.asyncio
async def test_input_permanent_and_auth_errors() -> None:
    transport = httpx.MockTransport(
        lambda request: httpx.Response(
            404,
            json=_error_envelope("REPORT_NOT_FOUND"),
        )
    )
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportInputHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        with pytest.raises(ReportInputError) as error:
            await adapter.load_source(1)
        assert error.value.code == "SOURCE_LOAD_FAILED"
        assert error.value.retryable is False

    transport = httpx.MockTransport(_handler(401, b"{}"))
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportInputHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        with pytest.raises(ReportInputError) as error:
            await adapter.load_source(1)
        assert error.value.code == "BACKEND_AUTH_ERROR"


@pytest.mark.asyncio
async def test_secrets_not_logged(caplog: pytest.LogCaptureFixture) -> None:
    transport = httpx.MockTransport(
        _handler(500, b'{"body":"SECRET_BODY","token":"LEAK_TOKEN"}')
    )
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportBackendHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="super-secret-token",
        )
        with caplog.at_level(logging.INFO):
            with pytest.raises(ReportBackendError):
                await adapter.acquire(
                    AcquireRequest(
                        study_id=1,
                        session_id=1,
                        apartment_id=1,
                        occurred_at=datetime(2026, 8, 1, tzinfo=timezone.utc),
                    )
                )
    text = "\n".join(record.getMessage() for record in caplog.records)
    assert "SECRET_BODY" not in text
    assert "LEAK_TOKEN" not in text
    assert "super-secret-token" not in text
    assert "Authorization" not in text
