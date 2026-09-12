"""Isolation guards for INF-007 Phase 2 opt-in Kafka E2E."""

from __future__ import annotations

from dataclasses import asdict, dataclass


BLOCKED_SUBSTRINGS = (
    "postgres-prod-data",
    "ai.report-worker.v1",  # ops consumer group must not be reused as E2E group
)


@dataclass(frozen=True)
class IsolationManifest:
    compose_file: str
    compose_project: str
    services: tuple[str, ...]
    volumes: tuple[str, ...]
    kafka_bootstrap: str
    request_topic: str
    dlt_topic: str
    consumer_group: str
    backend_target: str
    uses_postgresql: bool
    token_is_test_only: bool
    contains_ops_address: bool

    def as_log_dict(self) -> dict[str, object]:
        return asdict(self)


def assert_isolation_safe(manifest: IsolationManifest) -> None:
    """Raise if the E2E configuration could touch ops resources."""

    if manifest.uses_postgresql:
        raise RuntimeError("report Kafka E2E must not use PostgreSQL")
    if not manifest.token_is_test_only:
        raise RuntimeError("report Kafka E2E token must be test-only")
    if manifest.contains_ops_address:
        raise RuntimeError("report Kafka E2E must not include ops addresses")
    if manifest.backend_target != "contract-stub":
        raise RuntimeError("report Kafka E2E backend must be contract-stub")
    if not manifest.consumer_group.startswith("ai.report-worker.e2e."):
        raise RuntimeError("E2E consumer group must use ai.report-worker.e2e.* prefix")
    if manifest.consumer_group == "ai.report-worker.v1":
        raise RuntimeError("ops consumer group ai.report-worker.v1 is forbidden")
    if ".e2e." not in manifest.request_topic:
        raise RuntimeError("E2E request topic must include .e2e.")
    if ".e2e." not in manifest.dlt_topic:
        raise RuntimeError("E2E DLT topic must include .e2e.")
    if "ssabangpalbang-local" in manifest.compose_project:
        raise RuntimeError("must not reuse local compose project name")
    if "ssabangpalbang" in manifest.compose_project and "e2e" not in manifest.compose_project:
        raise RuntimeError("compose project name looks like shared ops/local stack")
    blob = " ".join(
        [
            manifest.compose_file,
            manifest.compose_project,
            " ".join(manifest.services),
            " ".join(manifest.volumes),
            manifest.kafka_bootstrap,
            manifest.request_topic,
            manifest.dlt_topic,
            manifest.consumer_group,
            manifest.backend_target,
        ]
    ).lower()
    for blocked in BLOCKED_SUBSTRINGS:
        if blocked == "ai.report-worker.v1":
            # Exact ops group only; e2e prefix contains similar text elsewhere.
            if manifest.consumer_group == blocked:
                raise RuntimeError(f"blocked isolation token: {blocked}")
            continue
        if blocked in blob:
            raise RuntimeError(f"blocked isolation token: {blocked}")
