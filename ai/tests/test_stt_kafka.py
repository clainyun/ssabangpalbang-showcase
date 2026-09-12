"""Kafka worker tests with raw UTF-8 JSON and manual commits."""

from __future__ import annotations

import json
from dataclasses import dataclass

import pytest

from app.messaging.idempotency import InMemorySttIdempotencyStore
from app.messaging.kafka_stt import KafkaTopics, LeaseBusyError, SttKafkaWorker
from app.providers.audio_store import FakeAudioStore, PassthroughAudioNormalizer
from app.providers.stt_provider import FakeSttProvider
from app.services.stt_service import SttService


@dataclass
class _Record:
    topic: str
    partition: int
    offset: int
    key: bytes | None
    value: bytes


class _Producer:
    def __init__(self, actions: list[tuple]) -> None:
        self.actions = actions

    async def send_and_wait(self, topic: str, *, key: bytes, value: bytes) -> None:
        self.actions.append(("send", topic, key, value))


class _Consumer:
    def __init__(self, actions: list[tuple]) -> None:
        self.actions = actions

    async def commit(self, offsets) -> None:
        self.actions.append(("commit", offsets))


class _LeaseLosingStore(InMemorySttIdempotencyStore):
    async def save_result_if_owner(
        self,
        key,
        token,
        result,
        ttl_seconds,
    ) -> bool:
        return False


def _request_record(value: dict | None = None, *, key: bytes = b"stt-test") -> _Record:
    payload = value or {
        "schemaVersion": 1,
        "sttId": "stt-test",
        "attemptNo": 1,
        "audioFileId": 90,
        "objectKey": "field-visit/stt/7/90.m4a",
        "contentType": "audio/m4a",
        "language": "ko-KR",
    }
    return _Record(
        topic="field-visit.stt.request.v1",
        partition=0,
        offset=42,
        key=key,
        value=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
    )


def _worker(
    provider: FakeSttProvider,
    idempotency=None,
):
    actions: list[tuple] = []
    service = SttService(
        audio_store=FakeAudioStore(),
        normalizer=PassthroughAudioNormalizer(),
        provider=provider,
        timeout_seconds=2,
    )
    return (
        SttKafkaWorker(
            consumer=_Consumer(actions),
            producer=_Producer(actions),
            service=service,
            idempotency=idempotency or InMemorySttIdempotencyStore(),
            topics=KafkaTopics(
                request="field-visit.stt.request.v1",
                result="field-visit.stt.result.v1",
                dlq="field-visit.stt.request.dlq.v1",
            ),
            lease_seconds=30,
            result_ttl_seconds=300,
        ),
        service,
        actions,
    )


def _sent_events(actions: list[tuple]) -> list[tuple[str, dict]]:
    return [
        (action[1], json.loads(action[3].decode("utf-8")))
        for action in actions
        if action[0] == "send"
    ]


@pytest.mark.asyncio
async def test_worker_publishes_processing_then_done_before_commit() -> None:
    provider = FakeSttProvider(text="정상 변환 결과")
    worker, service, actions = _worker(provider)
    try:
        await worker.handle_record(_request_record())
    finally:
        service.close()

    events = _sent_events(actions)
    assert [event["status"] for _, event in events] == ["PROCESSING", "DONE"]
    assert all(topic == "field-visit.stt.result.v1" for topic, _ in events)
    assert events[1][1]["textContent"] == "정상 변환 결과"
    assert actions[-1][0] == "commit"
    assert provider.call_count == 1
    assert all(len(action) == 4 for action in actions if action[0] == "send")


@pytest.mark.asyncio
async def test_duplicate_attempt_republishes_cached_result_without_inference() -> None:
    provider = FakeSttProvider(text="한 번만 변환")
    worker, service, actions = _worker(provider)
    try:
        record = _request_record()
        await worker.handle_record(record)
        await worker.handle_record(record)
    finally:
        service.close()

    events = _sent_events(actions)
    assert [event["status"] for _, event in events] == [
        "PROCESSING",
        "DONE",
        "DONE",
    ]
    assert provider.call_count == 1
    assert sum(action[0] == "commit" for action in actions) == 2


@pytest.mark.asyncio
async def test_lost_lease_does_not_publish_or_commit_final_result() -> None:
    provider = FakeSttProvider(text="소유권을 잃은 결과")
    worker, service, actions = _worker(provider, _LeaseLosingStore())
    try:
        with pytest.raises(LeaseBusyError):
            await worker.handle_record(_request_record())
    finally:
        service.close()

    events = _sent_events(actions)
    assert [event["status"] for _, event in events] == ["PROCESSING"]
    assert not any(action[0] == "commit" for action in actions)


@pytest.mark.asyncio
async def test_processing_failure_emits_safe_failed_event_and_commits() -> None:
    provider = FakeSttProvider(text="")
    worker, service, actions = _worker(provider)
    try:
        await worker.handle_record(_request_record())
    finally:
        service.close()

    events = _sent_events(actions)
    assert [event["status"] for _, event in events] == ["PROCESSING", "FAILED"]
    assert events[1][1]["failCode"] == "EMPTY_TRANSCRIPT"
    assert events[1][1]["retryable"] is False
    assert actions[-1][0] == "commit"


@pytest.mark.asyncio
async def test_invalid_message_goes_to_dlq_without_original_payload() -> None:
    provider = FakeSttProvider()
    worker, service, actions = _worker(provider)
    invalid = _request_record(
        {
            "schemaVersion": 1,
            "sttId": "stt-test",
            "objectKey": "secret/private/key.m4a",
        }
    )
    try:
        await worker.handle_record(invalid)
    finally:
        service.close()

    events = _sent_events(actions)
    assert len(events) == 1
    topic, event = events[0]
    assert topic == "field-visit.stt.request.dlq.v1"
    assert event["failCode"] == "INVALID_MESSAGE"
    assert "originalPayload" not in event
    assert "objectKey" not in event
    assert actions[-1][0] == "commit"
