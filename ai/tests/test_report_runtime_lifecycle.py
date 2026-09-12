"""Report worker settings / lifecycle tests (no external connections)."""

from __future__ import annotations

import asyncio
import os

import pytest
from fastapi import FastAPI

from app.config import ReportWorkerSettings


def test_import_main_does_not_connect(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "false")
    monkeypatch.setenv("STT_ENABLED", "false")
    monkeypatch.delenv("REPORT_INTERNAL_TOKEN", raising=False)
    # Re-import path: module already loaded; ensure settings stay disabled.
    from app.config import is_report_worker_enabled

    assert is_report_worker_enabled() is False


def test_disabled_settings_do_not_require_secrets(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "false")
    monkeypatch.delenv("REPORT_BACKEND_BASE_URL", raising=False)
    monkeypatch.delenv("REPORT_INTERNAL_TOKEN", raising=False)
    settings = ReportWorkerSettings.from_env()
    assert settings.enabled is False
    assert settings.internal_token == ""


def test_enabled_settings_require_token_and_backend(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "true")
    monkeypatch.setenv("REPORT_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    monkeypatch.setenv("REPORT_BACKEND_BASE_URL", "")
    monkeypatch.setenv("REPORT_INTERNAL_TOKEN", "")
    with pytest.raises(ValueError, match="missing required report worker"):
        ReportWorkerSettings.from_env()


def test_enabled_settings_ok(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "true")
    monkeypatch.setenv("REPORT_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    monkeypatch.setenv("REPORT_BACKEND_BASE_URL", "http://backend")
    monkeypatch.setenv("REPORT_INTERNAL_TOKEN", "tok")
    settings = ReportWorkerSettings.from_env()
    assert settings.enabled is True
    assert settings.kafka_topic == "field-visit.report.request.v1"
    assert settings.kafka_group_id == "ai.report-worker.v1"
    assert "tok" not in repr(settings)


def _enable_lifespan_workers(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("STT_ENABLED", "true")
    monkeypatch.setenv("STT_PROVIDER", "openai")
    monkeypatch.setenv("STT_MODEL", "whisper-1")
    monkeypatch.setenv("STT_OPENAI_API_KEY", "test-openai-stt-key")
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "true")
    monkeypatch.setenv("REPORT_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    monkeypatch.setenv("REPORT_BACKEND_BASE_URL", "http://backend")
    monkeypatch.setenv("REPORT_INTERNAL_TOKEN", "tok")
    monkeypatch.setenv("AI_REPORT_DB_ENABLED", "false")
    monkeypatch.setenv("RAG_ENABLED", "false")


@pytest.mark.asyncio
async def test_lifespan_preserves_normal_startup_and_shutdown_order(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _enable_lifespan_workers(monkeypatch)

    from app import main as main_module

    events: list[str] = []

    class FakeRuntime:
        def __init__(self, name: str) -> None:
            self.name = name

        async def start(self) -> None:
            events.append(f"{self.name}.start")

        async def stop(self) -> None:
            events.append(f"{self.name}.stop")

    stt_runtime = FakeRuntime("stt")
    report_runtime = FakeRuntime("report")
    monkeypatch.setattr(
        main_module,
        "create_stt_runtime",
        lambda settings: stt_runtime,
    )
    monkeypatch.setattr(
        main_module,
        "create_report_runtime",
        lambda settings: report_runtime,
    )

    test_app = FastAPI()
    async with main_module.lifespan(test_app):
        assert test_app.state.stt_runtime is stt_runtime
        assert test_app.state.report_runtime is report_runtime
        assert events == ["stt.start", "report.start"]

    assert events == [
        "stt.start",
        "report.start",
        "report.stop",
        "stt.stop",
    ]


@pytest.mark.asyncio
async def test_lifespan_stops_stt_even_when_report_stop_fails(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _enable_lifespan_workers(monkeypatch)

    from app import main as main_module

    events: list[str] = []

    class FakeSttRuntime:
        async def start(self) -> None:
            events.append("stt.start")

        async def stop(self) -> None:
            events.append("stt.stop")

    class FailingReportRuntime:
        async def start(self) -> None:
            events.append("report.start")

        async def stop(self) -> None:
            events.append("report.stop")
            raise RuntimeError("report runtime stop failed")

    monkeypatch.setattr(
        main_module,
        "create_stt_runtime",
        lambda settings: FakeSttRuntime(),
    )
    monkeypatch.setattr(
        main_module,
        "create_report_runtime",
        lambda settings: FailingReportRuntime(),
    )

    with pytest.raises(RuntimeError, match="report runtime stop failed"):
        async with main_module.lifespan(FastAPI()):
            pass

    assert events == [
        "stt.start",
        "report.start",
        "report.stop",
        "stt.stop",
    ]


@pytest.mark.asyncio
async def test_lifespan_stops_stt_when_report_runtime_creation_fails(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _enable_lifespan_workers(monkeypatch)

    from app import main as main_module

    events: list[str] = []

    class FakeSttRuntime:
        async def start(self) -> None:
            events.append("stt.start")

        async def stop(self) -> None:
            events.append("stt.stop")

    monkeypatch.setattr(
        main_module,
        "create_stt_runtime",
        lambda settings: FakeSttRuntime(),
    )

    def fail_report_runtime(settings: ReportWorkerSettings) -> None:
        events.append("report.create")
        raise RuntimeError("report runtime creation failed")

    monkeypatch.setattr(main_module, "create_report_runtime", fail_report_runtime)

    with pytest.raises(RuntimeError, match="report runtime creation failed"):
        async with main_module.lifespan(FastAPI()):
            pytest.fail("lifespan must not start the application")

    assert events == ["stt.start", "report.create", "stt.stop"]


@pytest.mark.asyncio
async def test_lifespan_cleans_partial_report_start_and_http_client(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _enable_lifespan_workers(monkeypatch)

    from app import main as main_module
    from app.messaging.report_runtime import ReportRuntime

    events: list[str] = []

    class FakeSttRuntime:
        async def start(self) -> None:
            events.append("stt.start")

        async def stop(self) -> None:
            events.append("stt.stop")

    class PartiallyStartedWorker:
        running = False

        async def start(self) -> None:
            events.append("report.start")
            self.running = True
            raise RuntimeError("report runtime start failed")

        async def stop(self) -> None:
            events.append("report.stop")
            self.running = False

        def is_running(self) -> bool:
            return self.running

    class FakeHttpClient:
        closed = False

        async def aclose(self) -> None:
            events.append("http.close")
            self.closed = True

    worker = PartiallyStartedWorker()
    http_client = FakeHttpClient()
    report_runtime = ReportRuntime(
        settings=ReportWorkerSettings.from_env(),
        worker=worker,  # type: ignore[arg-type]
        http_client=http_client,  # type: ignore[arg-type]
    )
    monkeypatch.setattr(
        main_module,
        "create_stt_runtime",
        lambda settings: FakeSttRuntime(),
    )
    monkeypatch.setattr(
        main_module,
        "create_report_runtime",
        lambda settings: report_runtime,
    )

    with pytest.raises(RuntimeError, match="report runtime start failed"):
        async with main_module.lifespan(FastAPI()):
            pytest.fail("lifespan must not start the application")

    assert worker.running is False
    assert http_client.closed is True
    # Partial-start cleanup closes worker/http; lifespan finally closes HTTP again safely.
    assert events == [
        "stt.start",
        "report.start",
        "report.stop",
        "http.close",
        "http.close",
        "stt.stop",
    ]


@pytest.mark.asyncio
async def test_health_returns_503_when_report_worker_blocked(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "false")
    monkeypatch.setenv("STT_ENABLED", "false")
    monkeypatch.setenv("AI_REPORT_DB_ENABLED", "false")
    monkeypatch.setenv("RAG_ENABLED", "false")

    from fastapi.testclient import TestClient

    from app.main import app
    from app.messaging.kafka_report import ReportKafkaWorker, ReportWorkerHealth
    from app.messaging.report_runtime import ReportRuntime
    from app.config import ReportWorkerSettings
    import httpx

    class UnhealthyWorker:
        def is_healthy(self) -> bool:
            return False

        def is_running(self) -> bool:
            return True

        async def start(self) -> None:
            return None

        async def stop(self) -> None:
            return None

        health = ReportWorkerHealth(
            enabled=True,
            running=True,
            consumer_started=True,
            blocked_partitions=["field-visit.report.request.v1:0"],
            last_error_code="RUNTIME_UNEXPECTED",
            assigned_partitions=["field-visit.report.request.v1:0"],
        )

    settings = ReportWorkerSettings(
        enabled=True,
        kafka_bootstrap_servers="localhost:9092",
        kafka_topic="field-visit.report.request.v1",
        kafka_group_id="ai.report-worker.v1",
        kafka_dlt_topic="field-visit.report.request.dlq.v1",
        kafka_rejection_policy="block",
        kafka_dlt_retention_ms=604_800_000,
        kafka_auto_offset_reset="earliest",
        kafka_max_poll_interval_ms=1_800_000,
        retry_backoff_seconds=2.0,
        backend_base_url="http://backend",
        backend_timeout_seconds=15.0,
        internal_token="tok",
    )
    runtime = ReportRuntime(
        settings=settings,
        worker=UnhealthyWorker(),  # type: ignore[arg-type]
        http_client=httpx.AsyncClient(),
    )

    with TestClient(app) as client:
        client.app.state.report_runtime = runtime
        # Force health() to see enabled worker via env during request.
        monkeypatch.setenv("REPORT_WORKER_ENABLED", "true")
        monkeypatch.setenv("REPORT_BACKEND_BASE_URL", "http://backend")
        monkeypatch.setenv("REPORT_INTERNAL_TOKEN", "tok")
        response = client.get("/health")
    assert response.status_code == 503
    body = response.json()
    assert body["reportWorkerHealthy"] is False
    assert body["status"] == "degraded"
    await runtime.stop()


@pytest.mark.asyncio
async def test_create_runtime_with_fake_consumer_no_broker(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "true")
    monkeypatch.setenv("REPORT_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    monkeypatch.setenv("REPORT_BACKEND_BASE_URL", "http://backend")
    monkeypatch.setenv("REPORT_INTERNAL_TOKEN", "tok")
    settings = ReportWorkerSettings.from_env()

    from app.messaging.report_runtime import create_report_runtime
    from tests.fixtures.report_worker.builders import (
        claimed_generation,
        sample_evidence_result,
        sufficient_normalized,
    )
    from tests.fixtures.report_worker.fakes import (
        FakeEvidenceService,
        FakeGenerationService,
    )
    from tests.support.fake_kafka_consumer import FakeKafkaConsumer

    source = sufficient_normalized()
    runtime = create_report_runtime(
        settings,
        generation=FakeGenerationService(results=[claimed_generation(source)]),
        evidence=FakeEvidenceService(results=[sample_evidence_result()]),
        consumer=FakeKafkaConsumer(records=[]),
    )
    report_worker = runtime._worker._handler._worker
    assert report_worker._sleeper is asyncio.sleep
    assert report_worker._provider_retry_delay_seconds == settings.retry_backoff_seconds
    await runtime.start()
    assert runtime.is_healthy() is True
    snap = runtime.health_snapshot()
    assert snap.enabled is True
    assert snap.consumer_started is True
    assert snap.rejection_policy == "block"
    assert snap.dlt_producer_started is False
    await runtime.stop()
    # second stop is safe
    await runtime.stop()


@pytest.mark.asyncio
async def test_create_runtime_dlt_policy_starts_producer(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "true")
    monkeypatch.setenv("REPORT_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    monkeypatch.setenv("REPORT_BACKEND_BASE_URL", "http://backend")
    monkeypatch.setenv("REPORT_INTERNAL_TOKEN", "tok")
    monkeypatch.setenv("REPORT_KAFKA_REJECTION_POLICY", "dlt")
    settings = ReportWorkerSettings.from_env()

    from app.messaging.report_runtime import create_report_runtime
    from tests.fixtures.report_worker.builders import (
        claimed_generation,
        sample_evidence_result,
        sufficient_normalized,
    )
    from tests.fixtures.report_worker.fakes import (
        FakeEvidenceService,
        FakeGenerationService,
    )
    from tests.support.fake_kafka_consumer import FakeKafkaConsumer

    class FakeDltProducer:
        def __init__(self) -> None:
            self.started = False
            self.stopped = False
            self.flushed = False

        async def start(self) -> None:
            self.started = True

        async def flush(self) -> None:
            self.flushed = True

        async def stop(self) -> None:
            self.stopped = True

    source = sufficient_normalized()
    producer = FakeDltProducer()
    runtime = create_report_runtime(
        settings,
        generation=FakeGenerationService(results=[claimed_generation(source)]),
        evidence=FakeEvidenceService(results=[sample_evidence_result()]),
        consumer=FakeKafkaConsumer(records=[]),
        dlt_producer=producer,
    )
    await runtime.start()
    assert producer.started is True
    snap = runtime.health_snapshot()
    assert snap.rejection_policy == "dlt"
    assert snap.dlt_producer_started is True
    assert snap.dlt_topic == "field-visit.report.request.dlq.v1"
    assert runtime.is_healthy() is True
    await runtime.stop()
    assert producer.flushed is True
    assert producer.stopped is True


@pytest.mark.asyncio
async def test_health_exposes_dlt_fields_after_publish(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("REPORT_WORKER_ENABLED", "false")
    monkeypatch.setenv("STT_ENABLED", "false")
    monkeypatch.setenv("AI_REPORT_DB_ENABLED", "false")
    monkeypatch.setenv("RAG_ENABLED", "false")

    from fastapi.testclient import TestClient

    import httpx

    from app.config import ReportWorkerSettings
    from app.main import app
    from app.messaging.kafka_report import ReportWorkerHealth
    from app.messaging.report_runtime import ReportRuntime

    class HealthyWorker:
        def is_healthy(self) -> bool:
            return True

        def is_running(self) -> bool:
            return True

        async def start(self) -> None:
            return None

        async def stop(self) -> None:
            return None

        health = ReportWorkerHealth(
            enabled=True,
            running=True,
            consumer_started=True,
            last_dlt_at="2026-08-02T10:00:00+00:00",
            dlt_publish_count=2,
            dlt_failure_count=0,
            rejection_policy="dlt",
            dlt_topic="field-visit.report.request.dlq.v1",
            dlt_producer_started=True,
        )

    settings = ReportWorkerSettings(
        enabled=True,
        kafka_bootstrap_servers="localhost:9092",
        kafka_topic="field-visit.report.request.v1",
        kafka_group_id="ai.report-worker.v1",
        kafka_dlt_topic="field-visit.report.request.dlq.v1",
        kafka_rejection_policy="dlt",
        kafka_dlt_retention_ms=604_800_000,
        kafka_auto_offset_reset="earliest",
        kafka_max_poll_interval_ms=1_800_000,
        retry_backoff_seconds=2.0,
        backend_base_url="http://backend",
        backend_timeout_seconds=15.0,
        internal_token="tok",
    )
    runtime = ReportRuntime(
        settings=settings,
        worker=HealthyWorker(),  # type: ignore[arg-type]
        http_client=httpx.AsyncClient(),
        dlt_producer=object(),
    )
    runtime._dlt_started = True

    with TestClient(app) as client:
        client.app.state.report_runtime = runtime
        monkeypatch.setenv("REPORT_WORKER_ENABLED", "true")
        monkeypatch.setenv("REPORT_BACKEND_BASE_URL", "http://backend")
        monkeypatch.setenv("REPORT_INTERNAL_TOKEN", "tok")
        response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["reportDltPublishCount"] == 2
    assert body["reportLastDltAt"] == "2026-08-02T10:00:00+00:00"
    assert body["reportRejectionPolicy"] == "dlt"
    assert body["reportDltTopic"] == "field-visit.report.request.dlq.v1"
    await runtime.stop()
