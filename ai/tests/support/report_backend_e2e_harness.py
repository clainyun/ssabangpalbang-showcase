"""Lifecycle helpers for INF-007 real Backend + PG + Kafka E2E."""

from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import os
import secrets
import shutil
import socket
import subprocess
import time
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable

import httpx

from app.adapters.report_backend_http import ReportBackendHttpAdapter
from app.adapters.report_input_http import ReportInputHttpAdapter
from app.exceptions.report_generation import ReportGenerationError
from app.messaging.kafka_report import ReportKafkaWorker
from app.messaging.report_message_handler import ReportMessageHandler
from app.messaging.report_rejection_policy import KafkaDltRejectionPolicy
from app.schemas.report_evidence import (
    ClaimType,
    EvidenceClaim,
    EvidenceLinkRequest,
    EvidenceLinkResult,
    EvidenceRef,
    EvidenceRole,
)
from app.schemas.report_generation import (
    OpinionType,
    ReportCategory,
    ReportFeature,
    ReportGenerationResult,
)
from app.schemas.report_input import NormalizedReportInput, ReportSourceType
from app.schemas.report_worker import WorkerErrorCode
from app.services.report_generation import (
    collect_usable_sources,
    compute_metrics,
    ordered_categories,
)
from app.services.report_worker import ReportWorker
from tests.fixtures.report_worker.fakes import RecordingSleeper
from tests.support.report_backend_e2e_isolation import (
    BackendE2EIsolationManifest,
    assert_backend_e2e_isolation_safe,
)

logger = logging.getLogger(__name__)

REPO_ROOT = Path(__file__).resolve().parents[3]
COMPOSE_FILE = REPO_ROOT / "infra" / "docker-compose.report-backend-e2e.yml"
BACKEND_DIR = REPO_ROOT / "backend"
KST = timezone(timedelta(hours=9))
OCCURRED_AT = "2026-08-02T13:30:00+09:00"
TEST_TOKEN = "inf007-backend-e2e-test-token-only"
# Fixed test-only Base64 key (>=256-bit after decode). Do not use ops secrets.
TEST_JWT = (
    "aW5mMDA3LWJhY2tlbmQtZTJlLXRlc3Qtb25seS1qd3Qtc2VjcmV0LWtleS0zMi1ieXRlcyEh"
)


@dataclass(frozen=True)
class FixtureIds:
    label: str
    study_id: int
    session_id: int
    apartment_id: int
    member_id: int
    checklist_item_id: int
    field_record_id: int


@dataclass(frozen=True)
class JarBuildEvidence:
    path: Path
    exit_code: int
    sha256: str
    mtime_iso: str
    boot_jar_ran: bool


@dataclass
class StackHandles:
    manifest: BackendE2EIsolationManifest
    env_path: Path
    env: dict[str, str]
    jar_path: Path
    jar_evidence: JarBuildEvidence


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def _pick_ports() -> tuple[int, int, int, int]:
    ports: set[int] = set()
    while len(ports) < 4:
        port = _free_port()
        if port in {5432, 6379, 8080, 9092, 18081, 19092, 19192, 19293}:
            continue
        ports.add(port)
    ordered = sorted(ports)
    return ordered[0], ordered[1], ordered[2], ordered[3]


def require_docker() -> None:
    if shutil.which("docker") is None:
        raise RuntimeError("REPORT_BACKEND_E2E_DOCKER_MISSING")
    probe = subprocess.run(
        ["docker", "info"],
        capture_output=True,
        text=True,
        check=False,
    )
    if probe.returncode != 0:
        raise RuntimeError("REPORT_BACKEND_E2E_DOCKER_UNAVAILABLE")


