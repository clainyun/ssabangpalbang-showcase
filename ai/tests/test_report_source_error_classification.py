"""P1 source-load / normalize error classification tests."""

from __future__ import annotations

import logging
from datetime import datetime, timezone

import httpx
import pytest

from app.adapters.report_input_http import ReportInputHttpAdapter
from app.exceptions.report_worker import ReportInputError
from app.schemas.report_worker import (
    CommitDecision,
    WorkerErrorCode,
    WorkerOutcome,
    commit_decision_for,
)
from app.services.report_worker import ReportWorker
from tests.fixtures.report_worker.builders import (
    claimed_generation,
    sample_evidence_result,
    sample_payload,
    sufficient_normalized,
)
from tests.fixtures.report_worker.fakes import (
    FakeEvidenceService,
    FakeGenerationService,
    FakeReportBackend,
    FakeReportInput,
    RecordingSleeper,
)


def _worker(
    *,
    input_port: FakeReportInput | None = None,
    backend: FakeReportBackend | None = None,
) -> tuple[ReportWorker, FakeReportBackend, FakeReportInput, FakeGenerationService]:
    source = sufficient_normalized()
    backend = backend or FakeReportBackend()
    input_port = input_port or FakeReportInput(normalized=source)
    generation = FakeGenerationService(results=[claimed_generation(source)])
    evidence = FakeEvidenceService(results=[sample_evidence_result()])
    worker = ReportWorker(
        backend=backend,
        input_port=input_port,
        generation=generation,
        evidence=evidence,
        sleeper=RecordingSleeper(),
        provider_retry_delay_seconds=0.01,
    )
    return worker, backend, input_port, generation


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("code", "retryable", "outcome", "decision"),
    [
        (
            WorkerErrorCode.BACKEND_AUTH_ERROR.value,
            False,
            WorkerOutcome.BACKEND_REJECTED,
            CommitDecision.POLICY_PENDING,
        ),
        (
            WorkerErrorCode.BACKEND_CONTRACT_ERROR.value,
            False,
            WorkerOutcome.BACKEND_REJECTED,
            CommitDecision.POLICY_PENDING,
        ),
        (
            WorkerErrorCode.BACKEND_TRANSIENT.value,
            True,
            WorkerOutcome.RETRY_LATER,
            CommitDecision.DO_NOT_COMMIT,
        ),
        (
            WorkerErrorCode.SOURCE_LOAD_FAILED.value,
            True,
            WorkerOutcome.RETRY_LATER,
            CommitDecision.DO_NOT_COMMIT,
        ),
        (
            WorkerErrorCode.SOURCE_LOAD_FAILED.value,
            False,
            WorkerOutcome.TERMINAL_FAILED,
            CommitDecision.COMMIT,
        ),
    ],
)
async def test_source_load_error_classification(
    code: str,
    retryable: bool,
    outcome: WorkerOutcome,
    decision: CommitDecision,
) -> None:
    worker, backend, input_port, generation = _worker()
    input_port.load_error = ReportInputError(code, retryable=retryable)
    result = await worker.process(sample_payload())
    assert result.outcome == outcome
    assert commit_decision_for(result.outcome) == decision
    assert generation.calls == 0
    assert backend.complete_calls == []
    if outcome == WorkerOutcome.TERMINAL_FAILED:
        assert len(backend.fail_calls) == 1
        assert backend.fail_calls[0].error_code == "SOURCE_LOAD_FAILED"
    else:
        assert backend.fail_calls == []


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("status", "code"),
    [
        (400, WorkerErrorCode.BACKEND_CONTRACT_ERROR.value),
        (409, WorkerErrorCode.BACKEND_CONTRACT_ERROR.value),
        (422, WorkerErrorCode.BACKEND_CONTRACT_ERROR.value),
        (404, WorkerErrorCode.SOURCE_LOAD_FAILED.value),
    ],
)
async def test_input_adapter_http_status_classification(
    status: int, code: str
) -> None:
    transport = httpx.MockTransport(
        lambda request: httpx.Response(status, content=b'{"body":"SECRET_BODY"}')
    )
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportInputHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="super-secret-token",
        )
        with pytest.raises(ReportInputError) as error:
            await adapter.load_source(1)
    assert error.value.code == code
    assert error.value.retryable is False


