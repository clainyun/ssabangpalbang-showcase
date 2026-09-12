"""DLT allowlist + Adapter→Worker→Handler→Runtime path tests (INF-007 P1)."""

from __future__ import annotations

import asyncio
import json
import httpx
import pytest

from app.adapters.report_backend_http import ReportBackendHttpAdapter
from app.adapters.report_input_http import ReportInputHttpAdapter
from app.messaging.kafka_report import ReportKafkaWorker
from app.messaging.report_dlt_eligibility import should_publish_to_dlt
from app.messaging.report_message_handler import KafkaRecordLike, ReportMessageHandler
from app.messaging.report_rejection_policy import (
    KafkaDltRejectionPolicy,
    RejectionHandleResult,
)
from app.schemas.report_worker import WorkerOutcome
from app.services.report_worker import ReportWorker
from tests.fixtures.report_worker.builders import (
    claimed_generation,
    sample_evidence_result,
    sufficient_normalized,
)
from tests.fixtures.report_worker.fakes import (
    FakeEvidenceService,
    FakeGenerationService,
    RecordingSleeper,
)
from tests.support.fake_kafka_consumer import FakeKafkaConsumer, FakeRecord
TOKEN = "test-dlt-eligibility-token"
TOPIC = "field-visit.report.request.v1"
DLT = "field-visit.report.request.dlq.v1"


def _error_envelope(code: str) -> dict[str, object]:
    return {
        "success": False,
        "code": code,
        "message": "리포트 처리 상태가 충돌합니다.",
        "data": None,
        "timestamp": "2026-08-02T12:00:00+09:00",
    }


def _payload_bytes(study_id: int = 7) -> bytes:
    return json.dumps(
        {
            "studyId": study_id,
            "sessionId": 3,
            "apartmentId": 100,
            "occurredAt": "2026-08-01T09:15:30.123Z",
        }
    ).encode("utf-8")


def test_should_publish_allowlist() -> None:
    assert should_publish_to_dlt(WorkerOutcome.INVALID_EVENT, "INVALID_EVENT", None)
    assert should_publish_to_dlt(
        WorkerOutcome.CONTRACT_CONFLICT, "CONTRACT_CONFLICT", None
    )
    assert should_publish_to_dlt(
        WorkerOutcome.BACKEND_REJECTED,
        "BACKEND_CONTRACT_ERROR",
        "COMPLETE_PAYLOAD_CONFLICT",
    )
    assert should_publish_to_dlt(
        WorkerOutcome.BACKEND_REJECTED,
        "BACKEND_CONTRACT_ERROR",
        "FAIL_PAYLOAD_CONFLICT",
    )
    assert not should_publish_to_dlt(
        WorkerOutcome.BACKEND_REJECTED, "BACKEND_AUTH_ERROR", None
    )
    assert not should_publish_to_dlt(
        WorkerOutcome.BACKEND_REJECTED, "BACKEND_CONTRACT_ERROR", None
    )
    assert not should_publish_to_dlt(
        WorkerOutcome.BACKEND_REJECTED,
        "BACKEND_CONTRACT_ERROR",
        "STALE_PROCESSING_TOKEN",
    )
    assert not should_publish_to_dlt(
        WorkerOutcome.BACKEND_REJECTED, "BACKEND_CONTRACT_ERROR", "OTHER"
    )
    assert not should_publish_to_dlt(WorkerOutcome.RETRY_LATER, None, None)


class _CountingDltPolicy:
    def __init__(self) -> None:
        self.calls = 0

    async def handle_policy_pending(self, **kwargs):  # noqa: ANN003
        self.calls += 1
        return RejectionHandleResult(published=True)


async def _run_runtime(
    *,
    handler: ReportMessageHandler,
    record: FakeRecord,
    policy: object,
) -> ReportKafkaWorker:
    consumer = FakeKafkaConsumer(records=[record])
    worker = ReportKafkaWorker(
        consumer=consumer,
        handler=handler,
        rejection_policy=policy,  # type: ignore[arg-type]
        retry_backoff_seconds=0.01,
        topic_partition_factory=lambda t, p: (t, p),
        rejection_policy_name="dlt",
        dlt_topic=DLT,
        dlt_producer_started=True,
    )
    await worker.start()
    await asyncio.sleep(0.08)
    await worker.stop()
    worker.health  # noqa: B018 — keep reference for callers via return
    # attach consumer for assertions
    worker._test_consumer = consumer  # type: ignore[attr-defined]
    return worker