def ensure_backend_jar() -> JarBuildEvidence:
    """Always rebuild bootJar from the current working tree.

    Existing jars must never short-circuit Gradle: a stale pre-BE-019 jar
    would make Backend E2E pass against the wrong binary.
    """

    gradlew = BACKEND_DIR / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not gradlew.exists():
        raise RuntimeError("REPORT_BACKEND_E2E_GRADLEW_MISSING")
    # --rerun-tasks: never trust an existing bootJar output as current.
    # clean is intentionally not used (keeps build cache for dependencies).
    cmd = [str(gradlew), "bootJar", "-x", "test", "--no-daemon", "--rerun-tasks"]
    started = time.time()
    completed = subprocess.run(
        cmd,
        cwd=BACKEND_DIR,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if completed.returncode != 0:
        logger.error(
            "bootJar failed exit=%s stderr=%s",
            completed.returncode,
            (completed.stderr or "")[-2000:],
        )
        raise RuntimeError("REPORT_BACKEND_E2E_JAR_BUILD_FAILED")
    runnable = [
        p
        for p in sorted((BACKEND_DIR / "build" / "libs").glob("*.jar"))
        if not p.name.endswith("-plain.jar")
    ]
    if not runnable:
        raise RuntimeError("REPORT_BACKEND_E2E_JAR_MISSING")
    jar_path = runnable[-1]
    mtime = jar_path.stat().st_mtime
    if mtime + 1.0 < started:
        raise RuntimeError("REPORT_BACKEND_E2E_JAR_STALE_AFTER_BOOTJAR")
    digest = hashlib.sha256(jar_path.read_bytes()).hexdigest()
    mtime_iso = datetime.fromtimestamp(mtime, tz=KST).isoformat()
    evidence = JarBuildEvidence(
        path=jar_path,
        exit_code=completed.returncode,
        sha256=digest,
        mtime_iso=mtime_iso,
        boot_jar_ran=True,
    )
    logger.info(
        "INF-007 Backend E2E bootJar ok elapsed=%.1fs jar=%s sha256=%s mtime=%s",
        time.time() - started,
        jar_path,
        digest,
        mtime_iso,
    )
    return evidence


def build_manifest(suffix: str, ports: tuple[int, int, int, int]) -> BackendE2EIsolationManifest:
    db_port, redis_port, kafka_port, backend_port = ports
    del redis_port  # host redis port is in compose env; not asserted beyond uniqueness
    return BackendE2EIsolationManifest(
        compose_file=str(COMPOSE_FILE.relative_to(REPO_ROOT)).replace("\\", "/"),
        compose_project=f"inf007-report-backend-e2e-{suffix}",
        services=("postgres-e2e", "redis-e2e", "kafka-e2e", "backend-e2e"),
        volumes=("postgres-e2e-data", "kafka-e2e-data"),
        db_host="127.0.0.1",
        db_port=db_port,
        db_name=f"inf007_report_e2e_{suffix}",
        db_volume=f"inf007-report-backend-e2e-pg-{suffix}",
        backend_url=f"http://127.0.0.1:{backend_port}",
        backend_port=backend_port,
        kafka_bootstrap=f"127.0.0.1:{kafka_port}",
        kafka_port=kafka_port,
        request_topic=f"field-visit.report.request.v1.e2e.{suffix}",
        dlt_topic=f"field-visit.report.request.dlq.v1.e2e.{suffix}",
        consumer_group=f"ai.report-worker.backend-e2e.{suffix}",
        token_is_test_only=True,
        contains_ops_address=False,
        uses_ssabangpalbang_resources=False,
        uses_production_volume=False,
        unique_suffix=suffix,
    )


def write_env_file(manifest: BackendE2EIsolationManifest, jar_path: Path, ports: tuple[int, int, int, int]) -> tuple[Path, dict[str, str]]:
    db_port, redis_port, kafka_port, backend_port = ports
    env = {
        "REPORT_BACKEND_E2E_DB_NAME": manifest.db_name,
        "REPORT_BACKEND_E2E_DB_USER": "inf007_e2e",
        "REPORT_BACKEND_E2E_DB_PASSWORD": "inf007_e2e_password_only",
        "REPORT_BACKEND_E2E_DB_PORT": str(db_port),
        "REPORT_BACKEND_E2E_REDIS_PORT": str(redis_port),
        "REPORT_BACKEND_E2E_REDIS_PASSWORD": "inf007_e2e_redis_only",
        "REPORT_BACKEND_E2E_KAFKA_PORT": str(kafka_port),
        "REPORT_BACKEND_E2E_BACKEND_PORT": str(backend_port),
        "REPORT_BACKEND_E2E_INTERNAL_TOKEN": TEST_TOKEN,
        "REPORT_BACKEND_E2E_JWT_SECRET": TEST_JWT,
        "REPORT_BACKEND_E2E_JAR_HOST_PATH": str(jar_path.resolve()),
        "REPORT_BACKEND_E2E_PG_VOLUME": manifest.db_volume,
        "REPORT_BACKEND_E2E_KAFKA_VOLUME": (
            f"inf007-report-backend-e2e-kafka-{manifest.unique_suffix}"
        ),
        "COMPOSE_PROJECT_NAME": manifest.compose_project,
    }
    env_path = REPO_ROOT / "infra" / "e2e" / "report-backend" / f".env.generated.{manifest.unique_suffix}"
    env_path.parent.mkdir(parents=True, exist_ok=True)
    env_path.write_text(
        "\n".join(f"{key}={value}" for key, value in env.items()) + "\n",
        encoding="utf-8",
    )
    return env_path, env


def _compose(manifest: BackendE2EIsolationManifest, env_path: Path, *args: str) -> subprocess.CompletedProcess[str]:
    cmd = [
        "docker",
        "compose",
        "-p",
        manifest.compose_project,
        "-f",
        str(COMPOSE_FILE),
        "--env-file",
        str(env_path),
        *args,
    ]
    return subprocess.run(
        cmd,
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )


def start_stack() -> StackHandles:
    require_docker()
    if not COMPOSE_FILE.exists():
        raise RuntimeError("REPORT_BACKEND_E2E_COMPOSE_MISSING")
    jar_evidence = ensure_backend_jar()
    jar_path = jar_evidence.path
    suffix = secrets.token_hex(4)
    ports = _pick_ports()
    manifest = build_manifest(suffix, ports)
    assert_backend_e2e_isolation_safe(manifest)
    logger.info("INF-007 Backend E2E isolation: %s", manifest.as_log_dict())
    logger.info(
        "INF-007 Backend E2E jar evidence: path=%s exit=%s sha256=%s mtime=%s bootJar=%s",
        jar_evidence.path,
        jar_evidence.exit_code,
        jar_evidence.sha256,
        jar_evidence.mtime_iso,
        jar_evidence.boot_jar_ran,
    )
    env_path, env = write_env_file(manifest, jar_path, ports)

    # Rename anonymous volume to unique logical name via project scoping;
    # also label DB volume explicitly by recreating with external name is unnecessary
    # because compose project prefixes volumes.
    up = _compose(manifest, env_path, "up", "-d", "--build")
    if up.returncode != 0:
        logger.error("compose up failed stdout=%s stderr=%s", up.stdout[-2000:], up.stderr[-2000:])
        _compose(manifest, env_path, "down", "-v", "--remove-orphans")
        raise RuntimeError("REPORT_BACKEND_E2E_COMPOSE_UP_FAILED")
    try:
        wait_backend_healthy(manifest.backend_url)
        wait_kafka_ready(manifest.kafka_bootstrap)
    except Exception:
        logs = _compose(manifest, env_path, "logs", "backend-e2e", "--tail", "120")
        logger.error("backend not ready logs=%s", (logs.stdout or logs.stderr or "")[-3000:])
        _compose(manifest, env_path, "down", "-v", "--remove-orphans")
        raise
    return StackHandles(
        manifest=manifest,
        env_path=env_path,
        env=env,
        jar_path=jar_path,
        jar_evidence=jar_evidence,
    )


def wait_kafka_ready(bootstrap: str, timeout_seconds: float = 90.0) -> None:
    """Block until Kafka accepts admin + a throwaway consumer group join."""

    from aiokafka import AIOKafkaConsumer
    from aiokafka.admin import AIOKafkaAdminClient

    deadline = time.monotonic() + timeout_seconds
    last_error = "timeout"
    while time.monotonic() < deadline:
        try:
            async def _probe() -> None:
                admin = AIOKafkaAdminClient(bootstrap_servers=bootstrap)
                await admin.start()
                try:
                    await admin.list_topics()
                finally:
                    await admin.close()
                consumer = AIOKafkaConsumer(
                    bootstrap_servers=bootstrap,
                    group_id=f"ai.report-worker.backend-e2e.probe.{secrets.token_hex(3)}",
                    enable_auto_commit=False,
                    auto_offset_reset="earliest",
                )
                await consumer.start()
                await consumer.stop()

            asyncio.run(_probe())
            return
        except Exception as exc:  # noqa: BLE001
            last_error = type(exc).__name__
            time.sleep(2.0)
    raise RuntimeError(f"REPORT_BACKEND_E2E_KAFKA_NOT_READY:{last_error}")


def stop_stack(handles: StackHandles | None) -> None:
    if handles is None:
        return
    _compose(handles.manifest, handles.env_path, "down", "-v", "--remove-orphans")
    try:
        handles.env_path.unlink(missing_ok=True)
    except OSError:
        pass


def wait_backend_healthy(base_url: str, timeout_seconds: float = 180.0) -> None:
    deadline = time.monotonic() + timeout_seconds
    url = f"{base_url.rstrip('/')}/actuator/health"
    last_error = "timeout"
    while time.monotonic() < deadline:
        try:
            response = httpx.get(url, timeout=2.0)
            if response.status_code == 200:
                return
            last_error = f"status={response.status_code}"
        except Exception as exc:  # noqa: BLE001 - poll until ready
            last_error = type(exc).__name__
        time.sleep(2.0)
    raise RuntimeError(f"REPORT_BACKEND_E2E_BACKEND_NOT_READY:{last_error}")


def _psql(handles: StackHandles, sql: str) -> str:
    cmd = [
        "docker",
        "compose",
        "-p",
        handles.manifest.compose_project,
        "-f",
        str(COMPOSE_FILE),
        "--env-file",
        str(handles.env_path),
        "exec",
        "-T",
        "postgres-e2e",
        "psql",
        "-v",
        "ON_ERROR_STOP=1",
        "-U",
        handles.env["REPORT_BACKEND_E2E_DB_USER"],
        "-d",
        handles.manifest.db_name,
        "-q",
        "-t",
        "-A",
        "-c",
        sql,
    ]
    completed = subprocess.run(
        cmd,
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(
            f"REPORT_BACKEND_E2E_SQL_FAILED:{(completed.stderr or '')[-1000:]}"
        )
    stdout = completed.stdout or ""
    lines = [
        line.strip()
        for line in stdout.splitlines()
        if line.strip() and not line.strip().upper().startswith("INSERT")
    ]
    return lines[0] if lines else ""


def seed_three_studies(handles: StackHandles) -> dict[str, FixtureIds]:
    """Seed standard study fixtures into the isolated DB."""

    fixtures: dict[str, FixtureIds] = {}
    for label in ("complete", "fail", "auth", "already_failed", "nplus1"):
        fixtures[label] = _seed_one(handles, label)
    return fixtures


def seed_labeled_study(handles: StackHandles, label: str) -> FixtureIds:
    """Public helper to seed one labeled fixture (self-contained tests)."""

    return _seed_one(handles, label)


def _seed_one(handles: StackHandles, label: str) -> FixtureIds:
    suffix = f"{handles.manifest.unique_suffix}_{label}"
    apartment_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO apartment (complex_code, name, longitude, latitude)
            VALUES ('E2E-{suffix}', 'INF007-E2E-{label}', 127.0, 37.5)
            RETURNING id;
            """,
        )
    )
    member_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO member (
                email, nickname, age_group_public_agreed,
                service_notification_agreed, ad_notification_agreed
            ) VALUES (
                'inf007-e2e-{suffix}@test.local',
                'e2e{suffix[:12]}',
                false, true, false
            )
            RETURNING id;
            """,
        )
    )
    study_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO study (
                apartment_id, leader_id, goal, capacity, title, status
            ) VALUES (
                {apartment_id}, {member_id}, 'inf007-e2e-goal', 5,
                'inf007-e2e-{label}', 'COMPLETED'
            )
            RETURNING id;
            """,
        )
    )
    _psql(
        handles,
        f"""
        INSERT INTO study_member (study_id, member_id, role, status)
        VALUES ({study_id}, {member_id}, 'LEADER', 'ACTIVE');
        """,
    )
    session_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO field_session (
                study_id, status, started_at, ended_at,
                ended_by_id, end_reason
            ) VALUES (
                {study_id}, 'ENDED',
                TIMESTAMPTZ '{OCCURRED_AT}' - INTERVAL '1 hour',
                TIMESTAMPTZ '{OCCURRED_AT}',
                {member_id}, 'ALL_ENDED'
            )
            RETURNING id;
            """,
        )
    )
    _psql(
        handles,
        f"""
        INSERT INTO field_participant (
            session_id, member_id, status, started_at,
            ended_at, end_reason
        ) VALUES (
            {session_id}, {member_id}, 'ENDED',
            TIMESTAMPTZ '{OCCURRED_AT}' - INTERVAL '1 hour',
            TIMESTAMPTZ '{OCCURRED_AT}', 'SELF_ENDED'
        );
        """,
    )
    checklist_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO checklist (session_id, member_id, is_fallback)
            VALUES ({session_id}, {member_id}, false)
            RETURNING id;
            """,
        )
    )
    # fail fixture intentionally omits field_record for sparse / claims=[] paths
    # when label == fail we still add one TEXT for generation-fail path after acquire.
    checklist_item_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO checklist_item (
                checklist_id, category, title, display_order
            ) VALUES (
                {checklist_id}, 'TRANSPORT',
                '대중교통 접근성을 확인했나요?', 1
            )
            RETURNING id;
            """,
        )
    )
    _psql(
        handles,
        f"""
        INSERT INTO checklist_answer (
            checklist_item_id, is_completed, completed_at
        ) VALUES (
            {checklist_item_id}, true, TIMESTAMPTZ '{OCCURRED_AT}'
        );
        """,
    )
    field_record_id = 0
    if label != "sparse":
        field_record_id = int(
            _psql(
                handles,
                f"""
                INSERT INTO field_record (
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, client_request_id, created_at, updated_at
                ) VALUES (
                    {session_id}, {checklist_item_id}, {member_id}, 'TEXT',
                    '역이 가깝습니다.', '{secrets.token_hex(8)}',
                    TIMESTAMPTZ '{OCCURRED_AT}', TIMESTAMPTZ '{OCCURRED_AT}'
                )
                RETURNING id;
                """,
            )
        )
    return FixtureIds(
        label=label,
        study_id=study_id,
        session_id=session_id,
        apartment_id=apartment_id,
        member_id=member_id,
        checklist_item_id=checklist_item_id,
        field_record_id=field_record_id,
    )


