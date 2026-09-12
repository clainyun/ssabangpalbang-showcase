"""Gate 3B-A Contract Stub + HTTP DTO / Adapter contract tests."""

from __future__ import annotations

import asyncio
from datetime import datetime, timezone

import httpx
import pytest
from pydantic import ValidationError

from app.adapters.report_backend_http import ReportBackendHttpAdapter
from app.adapters.report_input_http import ReportInputHttpAdapter
from app.schemas.report_backend_http import (
    AcquireHttpRequest,
    AcquireHttpResponse,
    ProgressHttpRequest,
)
from app.schemas.report_input import ReportNormalizationSource
from app.schemas.report_worker import AcquireStatus, WorkerStage
from app.services.report_backend_port import (
    AcquireRequest,
    CompleteCommand,
    FailCommand,
    ProgressUpdate,
)
from tests.fixtures.report_worker.builders import (
    claimed_generation,
    sample_evidence_result,
    sufficient_normalized,
)
from tests.support.report_contract_stub import create_report_contract_stub


TOKEN = "test-report-internal-token"


@pytest.fixture
async def stub_client():
    app, state = create_report_contract_stub(internal_token=TOKEN)
    state.reset()
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://stub",
    ) as client:
        yield client, state, app


def _backend(client: httpx.AsyncClient) -> ReportBackendHttpAdapter:
    return ReportBackendHttpAdapter(
        client=client,
        base_url="http://stub",
        internal_token=TOKEN,
    )


def _input(client: httpx.AsyncClient) -> ReportInputHttpAdapter:
    return ReportInputHttpAdapter(
        client=client,
        base_url="http://stub",
        internal_token=TOKEN,
    )


@pytest.mark.asyncio
async def test_stub_read_responses_use_common_flat_envelope(stub_client) -> None:
    client, _, _ = stub_client
    response = await client.post(
        "/internal/v1/reports/acquire",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json={
            "studyId": 7,
            "sessionId": 3,
            "apartmentId": 100,
            "occurredAt": "2026-08-01T09:00:00+00:00",
        },
    )
    assert response.status_code == 200
    payload = response.json()
    assert set(payload) == {"success", "code", "message", "data", "timestamp"}
    assert payload["success"] is True
    assert payload["data"]["status"] == "ACQUIRED"
    assert payload["timestamp"].endswith("+09:00")

    report_id = payload["data"]["reportId"]
    input_response = await client.get(
        f"/internal/v1/reports/{report_id}/input",
        headers={"Authorization": f"Bearer {TOKEN}"},
    )
    assert input_response.status_code == 200
    input_payload = input_response.json()
    assert set(input_payload) == {
        "success",
        "code",
        "message",
        "data",
        "timestamp",
    }
    assert input_payload["data"]["reportId"] == report_id


@pytest.mark.asyncio
async def test_stub_errors_use_top_level_code(stub_client) -> None:
    client, _, _ = stub_client
    response = await client.get(
        "/internal/v1/reports/999/input",
        headers={"Authorization": f"Bearer {TOKEN}"},
    )
    assert response.status_code == 404
    payload = response.json()
    assert set(payload) == {"success", "code", "message", "data", "timestamp"}
    assert payload["success"] is False
    assert payload["code"] == "REPORT_NOT_FOUND"
    assert payload["data"] is None
    assert "detail" not in payload


