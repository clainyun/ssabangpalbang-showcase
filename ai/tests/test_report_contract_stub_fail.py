"""Contract Stub fail API tests against the frozen BE-019 fail contract."""

from __future__ import annotations

import httpx
import pytest

from app.adapters.report_backend_http import ReportBackendHttpAdapter
from app.adapters.report_input_http import ReportInputHttpAdapter
from app.exceptions.report_generation import ReportGenerationError
from app.schemas.report_worker import WorkerOutcome, WorkerStage
from app.services.report_worker import ReportWorker
from tests.fixtures.report_worker.builders import (
    sample_evidence_result,
    sample_payload,
)
from tests.fixtures.report_worker.fakes import (
    FakeEvidenceService,
    FakeGenerationService,
    RecordingSleeper,
)
from tests.support.report_contract_stub import create_report_contract_stub

TOKEN = "test-report-internal-token-only"


@pytest.fixture
async def stub_client():
    app, state = create_report_contract_stub(internal_token=TOKEN)
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(
        transport=transport, base_url="http://stub"
    ) as client:
        yield client, state


async def _acquire(client: httpx.AsyncClient) -> dict:
    response = await client.post(
        "/internal/v1/reports/acquire",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json={
            "studyId": 7,
            "sessionId": 3,
            "apartmentId": 100,
            "occurredAt": "2026-08-01T09:15:30.123Z",
        },
    )
    assert response.status_code == 200
    return response.json()["data"]


def _fail_body(token: str, attempt: int) -> dict:
    return {
        "processingToken": token,
        "processingAttempt": attempt,
        "failedStage": "REPORT_GENERATION",
        "errorCode": "PROVIDER_FAILED",
        "message": "고정된 안전 메시지",
        "retryable": False,
    }


@pytest.mark.asyncio
async def test_fail_success_transitions_to_failed(stub_client) -> None:
    client, state = stub_client
    acquired = await _acquire(client)
    report_id = acquired["reportId"]
    response = await client.put(
        f"/internal/v1/reports/{report_id}/fail",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json=_fail_body(acquired["processingToken"], acquired["processingAttempt"]),
    )
    assert response.status_code == 204
    assert response.content == b""
    assert state.reports_by_id[report_id].status == "FAILED"
    assert state.reports_by_id[report_id].lease_expires_at is None


@pytest.mark.asyncio
async def test_fail_idempotent_same_payload(stub_client) -> None:
    client, _state = stub_client
    acquired = await _acquire(client)
    report_id = acquired["reportId"]
    body = _fail_body(acquired["processingToken"], acquired["processingAttempt"])
    first = await client.put(
        f"/internal/v1/reports/{report_id}/fail",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json=body,
    )
    second = await client.put(
        f"/internal/v1/reports/{report_id}/fail",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json=body,
    )
    assert first.status_code == 204
    assert second.status_code == 204


@pytest.mark.asyncio
async def test_fail_payload_conflict(stub_client) -> None:
    client, _state = stub_client
    acquired = await _acquire(client)
    report_id = acquired["reportId"]
    body = _fail_body(acquired["processingToken"], acquired["processingAttempt"])
    await client.put(
        f"/internal/v1/reports/{report_id}/fail",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json=body,
    )
    conflict = await client.put(
        f"/internal/v1/reports/{report_id}/fail",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json={**body, "errorCode": "OTHER_CODE"},
    )
    assert conflict.status_code == 409
    assert conflict.json()["code"] == "FAIL_PAYLOAD_CONFLICT"


@pytest.mark.asyncio
async def test_stale_token_on_fail(stub_client) -> None:
    client, _state = stub_client
    acquired = await _acquire(client)
    report_id = acquired["reportId"]
    response = await client.put(
        f"/internal/v1/reports/{report_id}/fail",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json=_fail_body("stale-token", acquired["processingAttempt"]),
    )
    assert response.status_code == 409
    assert response.json()["code"] == "STALE_PROCESSING_TOKEN"


@pytest.mark.asyncio
async def test_done_cannot_become_failed(stub_client) -> None:
    client, state = stub_client
    acquired = await _acquire(client)
    report_id = acquired["reportId"]
    report = state.reports_by_id[report_id]
    report.status = "DONE"
    report.lease_expires_at = None
    response = await client.put(
        f"/internal/v1/reports/{report_id}/fail",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json=_fail_body(acquired["processingToken"], acquired["processingAttempt"]),
    )
    assert response.status_code == 409
    assert response.json()["code"] == "STALE_PROCESSING_TOKEN"
    assert state.reports_by_id[report_id].status == "DONE"


@pytest.mark.asyncio
async def test_failed_acquire_returns_already_failed(stub_client) -> None:
    client, _state = stub_client
    acquired = await _acquire(client)
    report_id = acquired["reportId"]
    await client.put(
        f"/internal/v1/reports/{report_id}/fail",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json=_fail_body(acquired["processingToken"], acquired["processingAttempt"]),
    )
    again = await _acquire(client)
    assert again["status"] == "ALREADY_FAILED"
    assert again["reportId"] == report_id


@pytest.mark.asyncio
async def test_worker_terminal_fail_uses_stub_fail_api(stub_client) -> None:
    client, state = stub_client
    backend = ReportBackendHttpAdapter(
        client=client,
        base_url="http://stub",
        internal_token=TOKEN,
    )
    input_port = ReportInputHttpAdapter(
        client=client,
        base_url="http://stub",
        internal_token=TOKEN,
    )
    worker = ReportWorker(
        backend=backend,
        input_port=input_port,
        generation=FakeGenerationService(
            results=[
                ReportGenerationError(
                    "INPUT_TOO_LARGE", "usable source text exceeds"
                )
            ]
        ),
        evidence=FakeEvidenceService(results=[sample_evidence_result()]),
        sleeper=RecordingSleeper(),
        provider_retry_delay_seconds=0.01,
    )
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    reports = list(state.reports_by_id.values())
    assert reports
    assert reports[0].status == "FAILED"
    assert reports[0].fail_payload is not None
    assert (
        reports[0].fail_payload["failedStage"]
        == WorkerStage.REPORT_GENERATION.value
    )