def seed_sparse_study(handles: StackHandles) -> FixtureIds:
    """Study with checklist but no TEXT/STT sources → claims=[] DONE path."""

    # Reuse seed with no records by temporary label hack.
    suffix = f"{handles.manifest.unique_suffix}_sparse"
    apartment_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO apartment (complex_code, name, longitude, latitude)
            VALUES ('E2E-{suffix}', 'INF007-E2E-sparse', 127.0, 37.5)
            RETURNING id;
            """,
        )
    )
    member_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO member (
                email, nickname, age_group_public_agreed,
                service_notification_agreed, ad_notification_agreed
            ) VALUES (
                'inf007-e2e-{suffix}@test.local',
                'e2esparse{suffix[:8]}',
                false, true, false
            )
            RETURNING id;
            """,
        )
    )
    study_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO study (
                apartment_id, leader_id, goal, capacity, title, status
            ) VALUES (
                {apartment_id}, {member_id}, 'inf007-e2e-sparse', 5,
                'inf007-e2e-sparse', 'COMPLETED'
            )
            RETURNING id;
            """,
        )
    )
    _psql(
        handles,
        f"""
        INSERT INTO study_member (study_id, member_id, role, status)
        VALUES ({study_id}, {member_id}, 'LEADER', 'ACTIVE');
        """,
    )
    session_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO field_session (
                study_id, status, started_at, ended_at,
                ended_by_id, end_reason
            ) VALUES (
                {study_id}, 'ENDED',
                TIMESTAMPTZ '{OCCURRED_AT}' - INTERVAL '1 hour',
                TIMESTAMPTZ '{OCCURRED_AT}',
                {member_id}, 'ALL_ENDED'
            )
            RETURNING id;
            """,
        )
    )
    _psql(
        handles,
        f"""
        INSERT INTO field_participant (
            session_id, member_id, status, started_at,
            ended_at, end_reason
        ) VALUES (
            {session_id}, {member_id}, 'ENDED',
            TIMESTAMPTZ '{OCCURRED_AT}' - INTERVAL '1 hour',
            TIMESTAMPTZ '{OCCURRED_AT}', 'SELF_ENDED'
        );
        """,
    )
    checklist_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO checklist (session_id, member_id, is_fallback)
            VALUES ({session_id}, {member_id}, false)
            RETURNING id;
            """,
        )
    )
    checklist_item_id = int(
        _psql(
            handles,
            f"""
            INSERT INTO checklist_item (
                checklist_id, category, title, display_order
            ) VALUES (
                {checklist_id}, 'TRANSPORT',
                '대중교통 접근성을 확인했나요?', 1
            )
            RETURNING id;
            """,
        )
    )
    _psql(
        handles,
        f"""
        INSERT INTO checklist_answer (
            checklist_item_id, is_completed, completed_at
        ) VALUES (
            {checklist_item_id}, false, NULL
        );
        """,
    )
    return FixtureIds(
        label="sparse",
        study_id=study_id,
        session_id=session_id,
        apartment_id=apartment_id,
        member_id=member_id,
        checklist_item_id=checklist_item_id,
        field_record_id=0,
    )


def query_report_by_study(handles: StackHandles, study_id: int) -> dict[str, Any]:
    row = _psql(
        handles,
        f"""
        SELECT status,
               progress_stage,
               fail_code,
               fail_reason,
               is_retryable,
               fail_payload_hash,
               processing_attempt,
               processing_lease_expires_at IS NULL AS lease_cleared,
               failed_at IS NOT NULL AS has_failed_at,
               completed_at IS NOT NULL AS has_completed_at,
               result_json IS NOT NULL AS has_result,
               id
        FROM report
        WHERE study_id = {study_id};
        """,
    )
    if not row:
        return {}
    (
        status,
        progress_stage,
        fail_code,
        fail_reason,
        is_retryable,
        fail_payload_hash,
        processing_attempt,
        lease_cleared,
        has_failed_at,
        has_completed_at,
        has_result,
        report_id,
    ) = row.split("|")
    evidence_count = int(
        _psql(
            handles,
            f"SELECT COUNT(*) FROM report_evidence WHERE report_id = {report_id};",
        )
        or "0"
    )
    return {
        "report_id": int(report_id),
        "status": status,
        "progress_stage": progress_stage,
        "fail_code": fail_code if fail_code != "" else None,
        "fail_reason": fail_reason if fail_reason != "" else None,
        "is_retryable": is_retryable == "t",
        "fail_payload_hash": fail_payload_hash if fail_payload_hash != "" else None,
        "processing_attempt": int(processing_attempt),
        "lease_cleared": lease_cleared == "t",
        "has_failed_at": has_failed_at == "t",
        "has_completed_at": has_completed_at == "t",
        "has_result": has_result == "t",
        "evidence_count": evidence_count,
    }


def payload_bytes(fixture: FixtureIds) -> bytes:
    return json.dumps(
        {
            "studyId": fixture.study_id,
            "sessionId": fixture.session_id,
            "apartmentId": fixture.apartment_id,
            "occurredAt": "2026-08-02T04:30:00.000Z",
        }
    ).encode("utf-8")


class AdaptiveCompleteGeneration:
    """Build Backend-valid generation from real normalized input."""

    def __init__(self) -> None:
        self.calls = 0

    async def generate_report(
        self, normalized_input: NormalizedReportInput
    ) -> ReportGenerationResult:
        self.calls += 1
        metrics = compute_metrics(normalized_input)
        categories = ordered_categories(normalized_input) or ["TRANSPORT"]
        # Mirror ReportCompletePostgresTest shape: feature claims + empty
        # category opinions (counts must match opinion list size).
        return ReportGenerationResult(
            title="교통 리포트",
            summary="참여자 의견을 요약한 리포트입니다.",
            metrics=metrics,
            topPositiveFeatures=[
                ReportFeature(
                    rank=1,
                    label="교통",
                    summary="교통 접근성이 좋습니다.",
                    mentionCount=1,
                    participantRefs=["P1"],
                )
            ],
            topCautionFeatures=[],
            commonOpinions=[],
            conflictingOpinions=[],
            categories=[
                ReportCategory(
                    category=category,
                    summary="교통 기록을 분석했습니다.",
                    positiveOpinionCount=0,
                    cautionOpinionCount=0,
                    dataSufficient=False,
                    participantOpinions=[],
                )
                for category in categories
            ],
        )


class AdaptiveEvidenceService:
    def __init__(self) -> None:
        self.calls = 0

    async def link_evidence(
        self, request: EvidenceLinkRequest
    ) -> EvidenceLinkResult:
        self.calls += 1
        usable = collect_usable_sources(request.normalizedInput)
        if not usable:
            return EvidenceLinkResult(claims=[])
        source = usable[0]
        feature = request.generationResult.topPositiveFeatures[0]
        return EvidenceLinkResult(
            claims=[
                EvidenceClaim(
                    claimKey="feature.transport",
                    claimType=ClaimType.FEATURE_POSITIVE,
                    category=None,
                    label=feature.label,
                    opinionType=OpinionType.POSITIVE,
                    participantRefs=[source.participant_ref],
                    evidences=[
                        EvidenceRef(
                            sourceType=ReportSourceType.TEXT
                            if source.source_type == ReportSourceType.TEXT
                            else ReportSourceType.STT,
                            sourceId=source.source_id,
                            participantRef=source.participant_ref,
                            checklistItemId=source.checklist_item_id,
                            category=source.category,
                            recordedAt=next(
                                item.recordedAt
                                for item in request.normalizedInput.sources
                                if item.sourceId == source.source_id
                            ),
                            evidenceRole=EvidenceRole.SUPPORT,
                        )
                    ],
                    displayOrder=1,
                )
            ]
        )


class FailingGeneration:
    def __init__(self, code: str = WorkerErrorCode.SCHEMA_VALIDATION_FAILED.value) -> None:
        self.calls = 0
        self.code = code

    async def generate_report(
        self, normalized_input: NormalizedReportInput
    ) -> ReportGenerationResult:
        del normalized_input
        self.calls += 1
        raise ReportGenerationError(self.code, "forced non-retryable generation failure")


class InsufficientGeneration:
    def __init__(self) -> None:
        self.calls = 0

    async def generate_report(
        self, normalized_input: NormalizedReportInput
    ) -> ReportGenerationResult:
        from app.services.report_generation import (
            build_insufficient_result,
            ordered_categories,
        )

        self.calls += 1
        return build_insufficient_result(
            metrics=compute_metrics(normalized_input),
            categories=ordered_categories(normalized_input),
        )


async def ensure_topics(bootstrap: str, *topics: str) -> None:
    from aiokafka.admin import AIOKafkaAdminClient, NewTopic
    from aiokafka.errors import TopicAlreadyExistsError

    admin = AIOKafkaAdminClient(bootstrap_servers=bootstrap)
    await admin.start()
    try:
        try:
            await admin.create_topics(
                [NewTopic(name=t, num_partitions=1, replication_factor=1) for t in topics],
                validate_only=False,
            )
        except TopicAlreadyExistsError:
            return
        except Exception as exc:
            name = type(exc).__name__
            if "TopicAlreadyExists" in name:
                return
            raise RuntimeError("REPORT_BACKEND_E2E_TOPIC_CREATE_FAILED") from None
    finally:
        await admin.close()


async def committed_offset(
    bootstrap: str, topic: str, group: str
) -> int | None:
    from aiokafka import AIOKafkaConsumer, TopicPartition

    consumer = AIOKafkaConsumer(
        bootstrap_servers=bootstrap,
        group_id=group,
        enable_auto_commit=False,
    )
    await consumer.start()
    try:
        tp = TopicPartition(topic, 0)
        return await consumer.committed(tp)
    finally:
        await consumer.stop()


def build_worker(
    *,
    base_url: str,
    token: str,
    http_client: httpx.AsyncClient,
    generation: Any,
    evidence: Any,
    bootstrap: str,
    topic: str,
    dlt_topic: str,
    group: str,
    dlt_producer: Any,
) -> ReportKafkaWorker:
    from aiokafka import AIOKafkaConsumer, TopicPartition

    backend = ReportBackendHttpAdapter(
        client=http_client,
        base_url=base_url,
        internal_token=token,
    )
    input_port = ReportInputHttpAdapter(
        client=http_client,
        base_url=base_url,
        internal_token=token,
    )
    report_worker = ReportWorker(
        backend=backend,
        input_port=input_port,
        generation=generation,
        evidence=evidence,
        sleeper=RecordingSleeper(),
        provider_retry_delay_seconds=0.01,
    )
    consumer = AIOKafkaConsumer(
        topic,
        bootstrap_servers=bootstrap,
        group_id=group,
        enable_auto_commit=False,
        auto_offset_reset="earliest",
        max_poll_records=1,
    )
    return ReportKafkaWorker(
        consumer=consumer,
        handler=ReportMessageHandler(report_worker),
        rejection_policy=KafkaDltRejectionPolicy(
            producer=dlt_producer, dlt_topic=dlt_topic
        ),
        retry_backoff_seconds=0.2,
        consumer_group=group,
        rejection_policy_name="dlt",
        dlt_topic=dlt_topic,
        dlt_producer_started=True,
        topic_partition_factory=lambda t, p: TopicPartition(t, p),
    )


async def wait_until(
    predicate: Callable[[], bool] | Callable[[], Any],
    *,
    timeout: float = 60.0,
    interval: float = 0.2,
) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        await asyncio.sleep(interval)
    raise TimeoutError("REPORT_BACKEND_E2E_WAIT_TIMEOUT")
