"""Isolation guards for INF-007 real Backend + PostgreSQL + Kafka E2E."""

from __future__ import annotations

from dataclasses import asdict, dataclass


BLOCKED_SUBSTRINGS = (
    "postgres-prod-data",
    "ssabangpalbang-postgres",
    "ssabangpalbang-kafka",
    "ssabangpalbang-redis",
    "ai.report-worker.v1",
)

BLOCKED_DB_PORTS = {5432}
BLOCKED_BACKEND_PORTS = {8080, 18081}
BLOCKED_KAFKA_HOST_PORTS = {9092, 19092, 19192, 19293}


@dataclass(frozen=True)
class BackendE2EIsolationManifest:
    compose_file: str
    compose_project: str
    services: tuple[str, ...]
    volumes: tuple[str, ...]
    db_host: str
    db_port: int
    db_name: str
    db_volume: str
    backend_url: str
    backend_port: int
    kafka_bootstrap: str
    kafka_port: int
    request_topic: str
    dlt_topic: str
    consumer_group: str
    token_is_test_only: bool
    contains_ops_address: bool
    uses_ssabangpalbang_resources: bool
    uses_production_volume: bool
    unique_suffix: str

    def as_log_dict(self) -> dict[str, object]:
        return asdict(self)


def assert_backend_e2e_isolation_safe(manifest: BackendE2EIsolationManifest) -> None:
    """Raise if the Backend E2E configuration could touch ops resources."""

    if not manifest.unique_suffix:
        raise RuntimeError("E2E unique suffix is required")
    if not manifest.token_is_test_only:
        raise RuntimeError("Backend E2E token must be test-only")
    if manifest.contains_ops_address:
        raise RuntimeError("Backend E2E must not include ops addresses")
    if manifest.uses_ssabangpalbang_resources:
        raise RuntimeError("Backend E2E must not reuse ssabangpalbang resources")
    if manifest.uses_production_volume:
        raise RuntimeError("Backend E2E must not use production volumes")
    if manifest.db_host not in {"127.0.0.1", "localhost"}:
        raise RuntimeError("DB host must be localhost/127.0.0.1")
    if manifest.db_port in BLOCKED_DB_PORTS:
        raise RuntimeError("DB port 5432 is forbidden for isolated E2E")
    if manifest.backend_port in BLOCKED_BACKEND_PORTS:
        raise RuntimeError("Backend port collides with local/ops defaults")
    if manifest.kafka_port in BLOCKED_KAFKA_HOST_PORTS:
        raise RuntimeError("Kafka host port collides with reserved E2E/ops ports")
    if not manifest.db_name.startswith("inf007_report_e2e_"):
        raise RuntimeError("DB name must use inf007_report_e2e_* prefix")
    if "postgres-prod-data" in manifest.db_volume:
        raise RuntimeError("postgres-prod-data volume is forbidden")
    if not manifest.db_volume.startswith("inf007-report-backend-e2e-pg-"):
        raise RuntimeError("DB volume must use inf007-report-backend-e2e-pg-* prefix")
    if not manifest.compose_project.startswith("inf007-report-backend-e2e-"):
        raise RuntimeError("compose project must use inf007-report-backend-e2e-* prefix")
    if "ssabangpalbang" in manifest.compose_project:
        raise RuntimeError("compose project must not include ssabangpalbang")
    if not manifest.consumer_group.startswith("ai.report-worker.backend-e2e."):
        raise RuntimeError(
            "consumer group must use ai.report-worker.backend-e2e.* prefix"
        )
    if manifest.consumer_group == "ai.report-worker.v1":
        raise RuntimeError("ops consumer group is forbidden")
    if ".e2e." not in manifest.request_topic:
        raise RuntimeError("request topic must include .e2e.")
    if ".e2e." not in manifest.dlt_topic:
        raise RuntimeError("DLT topic must include .e2e.")
    if not manifest.backend_url.startswith("http://127.0.0.1:"):
        raise RuntimeError("Backend URL must be http://127.0.0.1:<port>")
    if "ssafy" in manifest.backend_url.lower() or "ec2" in manifest.backend_url.lower():
        raise RuntimeError("Backend URL looks like ops/EC2")

    blob = " ".join(
        [
            manifest.compose_file,
            manifest.compose_project,
            " ".join(manifest.services),
            " ".join(manifest.volumes),
            manifest.db_host,
            manifest.db_name,
            manifest.db_volume,
            manifest.backend_url,
            manifest.kafka_bootstrap,
            manifest.request_topic,
            manifest.dlt_topic,
            manifest.consumer_group,
        ]
    ).lower()
    for blocked in BLOCKED_SUBSTRINGS:
        if blocked == "ai.report-worker.v1":
            if manifest.consumer_group == blocked:
                raise RuntimeError(f"blocked isolation token: {blocked}")
            continue
        if blocked in blob:
            raise RuntimeError(f"blocked isolation token: {blocked}")
