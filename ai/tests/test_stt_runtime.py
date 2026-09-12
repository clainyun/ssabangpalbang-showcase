"""Lifecycle ordering and readiness tests for the STT runtime."""

import pytest

from app.messaging.stt_runtime import SttRuntime


class _Worker:
    def __init__(self, order: list[str]) -> None:
        self.order = order
        self.running = False

    async def start(self) -> None:
        self.order.append("worker")
        self.running = True

    async def stop(self) -> None:
        self.running = False

    def is_running(self) -> bool:
        return self.running


class _Service:
    def __init__(self, order: list[str]) -> None:
        self.order = order
        self.ready = False
        self.closed = False

    def warmup(self) -> None:
        self.order.append("warmup")
        self.ready = True

    def is_ready(self) -> bool:
        return self.ready

    def close(self) -> None:
        self.closed = True


class _FailingWarmupService(_Service):
    def warmup(self) -> None:
        raise RuntimeError("model unavailable")


class _Idempotency:
    def __init__(self, order: list[str]) -> None:
        self.order = order
        self.closed = False

    async def ping(self) -> None:
        self.order.append("redis")

    async def close(self) -> None:
        self.closed = True


@pytest.mark.asyncio
async def test_runtime_warms_model_before_starting_consumer() -> None:
    order: list[str] = []
    worker = _Worker(order)
    service = _Service(order)
    idempotency = _Idempotency(order)
    runtime = SttRuntime(
        worker=worker,
        service=service,
        idempotency=idempotency,
    )

    await runtime.start()
    try:
        assert order == ["redis", "warmup", "worker"]
        assert runtime.is_healthy() is True
        service.ready = False
        assert runtime.is_healthy() is False
    finally:
        await runtime.stop()


@pytest.mark.asyncio
async def test_start_failure_closes_all_resources() -> None:
    order: list[str] = []
    worker = _Worker(order)
    service = _FailingWarmupService(order)
    idempotency = _Idempotency(order)
    runtime = SttRuntime(
        worker=worker,
        service=service,
        idempotency=idempotency,
    )

    with pytest.raises(RuntimeError, match="model unavailable"):
        await runtime.start()

    assert worker.running is False
    assert service.closed is True
    assert idempotency.closed is True
