"""Opt-in real Kafka round trip for the FastAPI STT consumer.

Run with RUN_STT_KAFKA_E2E=1 after starting Kafka. The backend-owned E2E task
publishes the same raw request contract to prove Spring → Kafka compatibility.
"""

from __future__ import annotations

import asyncio
import json
import os
from pathlib import Path

import pytest

from app.messaging.idempotency import InMemorySttIdempotencyStore
from app.messaging.kafka_stt import KafkaTopics, SttKafkaWorker
from app.providers.audio_store import FakeAudioStore, PassthroughAudioNormalizer
from app.providers.stt_provider import FakeSttProvider
from app.services.stt_service import SttService


pytestmark = pytest.mark.skipif(
    os.getenv("RUN_STT_KAFKA_E2E") != "1",
    reason="set RUN_STT_KAFKA_E2E=1 with a reachable Kafka broker",
)


@pytest.mark.asyncio
async def test_real_kafka_request_is_received_and_done_is_returned() -> None:
    from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

    bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    request_topic = "field-visit.stt.request.v1"
    result_topic = "field-visit.stt.result.v1"
    dlq_topic = "field-visit.stt.request.dlq.v1"
    suffix = os.urandom(4).hex()
    stt_id = f"stt-ai-e2e-{suffix}"
    group = f"ssabangpalbang-ai-e2e-{suffix}"

    worker_consumer = AIOKafkaConsumer(
        request_topic,
        bootstrap_servers=bootstrap,
        group_id=group,
        enable_auto_commit=False,
        auto_offset_reset="latest",
        max_poll_records=1,
    )
    result_consumer = AIOKafkaConsumer(
        result_topic,
        bootstrap_servers=bootstrap,
        group_id=f"{group}-result",
        auto_offset_reset="latest",
    )
    producer = AIOKafkaProducer(bootstrap_servers=bootstrap, acks="all")
    result_producer = AIOKafkaProducer(bootstrap_servers=bootstrap, acks="all")
    service = SttService(
        audio_store=FakeAudioStore(),
        normalizer=PassthroughAudioNormalizer(),
        provider=FakeSttProvider(text="실제 카프카 왕복 결과"),
        timeout_seconds=2,
    )
    worker = SttKafkaWorker(
        consumer=worker_consumer,
        producer=result_producer,
        service=service,
        idempotency=InMemorySttIdempotencyStore(),
        topics=KafkaTopics(request_topic, result_topic, dlq_topic),
        lease_seconds=30,
        result_ttl_seconds=300,
    )

    fixture = json.loads(
        (
            Path(__file__).resolve().parents[2]
            / "contracts"
            / "ai-003"
            / "stt_request.json"
        ).read_text(encoding="utf-8")
    )
    fixture["sttId"] = stt_id

    await result_consumer.start()
    await producer.start()
    await worker.start()
    try:
        await producer.send_and_wait(
            request_topic,
            key=stt_id.encode("utf-8"),
            value=json.dumps(fixture, ensure_ascii=False).encode("utf-8"),
        )

        statuses: list[str] = []
        async with asyncio.timeout(15):
            async for record in result_consumer:
                if record.key != stt_id.encode("utf-8"):
                    continue
                statuses.append(json.loads(record.value.decode("utf-8"))["status"])
                if statuses[-1] == "DONE":
                    break
        assert statuses == ["PROCESSING", "DONE"]
    finally:
        await worker.stop()
        await producer.stop()
        await result_consumer.stop()
        service.close()
