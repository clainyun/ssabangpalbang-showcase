"""FastAPI entrypoint for field-visit AI workloads."""

import logging
import os
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, Request, Response, status

from app.api.chatbot import router as chatbot_router
from app.api.checklist import router as checklist_router
from app.api.rag_index import router as rag_index_router
from app.config import (
    ReportDatabaseSettings,
    ReportWorkerSettings,
    SttSettings,
    is_report_database_enabled,
    load_ai_env,
)
from app.messaging.report_runtime import ReportRuntime, create_report_runtime
from app.messaging.stt_runtime import SttRuntime, create_stt_runtime
from app.rag.config import RagSettings, is_rag_enabled

load_ai_env()
logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    if is_report_database_enabled():
        ReportDatabaseSettings.from_env()
    if is_rag_enabled():
        RagSettings.from_env()

    settings = SttSettings.from_env()
    runtime: SttRuntime | None = None
    if settings.enabled:
        runtime = create_stt_runtime(settings)
        await runtime.start()
        app.state.stt_runtime = runtime

    report_runtime: ReportRuntime | None = None
    try:
        report_settings = ReportWorkerSettings.from_env()
        if report_settings.enabled:
            report_runtime = create_report_runtime(report_settings)
            await report_runtime.start()
            app.state.report_runtime = report_runtime
        yield
    finally:
        try:
            if report_runtime is not None:
                await report_runtime.stop()
        finally:
            if runtime is not None:
                await runtime.stop()


app = FastAPI(
    title="Ssabangpalbang Field Visit AI",
    version="0.2.0",
    description="Internal AI service for field-visit checklists and Kafka STT.",
    lifespan=lifespan,
)

app.include_router(checklist_router)
app.include_router(chatbot_router)
app.include_router(rag_index_router)


@app.get("/health")
def health(
    request: Request,
    response: Response,
) -> dict[str, object]:
    settings = SttSettings.from_env()
    runtime = getattr(request.app.state, "stt_runtime", None)
    stt_healthy = (
        not settings.enabled
        or (runtime is not None and runtime.is_healthy())
    )

    report_settings = ReportWorkerSettings.from_env()
    report_runtime = getattr(request.app.state, "report_runtime", None)
    report_healthy = (
        not report_settings.enabled
        or (report_runtime is not None and report_runtime.is_healthy())
    )
    report_health = (
        report_runtime.health_snapshot()
        if report_runtime is not None
        else None
    )

    overall_ok = stt_healthy and report_healthy
    if not overall_ok:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    payload: dict[str, object] = {
        "status": "ok" if overall_ok else "degraded",
        "sttEnabled": settings.enabled,
        "sttHealthy": stt_healthy,
        "reportDbEnabled": is_report_database_enabled(),
        "ragEnabled": is_rag_enabled(),
        "reportWorkerEnabled": report_settings.enabled,
        "reportWorkerHealthy": report_healthy,
    }
    if report_health is not None:
        payload.update(
            {
                "reportWorkerRunning": report_health.running,
                "reportConsumerStarted": report_health.consumer_started,
                "reportAssignedPartitions": report_health.assigned_partitions,
                "reportBlockedPartitions": report_health.blocked_partitions,
                "reportLastPollAt": report_health.last_poll_at,
                "reportLastSuccessAt": report_health.last_success_at,
                "reportLastErrorCode": report_health.last_error_code,
                "reportAttemptedStage": report_health.attempted_stage,
                "reportLastCompletedStage": report_health.last_completed_stage,
                "reportLastDltAt": report_health.last_dlt_at,
                "reportDltPublishCount": report_health.dlt_publish_count,
                "reportDltFailureCount": report_health.dlt_failure_count,
                "reportRejectionPolicy": report_health.rejection_policy,
                "reportDltTopic": report_health.dlt_topic,
            }
        )
    return payload
