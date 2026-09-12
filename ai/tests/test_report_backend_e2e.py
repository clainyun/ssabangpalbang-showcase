"""Opt-in real Spring Backend + isolated PostgreSQL + Kafka E2E (INF-007).

Requires:
  RUN_REPORT_BACKEND_E2E=1
  Docker daemon
  Ability to build backend bootJar (-x test)

Fail-closed: if the flag is set but prerequisites are missing, tests FAIL
(not skip).
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import time

import httpx
import pytest

from tests.support.report_backend_e2e_harness import (
    TEST_TOKEN,
    AdaptiveCompleteGeneration,
    AdaptiveEvidenceService,
    FailingGeneration,
    InsufficientGeneration,
    StackHandles,
    build_worker,
    committed_offset,
    ensure_topics,
    payload_bytes,
    query_report_by_study,
    seed_sparse_study,
    seed_three_studies,
    start_stack,
    stop_stack,
    wait_until,
)

logger = logging.getLogger(__name__)

pytestmark = pytest.mark.skipif(
    os.getenv("RUN_REPORT_BACKEND_E2E") != "1",
    reason="set RUN_REPORT_BACKEND_E2E=1 to run isolated Backend+PG+Kafka E2E",
)


@pytest.fixture(scope="module")
def e2e_stack() -> StackHandles:
    handles: StackHandles | None = None
    try:
        handles = start_stack()
        yield handles
    finally:
        stop_stack(handles)


@pytest.fixture(scope="module")
def fixtures(e2e_stack: StackHandles):
    seeded = seed_three_studies(e2e_stack)
    seeded["sparse"] = seed_sparse_study(e2e_stack)
    return seeded


def _topics(manifest, name: str) -> tuple[str, str, str]:
    request = f"{manifest.request_topic}.{name}"
    dlt = f"{manifest.dlt_topic}.{name}"
    group = f"{manifest.consumer_group}.{name}"
    return request, dlt, group


@pytest.mark.asyncio
async def test_e2e1_happy_complete_done(
    e2e_stack: StackHandles, fixtures
) -> None:
    from aiokafka import AIOKafkaProducer

    assert e2e_stack.jar_evidence.boot_jar_ran is True
    assert e2e_stack.jar_evidence.exit_code == 0
    assert e2e_stack.jar_path.exists()
    assert len(e2e_stack.jar_evidence.sha256) == 64
    logger.info(
        "E2E jar path=%s sha256=%s mtime=%s",
        e2e_stack.jar_evidence.path,
        e2e_stack.jar_evidence.sha256,
        e2e_stack.jar_evidence.mtime_iso,
    )

    manifest = e2e_stack.manifest
    fixture = fixtures["complete"]
    request_topic, dlt_topic, group = _topics(manifest, "complete")
    await ensure_topics(manifest.kafka_bootstrap, request_topic, dlt_topic)

    generation = AdaptiveCompleteGeneration()
    evidence = AdaptiveEvidenceService()
    dlt_producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    await dlt_producer.start()
    await producer.start()
    async with httpx.AsyncClient(timeout=30.0) as http_client:
        worker = build_worker(
            base_url=manifest.backend_url,
            token=TEST_TOKEN,
            http_client=http_client,
            generation=generation,
            evidence=evidence,
            bootstrap=manifest.kafka_bootstrap,
            topic=request_topic,
            dlt_topic=dlt_topic,
            group=group,
            dlt_producer=dlt_producer,
        )
        await worker.start()
        try:
            await producer.send_and_wait(
                request_topic,
                key=str(fixture.study_id).encode(),
                value=payload_bytes(fixture),
            )
            await wait_until(
                lambda: query_report_by_study(e2e_stack, fixture.study_id).get("status")
                == "DONE",
                timeout=90.0,
            )
            stored = query_report_by_study(e2e_stack, fixture.study_id)
            assert stored["progress_stage"] == "COMPLETED"
            assert stored["has_result"] is True
            assert stored["evidence_count"] >= 1
            assert stored["lease_cleared"] is True
            assert generation.calls >= 1
            assert evidence.calls >= 1
            assert worker.is_healthy() is True
            committed = await committed_offset(
                manifest.kafka_bootstrap, request_topic, group
            )
            assert committed is not None and committed >= 1
        finally:
            await worker.stop()
            await producer.stop()
            await dlt_producer.stop()


@pytest.mark.asyncio
async def test_e2e2_sparse_claims_empty_done(
    e2e_stack: StackHandles, fixtures
) -> None:
    from aiokafka import AIOKafkaProducer

    manifest = e2e_stack.manifest
    fixture = fixtures["sparse"]
    request_topic, dlt_topic, group = _topics(manifest, "sparse")
    await ensure_topics(manifest.kafka_bootstrap, request_topic, dlt_topic)

    generation = InsufficientGeneration()
    evidence = AdaptiveEvidenceService()
    dlt_producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    await dlt_producer.start()
    await producer.start()
    async with httpx.AsyncClient(timeout=30.0) as http_client:
        worker = build_worker(
            base_url=manifest.backend_url,
            token=TEST_TOKEN,
            http_client=http_client,
            generation=generation,
            evidence=evidence,
            bootstrap=manifest.kafka_bootstrap,
            topic=request_topic,
            dlt_topic=dlt_topic,
            group=group,
            dlt_producer=dlt_producer,
        )
        await worker.start()
        try:
            await producer.send_and_wait(
                request_topic,
                key=str(fixture.study_id).encode(),
                value=payload_bytes(fixture),
            )
            await wait_until(
                lambda: query_report_by_study(e2e_stack, fixture.study_id).get("status")
                == "DONE",
                timeout=90.0,
            )
            stored = query_report_by_study(e2e_stack, fixture.study_id)
            assert stored["evidence_count"] == 0
            assert stored["has_result"] is True
            assert evidence.calls == 0
            committed = await committed_offset(
                manifest.kafka_bootstrap, request_topic, group
            )
            assert committed is not None and committed >= 1
        finally:
            await worker.stop()
            await producer.stop()
            await dlt_producer.stop()


@pytest.mark.asyncio
async def test_e2e3_permanent_fail_persisted(
    e2e_stack: StackHandles, fixtures
) -> None:
    from aiokafka import AIOKafkaProducer

    manifest = e2e_stack.manifest
    fixture = fixtures["fail"]
    request_topic, dlt_topic, group = _topics(manifest, "fail")
    await ensure_topics(manifest.kafka_bootstrap, request_topic, dlt_topic)

    generation = FailingGeneration()
    evidence = AdaptiveEvidenceService()
    dlt_producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    await dlt_producer.start()
    await producer.start()
    async with httpx.AsyncClient(timeout=30.0) as http_client:
        worker = build_worker(
            base_url=manifest.backend_url,
            token=TEST_TOKEN,
            http_client=http_client,
            generation=generation,
            evidence=evidence,
            bootstrap=manifest.kafka_bootstrap,
            topic=request_topic,
            dlt_topic=dlt_topic,
            group=group,
            dlt_producer=dlt_producer,
        )
        await worker.start()
        try:
            await producer.send_and_wait(
                request_topic,
                key=str(fixture.study_id).encode(),
                value=payload_bytes(fixture),
            )
            await wait_until(
                lambda: query_report_by_study(e2e_stack, fixture.study_id).get("status")
                == "FAILED",
                timeout=90.0,
            )
            stored = query_report_by_study(e2e_stack, fixture.study_id)
            assert stored["progress_stage"] == "REPORT_GENERATION"
            assert stored["fail_code"] == "SCHEMA_VALIDATION_FAILED"
            assert stored["fail_reason"] == "AI 응답 스키마 검증에 실패했습니다."
            assert stored["is_retryable"] is False
            assert stored["fail_payload_hash"] is not None
            assert len(stored["fail_payload_hash"]) == 64
            assert stored["has_failed_at"] is True
            assert stored["lease_cleared"] is True
            assert generation.calls >= 1
            committed = await committed_offset(
                manifest.kafka_bootstrap, request_topic, group
            )
            assert committed is not None and committed >= 1
        finally:
            await worker.stop()
            await producer.stop()
            await dlt_producer.stop()


@pytest.mark.asyncio
async def test_e2e4_already_failed_replay_is_self_contained(
    e2e_stack: StackHandles, fixtures
) -> None:
    """Drive FAILED then replay ALREADY_FAILED without depending on E2E-3 order."""

    from aiokafka import AIOKafkaProducer

    manifest = e2e_stack.manifest
    fixture = fixtures["already_failed"]
    assert query_report_by_study(e2e_stack, fixture.study_id) == {}

    request_topic, dlt_topic, group = _topics(manifest, "already-failed")
    await ensure_topics(manifest.kafka_bootstrap, request_topic, dlt_topic)
    generation = FailingGeneration()
    evidence = AdaptiveEvidenceService()
    dlt_producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    await dlt_producer.start()
    await producer.start()
    async with httpx.AsyncClient(timeout=30.0) as http_client:
        worker = build_worker(
            base_url=manifest.backend_url,
            token=TEST_TOKEN,
            http_client=http_client,
            generation=generation,
            evidence=evidence,
            bootstrap=manifest.kafka_bootstrap,
            topic=request_topic,
            dlt_topic=dlt_topic,
            group=group,
            dlt_producer=dlt_producer,
        )
        await worker.start()
        try:
            await producer.send_and_wait(
                request_topic,
                key=str(fixture.study_id).encode(),
                value=payload_bytes(fixture),
            )
            await wait_until(
                lambda: query_report_by_study(e2e_stack, fixture.study_id).get("status")
                == "FAILED",
                timeout=90.0,
            )
            stored_after_fail = query_report_by_study(e2e_stack, fixture.study_id)
            assert stored_after_fail["fail_code"] == "SCHEMA_VALIDATION_FAILED"
            assert stored_after_fail["is_retryable"] is False
            attempt_after_fail = stored_after_fail["processing_attempt"]
            calls_after_fail = generation.calls
            assert calls_after_fail >= 1
            committed_after_fail = await committed_offset(
                manifest.kafka_bootstrap, request_topic, group
            )
            assert committed_after_fail is not None and committed_after_fail >= 1

            await producer.send_and_wait(
                request_topic,
                key=str(fixture.study_id).encode(),
                value=payload_bytes(fixture),
            )
            deadline = time.monotonic() + 40.0
            while time.monotonic() < deadline:
                value = await committed_offset(
                    manifest.kafka_bootstrap, request_topic, group
                )
                if value is not None and value >= committed_after_fail + 1:
                    break
                await asyncio.sleep(0.2)
            else:
                raise TimeoutError("E2E-4 replay commit wait timeout")

            stored = query_report_by_study(e2e_stack, fixture.study_id)
            assert stored["status"] == "FAILED"
            assert stored["processing_attempt"] == attempt_after_fail
            assert generation.calls == calls_after_fail
        finally:
            await worker.stop()
            await producer.stop()
            await dlt_producer.stop()


@pytest.mark.asyncio
async def test_e2e5_and_e2e6_auth_block_then_restart_reprocess(
    e2e_stack: StackHandles, fixtures
) -> None:
    from aiokafka import AIOKafkaProducer

    manifest = e2e_stack.manifest
    fixture = fixtures["auth"]
    request_topic, dlt_topic, group = _topics(manifest, "auth")
    await ensure_topics(manifest.kafka_bootstrap, request_topic, dlt_topic)

    dlt_calls = {"n": 0}

    class CountingProducer:
        async def send_and_wait(self, *args, **kwargs):  # noqa: ANN002, ANN003
            dlt_calls["n"] += 1
            return None

    generation = AdaptiveCompleteGeneration()
    evidence = AdaptiveEvidenceService()
    producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    await producer.start()
    async with httpx.AsyncClient(timeout=30.0) as http_client:
        bad_worker = build_worker(
            base_url=manifest.backend_url,
            token="wrong-token-not-matching-backend",
            http_client=http_client,
            generation=generation,
            evidence=evidence,
            bootstrap=manifest.kafka_bootstrap,
            topic=request_topic,
            dlt_topic=dlt_topic,
            group=group,
            dlt_producer=CountingProducer(),
        )
        await bad_worker.start()
        try:
            await producer.send_and_wait(
                request_topic,
                key=str(fixture.study_id).encode(),
                value=payload_bytes(fixture),
            )
            await wait_until(
                lambda: bool(bad_worker.health.blocked_partitions),
                timeout=40.0,
            )
            assert dlt_calls["n"] == 0
            assert bad_worker.health.last_error_code == "BACKEND_AUTH_ERROR"
            assert bad_worker.is_healthy() is False
            committed = await committed_offset(
                manifest.kafka_bootstrap, request_topic, group
            )
            assert committed is None or committed == 0
        finally:
            await bad_worker.stop()

        dlt_producer = AIOKafkaProducer(
            bootstrap_servers=manifest.kafka_bootstrap, acks="all"
        )
        await dlt_producer.start()
        good_worker = build_worker(
            base_url=manifest.backend_url,
            token=TEST_TOKEN,
            http_client=http_client,
            generation=generation,
            evidence=evidence,
            bootstrap=manifest.kafka_bootstrap,
            topic=request_topic,
            dlt_topic=dlt_topic,
            group=group,
            dlt_producer=dlt_producer,
        )
        await good_worker.start()
        try:
            await wait_until(
                lambda: query_report_by_study(e2e_stack, fixture.study_id).get("status")
                == "DONE",
                timeout=90.0,
            )
            stored = query_report_by_study(e2e_stack, fixture.study_id)
            assert stored["status"] == "DONE"
            committed = await committed_offset(
                manifest.kafka_bootstrap, request_topic, group
            )
            assert committed is not None and committed >= 1
            assert good_worker.is_healthy() is True
        finally:
            await good_worker.stop()
            await dlt_producer.stop()
            await producer.stop()


@pytest.mark.asyncio
async def test_e2e7_malformed_publishes_safe_dlt(
    e2e_stack: StackHandles,
) -> None:
    from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

    manifest = e2e_stack.manifest
    request_topic, dlt_topic, group = _topics(manifest, "dlt-ok")
    await ensure_topics(manifest.kafka_bootstrap, request_topic, dlt_topic)

    generation = AdaptiveCompleteGeneration()
    evidence = AdaptiveEvidenceService()
    dlt_producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    dlt_reader = AIOKafkaConsumer(
        dlt_topic,
        bootstrap_servers=manifest.kafka_bootstrap,
        group_id=f"{group}-reader",
        auto_offset_reset="earliest",
        enable_auto_commit=True,
    )
    producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    await dlt_producer.start()
    await dlt_reader.start()
    await producer.start()
    async with httpx.AsyncClient(timeout=30.0) as http_client:
        worker = build_worker(
            base_url=manifest.backend_url,
            token=TEST_TOKEN,
            http_client=http_client,
            generation=generation,
            evidence=evidence,
            bootstrap=manifest.kafka_bootstrap,
            topic=request_topic,
            dlt_topic=dlt_topic,
            group=group,
            dlt_producer=dlt_producer,
        )
        await worker.start()
        try:
            await producer.send_and_wait(
                request_topic, key=b"x", value=b'{"not":"valid"}'
            )
            dlt_msg = None
            async with asyncio.timeout(40):
                async for record in dlt_reader:
                    dlt_msg = json.loads(record.value.decode("utf-8"))
                    break
            assert dlt_msg is not None
            assert dlt_msg["originalTopic"] == request_topic
            assert "raw" not in dlt_msg
            assert "processingToken" not in dlt_msg
            assert "Authorization" not in json.dumps(dlt_msg)
            assert len(dlt_msg["payloadHash"]) == 64
            assert worker.health.dlt_publish_count >= 1
            committed = await committed_offset(
                manifest.kafka_bootstrap, request_topic, group
            )
            assert committed is not None and committed >= 1
        finally:
            await worker.stop()
            await producer.stop()
            await dlt_producer.stop()
            await dlt_reader.stop()


@pytest.mark.asyncio
async def test_e2e8_dlt_publish_failure_blocks_n_plus_one(
    e2e_stack: StackHandles, fixtures
) -> None:
    """DLT ACK failure on N must block the partition so N+1 is never processed."""

    from aiokafka import AIOKafkaProducer

    manifest = e2e_stack.manifest
    nplus1 = fixtures["nplus1"]
    assert query_report_by_study(e2e_stack, nplus1.study_id) == {}

    request_topic, dlt_topic, group = _topics(manifest, "dlt-fail")
    await ensure_topics(manifest.kafka_bootstrap, request_topic)

    class ClosedProducer:
        async def send_and_wait(self, *args, **kwargs):  # noqa: ANN002, ANN003
            raise TimeoutError("forced dlt ack timeout")

    generation = AdaptiveCompleteGeneration()
    evidence = AdaptiveEvidenceService()
    producer = AIOKafkaProducer(
        bootstrap_servers=manifest.kafka_bootstrap, acks="all"
    )
    await producer.start()
    async with httpx.AsyncClient(timeout=30.0) as http_client:
        worker = build_worker(
            base_url=manifest.backend_url,
            token=TEST_TOKEN,
            http_client=http_client,
            generation=generation,
            evidence=evidence,
            bootstrap=manifest.kafka_bootstrap,
            topic=request_topic,
            dlt_topic=dlt_topic,
            group=group,
            dlt_producer=ClosedProducer(),
        )
        await worker.start()
        try:
            # N: malformed → DLT publish failure → partition block
            await producer.send_and_wait(
                request_topic, key=b"n", value=b'{"bad":true}'
            )
            # N+1: valid REPORT_REQUESTED that must NOT be acquired/completed
            await producer.send_and_wait(
                request_topic,
                key=str(nplus1.study_id).encode(),
                value=payload_bytes(nplus1),
            )
            await wait_until(
                lambda: worker.health.dlt_failure_count >= 1,
                timeout=40.0,
            )
            assert worker.health.last_error_code == "DLT_PUBLISH_FAILED"
            assert worker.is_healthy() is False
            assert worker.health.blocked_partitions
            committed = await committed_offset(
                manifest.kafka_bootstrap, request_topic, group
            )
            assert committed is None or committed == 0

            # Allow time for a buggy worker to incorrectly advance to N+1.
            await asyncio.sleep(5.0)
            assert generation.calls == 0
            assert query_report_by_study(e2e_stack, nplus1.study_id) == {}
            committed_later = await committed_offset(
                manifest.kafka_bootstrap, request_topic, group
            )
            assert committed_later is None or committed_later == 0
            assert worker.health.blocked_partitions
            assert worker.is_healthy() is False
        finally:
            await worker.stop()
            await producer.stop()
