"""Redis-backed idempotency lease and final-result cache for STT attempts."""

from __future__ import annotations

import asyncio
import time
from abc import ABC, abstractmethod

from pydantic import ValidationError

from app.config import SttSettings
from app.schemas.stt import (
    STT_FINAL_RESULT_ADAPTER,
    SttDoneResult,
    SttFailedResult,
    SttFinalResult,
)


class SttIdempotencyStore(ABC):
    @abstractmethod
    async def get_result(self, key: str) -> SttFinalResult | None:
        """Return a previously completed result, if any."""

    @abstractmethod
    async def acquire(self, key: str, token: str, ttl_seconds: int) -> bool:
        """Acquire a lease for one sttId + attemptNo."""

    @abstractmethod
    async def extend(self, key: str, token: str, ttl_seconds: int) -> bool:
        """Extend a lease only when the caller still owns it."""

    @abstractmethod
    async def save_result_if_owner(
        self,
        key: str,
        token: str,
        result: SttDoneResult | SttFailedResult,
        ttl_seconds: int,
    ) -> bool:
        """Cache the final event only while the caller owns the lease."""

    @abstractmethod
    async def release(self, key: str, token: str) -> None:
        """Release a caller-owned lease."""

    async def close(self) -> None:
        """Close backing resources."""


class RedisSttIdempotencyStore(SttIdempotencyStore):
    _EXTEND_SCRIPT = """
    if redis.call('get', KEYS[1]) == ARGV[1] then
      return redis.call('expire', KEYS[1], ARGV[2])
    end
    return 0
    """
    _RELEASE_SCRIPT = """
    if redis.call('get', KEYS[1]) == ARGV[1] then
      return redis.call('del', KEYS[1])
    end
    return 0
    """
    _SAVE_RESULT_SCRIPT = """
    if redis.call('get', KEYS[1]) == ARGV[1] then
      redis.call('set', KEYS[2], ARGV[2], 'EX', ARGV[3])
      return 1
    end
    return 0
    """

    def __init__(
        self,
        *,
        host: str,
        port: int,
        password: str | None,
        prefix: str,
    ) -> None:
        try:
            from redis.asyncio import Redis
        except ImportError as exc:  # pragma: no cover - deployment dependency guard
            raise RuntimeError("Redis dependency is unavailable") from exc
        self._redis = Redis(
            host=host,
            port=port,
            password=password,
            decode_responses=True,
        )
        self._prefix = prefix.rstrip(":")

    def _lease_key(self, key: str) -> str:
        return f"{self._prefix}:{key}:lease"

    def _result_key(self, key: str) -> str:
        return f"{self._prefix}:{key}:result"

    async def ping(self) -> None:
        await self._redis.ping()

    async def get_result(self, key: str) -> SttFinalResult | None:
        cache_key = self._result_key(key)
        payload = await self._redis.get(cache_key)
        if payload is None:
            return None
        try:
            return STT_FINAL_RESULT_ADAPTER.validate_json(payload)
        except ValidationError:
            await self._redis.delete(cache_key)
            return None

    async def acquire(self, key: str, token: str, ttl_seconds: int) -> bool:
        acquired = await self._redis.set(
            self._lease_key(key),
            token,
            ex=ttl_seconds,
            nx=True,
        )
        return bool(acquired)

    async def extend(self, key: str, token: str, ttl_seconds: int) -> bool:
        result = await self._redis.eval(
            self._EXTEND_SCRIPT,
            1,
            self._lease_key(key),
            token,
            ttl_seconds,
        )
        return bool(result)

    async def save_result_if_owner(
        self,
        key: str,
        token: str,
        result: SttDoneResult | SttFailedResult,
        ttl_seconds: int,
    ) -> bool:
        saved = await self._redis.eval(
            self._SAVE_RESULT_SCRIPT,
            2,
            self._lease_key(key),
            self._result_key(key),
            token,
            result.model_dump_json(),
            ttl_seconds,
        )
        return bool(saved)

    async def release(self, key: str, token: str) -> None:
        await self._redis.eval(
            self._RELEASE_SCRIPT,
            1,
            self._lease_key(key),
            token,
        )

    async def close(self) -> None:
        await self._redis.aclose()


class InMemorySttIdempotencyStore(SttIdempotencyStore):
    """Process-local test implementation with lease expiration semantics."""

    def __init__(self) -> None:
        self._leases: dict[str, tuple[str, float]] = {}
        self._results: dict[
            str, tuple[SttDoneResult | SttFailedResult, float]
        ] = {}
        self._lock = asyncio.Lock()

    async def get_result(self, key: str) -> SttFinalResult | None:
        async with self._lock:
            cached = self._results.get(key)
            if cached is None:
                return None
            result, expires_at = cached
            if expires_at <= time.monotonic():
                self._results.pop(key, None)
                return None
            return result

    async def acquire(self, key: str, token: str, ttl_seconds: int) -> bool:
        async with self._lock:
            existing = self._leases.get(key)
            now = time.monotonic()
            if existing is not None and existing[1] > now:
                return False
            self._leases[key] = (token, now + ttl_seconds)
            return True

    async def extend(self, key: str, token: str, ttl_seconds: int) -> bool:
        async with self._lock:
            existing = self._leases.get(key)
            if existing is None or existing[0] != token:
                return False
            self._leases[key] = (token, time.monotonic() + ttl_seconds)
            return True

    async def save_result_if_owner(
        self,
        key: str,
        token: str,
        result: SttDoneResult | SttFailedResult,
        ttl_seconds: int,
    ) -> bool:
        async with self._lock:
            existing = self._leases.get(key)
            now = time.monotonic()
            if (
                existing is None
                or existing[0] != token
                or existing[1] <= now
            ):
                return False
            self._results[key] = (result, time.monotonic() + ttl_seconds)
            return True

    async def release(self, key: str, token: str) -> None:
        async with self._lock:
            existing = self._leases.get(key)
            if existing is not None and existing[0] == token:
                self._leases.pop(key, None)


def create_idempotency_store(
    settings: SttSettings,
) -> RedisSttIdempotencyStore:
    return RedisSttIdempotencyStore(
        host=settings.redis_host,
        port=settings.redis_port,
        password=settings.redis_password,
        prefix=settings.redis_key_prefix,
    )