@pytest.mark.asyncio
async def test_stub_write_response_is_empty_204(stub_client) -> None:
    client, _, _ = stub_client
    acquired = await _backend(client).acquire(
        AcquireRequest(
            study_id=8,
            session_id=3,
            apartment_id=100,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    response = await client.patch(
        f"/internal/v1/reports/{acquired.report_id}/progress",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json={
            "processingToken": acquired.processing_token,
            "processingAttempt": acquired.processing_attempt,
            "stage": "RECORD_COLLECTION",
        },
    )
    assert response.status_code == 204
    assert response.content == b""


@pytest.mark.asyncio
async def test_acquire_acquired_and_token_required(stub_client) -> None:
    client, _, _ = stub_client
    backend = _backend(client)
    result = await backend.acquire(
        AcquireRequest(
            study_id=7,
            session_id=3,
            apartment_id=100,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    assert result.status == AcquireStatus.ACQUIRED
    assert result.report_id is not None
    assert result.processing_token
    assert result.processing_attempt == 1


@pytest.mark.asyncio
async def test_acquire_already_processing_and_completed(stub_client) -> None:
    client, state, _ = stub_client
    backend = _backend(client)
    first = await backend.acquire(
        AcquireRequest(
            study_id=7,
            session_id=3,
            apartment_id=100,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    second = await backend.acquire(
        AcquireRequest(
            study_id=7,
            session_id=3,
            apartment_id=100,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    assert second.status == AcquireStatus.ALREADY_PROCESSING
    assert second.report_id == first.report_id

    report = state.reports_by_id[first.report_id]
    report.status = "DONE"
    third = await backend.acquire(
        AcquireRequest(
            study_id=7,
            session_id=3,
            apartment_id=100,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    assert third.status == AcquireStatus.ALREADY_COMPLETED


@pytest.mark.asyncio
async def test_acquire_contract_conflict(stub_client) -> None:
    client, _, _ = stub_client
    backend = _backend(client)
    await backend.acquire(
        AcquireRequest(
            study_id=7,
            session_id=3,
            apartment_id=100,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    conflict = await backend.acquire(
        AcquireRequest(
            study_id=7,
            session_id=99,
            apartment_id=100,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    assert conflict.status == AcquireStatus.CONTRACT_CONFLICT


def test_acquire_dto_rejects_extra_and_naive_datetime() -> None:
    with pytest.raises(ValidationError):
        AcquireHttpRequest.model_validate(
            {
                "studyId": 1,
                "sessionId": 1,
                "apartmentId": 1,
                "occurredAt": "2026-08-01T09:00:00+00:00",
                "extra": True,
            }
        )
    with pytest.raises(ValidationError):
        AcquireHttpRequest.model_validate(
            {
                "studyId": 1,
                "sessionId": 1,
                "apartmentId": 1,
                "occurredAt": "2026-08-01T09:00:00",
            }
        )


def test_acquired_response_requires_attempt() -> None:
    with pytest.raises(ValidationError):
        AcquireHttpResponse.model_validate(
            {
                "status": "ACQUIRED",
                "reportId": 1,
                "processingToken": "tok",
                "processingAttempt": 0,
            }
        )


@pytest.mark.asyncio
async def test_input_returns_ai004_source(stub_client) -> None:
    client, _, _ = stub_client
    backend = _backend(client)
    input_port = _input(client)
    acquired = await backend.acquire(
        AcquireRequest(
            study_id=7,
            session_id=3,
            apartment_id=100,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    source = await input_port.load_source(acquired.report_id)
    assert isinstance(source, ReportNormalizationSource)
    assert source.reportId == acquired.report_id
    normalized = input_port.normalize(source)
    assert normalized.schemaVersion == 1


@pytest.mark.asyncio
async def test_progress_complete_fail_204(stub_client) -> None:
    client, state, _ = stub_client
    backend = _backend(client)
    acquired = await backend.acquire(
        AcquireRequest(
            study_id=11,
            session_id=4,
            apartment_id=200,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    await backend.update_progress(
        ProgressUpdate(
            report_id=acquired.report_id,
            processing_token=acquired.processing_token,
            processing_attempt=acquired.processing_attempt,
            stage=WorkerStage.RECORD_COLLECTION,
        )
    )
    await backend.complete(
        CompleteCommand(
            report_id=acquired.report_id,
            processing_token=acquired.processing_token,
            processing_attempt=acquired.processing_attempt,
            generation_result=claimed_generation(sufficient_normalized()),
            evidence_result=sample_evidence_result(),
        )
    )
    report = state.reports_by_id[acquired.report_id]
    assert report.status == "DONE"
    assert report.stage == WorkerStage.COMPLETED
    # Idempotent complete
    await backend.complete(
        CompleteCommand(
            report_id=acquired.report_id,
            processing_token=acquired.processing_token,
            processing_attempt=acquired.processing_attempt,
            generation_result=claimed_generation(sufficient_normalized()),
            evidence_result=sample_evidence_result(),
        )
    )


@pytest.mark.asyncio
async def test_wrong_token_progress_and_fail(stub_client) -> None:
    client, _, _ = stub_client
    backend = _backend(client)
    acquired = await backend.acquire(
        AcquireRequest(
            study_id=12,
            session_id=4,
            apartment_id=200,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    from app.services.report_backend_port import ReportBackendError

    with pytest.raises(ReportBackendError) as progress_error:
        await backend.update_progress(
            ProgressUpdate(
                report_id=acquired.report_id,
                processing_token="wrong",
                processing_attempt=acquired.processing_attempt,
                stage=WorkerStage.RECORD_COLLECTION,
            )
        )
    assert progress_error.value.code == "BACKEND_CONTRACT_ERROR"

    # Separate acquire for fail path
    acquired2 = await backend.acquire(
        AcquireRequest(
            study_id=13,
            session_id=4,
            apartment_id=200,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    with pytest.raises(ReportBackendError):
        await backend.fail(
            FailCommand(
                report_id=acquired2.report_id,
                processing_token="wrong",
                processing_attempt=acquired2.processing_attempt,
                failed_stage=WorkerStage.NORMALIZATION,
                error_code="NORMALIZATION_FAILED",
                message="리포트 입력 정규화에 실패했습니다.",
                retryable=False,
            )
        )


@pytest.mark.asyncio
async def test_complete_conflict_on_different_payload(stub_client) -> None:
    client, _, _ = stub_client
    backend = _backend(client)
    acquired = await backend.acquire(
        AcquireRequest(
            study_id=21,
            session_id=4,
            apartment_id=200,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    first = claimed_generation(sufficient_normalized())
    await backend.complete(
        CompleteCommand(
            report_id=acquired.report_id,
            processing_token=acquired.processing_token,
            processing_attempt=acquired.processing_attempt,
            generation_result=first,
            evidence_result=sample_evidence_result(),
        )
    )
    from app.schemas.report_evidence import EvidenceLinkResult
    from app.services.report_backend_port import ReportBackendError

    with pytest.raises(ReportBackendError) as error:
        await backend.complete(
            CompleteCommand(
                report_id=acquired.report_id,
                processing_token=acquired.processing_token,
                processing_attempt=acquired.processing_attempt,
                generation_result=first,
                evidence_result=EvidenceLinkResult(claims=[]),
            )
        )
    assert error.value.code == "BACKEND_CONTRACT_ERROR"


@pytest.mark.asyncio
async def test_complete_preserves_claim_keys_and_roles(stub_client) -> None:
    client, state, _ = stub_client
    backend = _backend(client)
    acquired = await backend.acquire(
        AcquireRequest(
            study_id=22,
            session_id=4,
            apartment_id=200,
            occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
        )
    )
    generation = claimed_generation(sufficient_normalized())
    evidence = sample_evidence_result()
    await backend.complete(
        CompleteCommand(
            report_id=acquired.report_id,
            processing_token=acquired.processing_token,
            processing_attempt=acquired.processing_attempt,
            generation_result=generation,
            evidence_result=evidence,
        )
    )
    stored = state.reports_by_id[acquired.report_id].complete_payload
    assert stored is not None
    claim = stored["evidenceResult"]["claims"][0]
    assert claim["claimKey"] == evidence.claims[0].claimKey
    assert claim["evidences"][0]["sourceId"] == evidence.claims[0].evidences[0].sourceId
    assert claim["evidences"][0]["evidenceRole"] == "SUPPORT"
    assert stored["generationResult"]["title"] == generation.title
    assert "dataSufficient" in stored["generationResult"]["categories"][0]


@pytest.mark.asyncio
async def test_concurrent_acquire_single_winner(stub_client) -> None:
    client, _, _ = stub_client
    backend = _backend(client)
    req = AcquireRequest(
        study_id=77,
        session_id=1,
        apartment_id=1,
        occurred_at=datetime(2026, 8, 1, 9, 0, tzinfo=timezone.utc),
    )
    results = await asyncio.gather(
        backend.acquire(req),
        backend.acquire(req),
        backend.acquire(req),
    )
    acquired = [r for r in results if r.status == AcquireStatus.ACQUIRED]
    processing = [
        r for r in results if r.status == AcquireStatus.ALREADY_PROCESSING
    ]
    assert len(acquired) == 1
    assert len(processing) == 2


def test_progress_stage_rejects_completed() -> None:
    with pytest.raises(ValidationError):
        ProgressHttpRequest.model_validate(
            {
                "processingToken": "tok",
                "processingAttempt": 1,
                "stage": "COMPLETED",
            }
        )