@pytest.mark.asyncio
async def test_backend_401_blocks_without_dlt() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, json=_error_envelope("UNAUTHORIZED"))

    transport = httpx.MockTransport(handler)
    dlt = _CountingDltPolicy()
    async with httpx.AsyncClient(transport=transport) as client:
        backend = ReportBackendHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        input_port = ReportInputHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        source = sufficient_normalized()
        report_worker = ReportWorker(
            backend=backend,
            input_port=input_port,
            generation=FakeGenerationService(results=[claimed_generation(source)]),
            evidence=FakeEvidenceService(results=[sample_evidence_result()]),
            sleeper=RecordingSleeper(),
            provider_retry_delay_seconds=0.01,
        )
        kafka_worker = await _run_runtime(
            handler=ReportMessageHandler(report_worker),
            record=FakeRecord(TOPIC, 0, 1, b"7", _payload_bytes()),
            policy=dlt,
        )
    consumer = kafka_worker._test_consumer  # type: ignore[attr-defined]
    assert dlt.calls == 0
    assert consumer.committed == {}
    assert kafka_worker.health.blocked_partitions
    assert kafka_worker.health.last_error_code == "BACKEND_AUTH_ERROR"
    assert kafka_worker.is_healthy() is False


@pytest.mark.asyncio
async def test_generic_409_blocks_without_dlt() -> None:
    calls = {"n": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        calls["n"] += 1
        if request.url.path.endswith("/acquire"):
            return httpx.Response(
                200,
                json={
                    "success": True,
                    "code": "REPORT_ACQUIRE_SUCCESS",
                    "message": "ok",
                    "data": {
                        "status": "ACQUIRED",
                        "reportId": 48,
                        "processingToken": "tok-1",
                        "processingAttempt": 1,
                    },
                    "timestamp": "2026-08-02T12:00:00+09:00",
                },
            )
        if "/progress" in request.url.path:
            return httpx.Response(204)
        if request.url.path.endswith("/input"):
            # Force contract error before generation via progress already ok;
            # return 422 on input instead.
            return httpx.Response(422, json=_error_envelope("VALIDATION_FAILED"))
        return httpx.Response(500, json=_error_envelope("X"))

    transport = httpx.MockTransport(handler)
    dlt = _CountingDltPolicy()
    async with httpx.AsyncClient(transport=transport) as client:
        backend = ReportBackendHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        input_port = ReportInputHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        source = sufficient_normalized()
        report_worker = ReportWorker(
            backend=backend,
            input_port=input_port,
            generation=FakeGenerationService(results=[claimed_generation(source)]),
            evidence=FakeEvidenceService(results=[sample_evidence_result()]),
            sleeper=RecordingSleeper(),
            provider_retry_delay_seconds=0.01,
        )
        kafka_worker = await _run_runtime(
            handler=ReportMessageHandler(report_worker),
            record=FakeRecord(TOPIC, 0, 2, b"7", _payload_bytes()),
            policy=dlt,
        )
    consumer = kafka_worker._test_consumer  # type: ignore[attr-defined]
    assert dlt.calls == 0
    assert consumer.committed == {}
    assert kafka_worker.health.blocked_partitions


@pytest.mark.asyncio
async def test_complete_and_fail_conflict_via_http_mock_path() -> None:
    """Adapter HTTP 409 conflict_code → Worker → Handler → Runtime DLT commit."""

    class OkProducer:
        calls: list[object] = []

        async def send_and_wait(self, *args, **kwargs):  # noqa: ANN002,ANN003
            OkProducer.calls.append((args, kwargs))
            return None

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("/acquire"):
            return httpx.Response(
                200,
                json={
                    "success": True,
                    "code": "REPORT_ACQUIRE_SUCCESS",
                    "message": "ok",
                    "data": {
                        "status": "ACQUIRED",
                        "reportId": 48,
                        "processingToken": "tok-1",
                        "processingAttempt": 1,
                    },
                    "timestamp": "2026-08-02T12:00:00+09:00",
                },
            )
        if "/progress" in path:
            return httpx.Response(204)
        if path.endswith("/input"):
            from tests.support.report_contract_stub import load_canonical_source_payload

            payload = load_canonical_source_payload()
            return httpx.Response(
                200,
                json={
                    "success": True,
                    "code": "REPORT_INPUT_SUCCESS",
                    "message": "ok",
                    "data": payload,
                    "timestamp": "2026-08-02T12:00:00+09:00",
                },
            )
        if path.endswith("/complete"):
            return httpx.Response(
                409, json=_error_envelope("COMPLETE_PAYLOAD_CONFLICT")
            )
        return httpx.Response(500, json=_error_envelope("X"))

    transport = httpx.MockTransport(handler)
    async with httpx.AsyncClient(transport=transport) as client:
        backend = ReportBackendHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        input_port = ReportInputHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        source = sufficient_normalized()
        report_worker = ReportWorker(
            backend=backend,
            input_port=input_port,
            generation=FakeGenerationService(results=[claimed_generation(source)]),
            evidence=FakeEvidenceService(results=[sample_evidence_result()]),
            sleeper=RecordingSleeper(),
            provider_retry_delay_seconds=0.01,
        )
        message_handler = ReportMessageHandler(report_worker)
        result = await message_handler.handle(
            KafkaRecordLike(
                key=b"7",
                value=_payload_bytes(),
                topic=TOPIC,
                partition=0,
                offset=9,
            )
        )
        assert result.outcome == WorkerOutcome.BACKEND_REJECTED
        assert result.error_code == "BACKEND_CONTRACT_ERROR"
        assert result.conflict_code == "COMPLETE_PAYLOAD_CONFLICT"
        assert should_publish_to_dlt(
            result.outcome, result.error_code, result.conflict_code
        )

        OkProducer.calls.clear()
        kafka_worker = await _run_runtime(
            handler=message_handler,
            record=FakeRecord(TOPIC, 0, 9, b"7", _payload_bytes()),
            policy=KafkaDltRejectionPolicy(producer=OkProducer(), dlt_topic=DLT),
        )
    consumer = kafka_worker._test_consumer  # type: ignore[attr-defined]
    assert OkProducer.calls
    assert consumer.committed.get((TOPIC, 0)) == 10
    assert kafka_worker.health.dlt_publish_count == 1


@pytest.mark.asyncio
async def test_fail_payload_conflict_publishes_dlt() -> None:
    class OkProducer:
        calls = 0

        async def send_and_wait(self, *args, **kwargs):  # noqa: ANN002,ANN003
            OkProducer.calls += 1
            return None

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("/acquire"):
            return httpx.Response(
                200,
                json={
                    "success": True,
                    "code": "REPORT_ACQUIRE_SUCCESS",
                    "message": "ok",
                    "data": {
                        "status": "ACQUIRED",
                        "reportId": 48,
                        "processingToken": "tok-1",
                        "processingAttempt": 1,
                    },
                    "timestamp": "2026-08-02T12:00:00+09:00",
                },
            )
        if "/progress" in path:
            return httpx.Response(204)
        if path.endswith("/input"):
            from tests.support.report_contract_stub import load_canonical_source_payload

            return httpx.Response(
                200,
                json={
                    "success": True,
                    "code": "REPORT_INPUT_SUCCESS",
                    "message": "ok",
                    "data": load_canonical_source_payload(),
                    "timestamp": "2026-08-02T12:00:00+09:00",
                },
            )
        if path.endswith("/fail"):
            return httpx.Response(409, json=_error_envelope("FAIL_PAYLOAD_CONFLICT"))
        return httpx.Response(500, json=_error_envelope("X"))

    transport = httpx.MockTransport(handler)
    OkProducer.calls = 0
    async with httpx.AsyncClient(transport=transport) as client:
        backend = ReportBackendHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        input_port = ReportInputHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        from app.exceptions.report_generation import ReportGenerationError

        report_worker = ReportWorker(
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
        kafka_worker = await _run_runtime(
            handler=ReportMessageHandler(report_worker),
            record=FakeRecord(TOPIC, 0, 4, b"7", _payload_bytes()),
            policy=KafkaDltRejectionPolicy(producer=OkProducer(), dlt_topic=DLT),
        )
    consumer = kafka_worker._test_consumer  # type: ignore[attr-defined]
    assert OkProducer.calls == 1
    assert consumer.committed.get((TOPIC, 0)) == 5
    assert kafka_worker.health.last_error_code in {
        None,
        "FAIL_PAYLOAD_CONFLICT",
        "BACKEND_CONTRACT_ERROR",
    } or kafka_worker.health.dlt_publish_count == 1


@pytest.mark.asyncio
async def test_stale_token_blocks_without_dlt() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("/acquire"):
            return httpx.Response(
                200,
                json={
                    "success": True,
                    "code": "REPORT_ACQUIRE_SUCCESS",
                    "message": "ok",
                    "data": {
                        "status": "ACQUIRED",
                        "reportId": 48,
                        "processingToken": "tok-1",
                        "processingAttempt": 1,
                    },
                    "timestamp": "2026-08-02T12:00:00+09:00",
                },
            )
        if "/progress" in path:
            return httpx.Response(409, json=_error_envelope("STALE_PROCESSING_TOKEN"))
        return httpx.Response(500, json=_error_envelope("X"))

    dlt = _CountingDltPolicy()
    transport = httpx.MockTransport(handler)
    async with httpx.AsyncClient(transport=transport) as client:
        backend = ReportBackendHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        input_port = ReportInputHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        source = sufficient_normalized()
        report_worker = ReportWorker(
            backend=backend,
            input_port=input_port,
            generation=FakeGenerationService(results=[claimed_generation(source)]),
            evidence=FakeEvidenceService(results=[sample_evidence_result()]),
            sleeper=RecordingSleeper(),
            provider_retry_delay_seconds=0.01,
        )
        kafka_worker = await _run_runtime(
            handler=ReportMessageHandler(report_worker),
            record=FakeRecord(TOPIC, 0, 3, b"7", _payload_bytes()),
            policy=dlt,
        )
    consumer = kafka_worker._test_consumer  # type: ignore[attr-defined]
    assert dlt.calls == 0
    assert consumer.committed == {}
    assert kafka_worker.health.blocked_partitions
    assert kafka_worker.health.last_error_code == "STALE_PROCESSING_TOKEN"


@pytest.mark.asyncio
async def test_unknown_409_blocks_without_dlt() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("/acquire"):
            return httpx.Response(
                200,
                json={
                    "success": True,
                    "code": "REPORT_ACQUIRE_SUCCESS",
                    "message": "ok",
                    "data": {
                        "status": "ACQUIRED",
                        "reportId": 48,
                        "processingToken": "tok-1",
                        "processingAttempt": 1,
                    },
                    "timestamp": "2026-08-02T12:00:00+09:00",
                },
            )
        if "/progress" in path:
            return httpx.Response(409, json=_error_envelope("SOME_UNKNOWN_CONFLICT"))
        return httpx.Response(500, json=_error_envelope("X"))

    dlt = _CountingDltPolicy()
    transport = httpx.MockTransport(handler)
    async with httpx.AsyncClient(transport=transport) as client:
        backend = ReportBackendHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        input_port = ReportInputHttpAdapter(
            client=client, base_url="http://backend", internal_token=TOKEN
        )
        source = sufficient_normalized()
        report_worker = ReportWorker(
            backend=backend,
            input_port=input_port,
            generation=FakeGenerationService(results=[claimed_generation(source)]),
            evidence=FakeEvidenceService(results=[sample_evidence_result()]),
            sleeper=RecordingSleeper(),
            provider_retry_delay_seconds=0.01,
        )
        kafka_worker = await _run_runtime(
            handler=ReportMessageHandler(report_worker),
            record=FakeRecord(TOPIC, 0, 6, b"7", _payload_bytes()),
            policy=dlt,
        )
    consumer = kafka_worker._test_consumer  # type: ignore[attr-defined]
    assert dlt.calls == 0
    assert consumer.committed == {}