@pytest.mark.asyncio
@pytest.mark.parametrize("status", [400, 409, 422])
async def test_input_contract_statuses_through_worker_are_policy_pending(
    status: int,
    caplog: pytest.LogCaptureFixture,
) -> None:
    transport = httpx.MockTransport(
        lambda request: httpx.Response(status, content=b'{"leak":"HTTP_BODY"}')
    )

    class HttpInputPort(ReportInputHttpAdapter):
        pass

    async with httpx.AsyncClient(transport=transport) as client:
        input_port = HttpInputPort(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        worker, backend, _, generation = _worker()
        worker = ReportWorker(
            backend=backend,
            input_port=input_port,
            generation=generation,
            evidence=FakeEvidenceService(results=[sample_evidence_result()]),
            sleeper=RecordingSleeper(),
            provider_retry_delay_seconds=0.01,
        )
        with caplog.at_level(logging.DEBUG):
            result = await worker.process(sample_payload())

    assert result.outcome == WorkerOutcome.BACKEND_REJECTED
    assert result.error_code == WorkerErrorCode.BACKEND_CONTRACT_ERROR.value
    assert commit_decision_for(result.outcome) == CommitDecision.POLICY_PENDING
    assert backend.fail_calls == []
    assert backend.complete_calls == []
    assert generation.calls == 0
    text = "\n".join(record.getMessage() for record in caplog.records)
    assert "HTTP_BODY" not in text
    assert "Authorization" not in text
    assert "tok" not in text
    assert "Traceback" not in text


@pytest.mark.asyncio
async def test_input_404_through_worker_is_terminal_failed() -> None:
    transport = httpx.MockTransport(
        lambda request: httpx.Response(
            404,
            json={
                "success": False,
                "code": "REPORT_NOT_FOUND",
                "message": "리포트를 찾을 수 없습니다.",
                "data": None,
                "timestamp": "2026-08-02T12:00:00+09:00",
            },
        )
    )
    async with httpx.AsyncClient(transport=transport) as client:
        input_port = ReportInputHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        _, backend, _, generation = _worker()
        worker = ReportWorker(
            backend=backend,
            input_port=input_port,
            generation=generation,
            evidence=FakeEvidenceService(results=[sample_evidence_result()]),
            sleeper=RecordingSleeper(),
            provider_retry_delay_seconds=0.01,
        )
        result = await worker.process(sample_payload())

    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert commit_decision_for(result.outcome) == CommitDecision.COMMIT
    assert len(backend.fail_calls) == 1
    assert backend.fail_calls[0].error_code == "SOURCE_LOAD_FAILED"
    assert backend.complete_calls == []
    assert generation.calls == 0


@pytest.mark.asyncio
@pytest.mark.parametrize("status", [401, 403])
async def test_input_adapter_auth_maps_to_backend_auth(status: int) -> None:
    transport = httpx.MockTransport(
        lambda request: httpx.Response(status, content=b'{"secret":"LEAK"}')
    )
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportInputHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        with pytest.raises(ReportInputError) as error:
            await adapter.load_source(1)
    assert error.value.code == WorkerErrorCode.BACKEND_AUTH_ERROR.value
    assert error.value.retryable is False


@pytest.mark.asyncio
async def test_input_invalid_json_and_schema_are_contract_errors() -> None:
    transport = httpx.MockTransport(lambda request: httpx.Response(200, content=b"nope"))
    async with httpx.AsyncClient(transport=transport) as client:
        adapter = ReportInputHttpAdapter(
            client=client,
            base_url="http://backend",
            internal_token="tok",
        )
        with pytest.raises(ReportInputError) as error:
            await adapter.load_source(1)
    assert error.value.code == WorkerErrorCode.BACKEND_CONTRACT_ERROR.value

    transport = httpx.MockTransport(
        lambda request: httpx.Response(
            200,
            json={
                "success": True,
                "code": "REPORT_INPUT_SUCCESS",
                "message": "리포트 입력을 조회했습니다.",
                "data": {"schemaVersion": 1},
                "timestamp": "2026-08-02T12:00:00+09:00",
            },
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
    assert error.value.code == WorkerErrorCode.BACKEND_CONTRACT_ERROR.value


@pytest.mark.asyncio
async def test_input_auth_through_worker_does_not_fail_or_commit_terminal(
    caplog: pytest.LogCaptureFixture,
) -> None:
    worker, backend, input_port, generation = _worker()
    input_port.load_error = ReportInputError(
        WorkerErrorCode.BACKEND_AUTH_ERROR.value,
        retryable=False,
    )
    with caplog.at_level(logging.DEBUG):
        result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.BACKEND_REJECTED
    assert commit_decision_for(result.outcome) == CommitDecision.POLICY_PENDING
    assert backend.fail_calls == []
    assert backend.complete_calls == []
    assert generation.calls == 0
    text = "\n".join(record.getMessage() for record in caplog.records)
    assert "LEAK" not in text
    assert "Authorization" not in text


@pytest.mark.asyncio
async def test_normalize_unknown_runtime_error_is_retry_later() -> None:
    class BoomInput(FakeReportInput):
        def normalize(self, source):  # type: ignore[override]
            self.normalize_calls += 1
            raise RuntimeError("secret=NORMALIZE_SECRET boom")

    worker, backend, input_port, generation = _worker(
        input_port=BoomInput(normalized=sufficient_normalized())
    )
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.RETRY_LATER
    assert commit_decision_for(result.outcome) == CommitDecision.DO_NOT_COMMIT
    assert backend.fail_calls == []
    assert backend.complete_calls == []
    assert generation.calls == 0


@pytest.mark.asyncio
async def test_normalize_invalid_dto_is_terminal_failed() -> None:
    adapter = ReportInputHttpAdapter(
        client=httpx.AsyncClient(),
        base_url="http://backend",
        internal_token="tok",
    )
    with pytest.raises(ReportInputError) as error:
        adapter.normalize({"not": "a source"})
    assert error.value.code == WorkerErrorCode.NORMALIZATION_FAILED.value
    assert error.value.retryable is False

    worker, backend, input_port, generation = _worker()
    input_port.normalize_error = ReportInputError(
        WorkerErrorCode.NORMALIZATION_FAILED.value,
        retryable=False,
    )
    result = await worker.process(sample_payload())
    assert result.outcome == WorkerOutcome.TERMINAL_FAILED
    assert commit_decision_for(result.outcome) == CommitDecision.COMMIT
    assert backend.fail_calls
    assert generation.calls == 0


@pytest.mark.asyncio
async def test_adapter_normalize_unknown_exception_propagates() -> None:
    adapter = ReportInputHttpAdapter(
        client=httpx.AsyncClient(),
        base_url="http://backend",
        internal_token="tok",
    )

    class FakeSource:
        pass

    # Bypass DTO validate path by monkeypatching isinstance check via real source
    # and patching normalize_report_input.
    from app.adapters import report_input_http as module
    from app.schemas.report_input import ReportNormalizationSource
    from pathlib import Path
    import json

    payload = json.loads(
        (
            Path(__file__).resolve().parents[2]
            / "contracts"
            / "ai-004"
            / "report_normalization_source.json"
        ).read_text(encoding="utf-8")
    )
    source = ReportNormalizationSource.model_validate(payload)

    def boom(_source):
        raise RuntimeError("unexpected normalize bug secret=X")

    original = module.normalize_report_input
    module.normalize_report_input = boom  # type: ignore[assignment]
    try:
        with pytest.raises(RuntimeError, match="unexpected normalize bug"):
            adapter.normalize(source)
    finally:
        module.normalize_report_input = original  # type: ignore[assignment]
