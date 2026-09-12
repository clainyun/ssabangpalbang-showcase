"""Fake AIOKafkaConsumer for Gate 3B-A offset/seek/pause tests."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from typing import Any


@dataclass
class FakeRecord:
    topic: str
    partition: int
    offset: int
    key: bytes | None
    value: bytes | None


@dataclass
class FakeKafkaConsumer:
    records: list[FakeRecord]
    committed: dict[Any, int] = field(default_factory=dict)
    seeks: list[tuple[Any, int]] = field(default_factory=list)
    paused: set[Any] = field(default_factory=set)
    pause_calls: list[Any] = field(default_factory=list)
    resume_calls: list[Any] = field(default_factory=list)
    seek_calls: list[tuple[Any, int]] = field(default_factory=list)
    commit_calls: list[dict[Any, int]] = field(default_factory=list)
    delivered_offsets: list[int] = field(default_factory=list)
    started: bool = False
    stopped: bool = False
    commit_error: Exception | None = None
    _queue: asyncio.Queue[FakeRecord | None] = field(default_factory=asyncio.Queue)

    def __post_init__(self) -> None:
        for record in self.records:
            self._queue.put_nowait(record)

    async def start(self) -> None:
        self.started = True

    async def stop(self) -> None:
        self.stopped = True
        self._queue.put_nowait(None)

    def assignment(self) -> set[tuple[str, int]]:
        return {(record.topic, record.partition) for record in self.records}

    def __aiter__(self) -> "FakeKafkaConsumer":
        return self

    async def __anext__(self) -> FakeRecord:
        while True:
            item = await self._queue.get()
            if item is None:
                raise StopAsyncIteration
            tp = (item.topic, item.partition)
            if tp in self.paused:
                # Re-queue and wait briefly so resume can happen.
                self._queue.put_nowait(item)
                await asyncio.sleep(0.01)
                continue
            self.delivered_offsets.append(item.offset)
            return item

    async def commit(self, offsets: dict[Any, int]) -> None:
        self.commit_calls.append(dict(offsets))
        if self.commit_error is not None:
            raise self.commit_error
        if not isinstance(offsets, dict) or not offsets:
            raise ValueError("explicit partition offsets required")
        self.committed.update(offsets)

    def seek(self, partition: Any, offset: int) -> None:
        self.seeks.append((partition, offset))
        self.seek_calls.append((partition, offset))
        for record in self.records:
            key = (record.topic, record.partition)
            if key == partition or (
                hasattr(partition, "topic")
                and partition.topic == record.topic
                and partition.partition == record.partition
            ):
                if record.offset == offset:
                    self._queue.put_nowait(record)
                    return
        topic, part = _tp_parts(partition)
        self._queue.put_nowait(
            FakeRecord(
                topic=topic,
                partition=part,
                offset=offset,
                key=b"1",
                value=b"{}",
            )
        )

    def pause(self, *partitions: Any) -> None:
        for partition in partitions:
            normalized = _normalize_tp(partition)
            self.pause_calls.append(normalized)
            self.paused.add(normalized)

    def resume(self, *partitions: Any) -> None:
        for partition in partitions:
            normalized = _normalize_tp(partition)
            self.resume_calls.append(normalized)
            self.paused.discard(normalized)


def _normalize_tp(partition: Any) -> Any:
    if hasattr(partition, "topic"):
        return (partition.topic, partition.partition)
    return partition


def _tp_parts(partition: Any) -> tuple[str, int]:
    if hasattr(partition, "topic"):
        return partition.topic, int(partition.partition)
    topic, part = partition
    return str(topic), int(part)
