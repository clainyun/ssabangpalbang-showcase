"""Load local environment files for the AI service.

Order: process env > ai/.env.local > ai/.env
Never log secret values.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlparse

from dotenv import load_dotenv

_AI_ROOT = Path(__file__).resolve().parents[1]


def load_ai_env() -> None:
    """Load dotenv files if present. Existing process env wins."""
    load_dotenv(_AI_ROOT / ".env.local", override=False)
    load_dotenv(_AI_ROOT / ".env", override=False)


def env_bool(name: str, default: bool) -> bool:
    """Parse a boolean env value and reject ambiguous spellings."""
    value = os.getenv(name)
    if value is None or not value.strip():
        return default

    normalized = value.strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False
    raise ValueError(
        f"{name} must be one of: 1, true, yes, on, 0, false, no, off"
    )


def is_report_database_enabled() -> bool:
    """Return whether report database credentials should be required."""
    load_ai_env()
    return env_bool("AI_REPORT_DB_ENABLED", False)


def _env_int(name: str, default: int, *, minimum: int = 1) -> int:
    raw = os.getenv(name)
    if raw is None:
        return default
    value = int(raw)
    if value < minimum:
        raise ValueError(f"{name} must be at least {minimum}")
    return value


@dataclass(frozen=True)
class ReportDatabaseSettings:
    """Read-only PostgreSQL settings for AI-004 report input snapshots."""

    host: str
    port: int
    database: str
    user: str
    password: str = field(repr=False)
    connect_timeout_seconds: int = 5
    statement_timeout_ms: int = 5_000

    @classmethod
    def from_env(cls) -> "ReportDatabaseSettings":
        settings = cls(
            host=os.getenv("AI_REPORT_DB_HOST", "localhost").strip(),
            port=_env_int("AI_REPORT_DB_PORT", 5432),
            database=os.getenv("AI_REPORT_DB_NAME", "").strip(),
            user=os.getenv("AI_REPORT_DB_USER", "").strip(),
            password=os.getenv("AI_REPORT_DB_PASSWORD", ""),
            connect_timeout_seconds=_env_int(
                "AI_REPORT_DB_CONNECT_TIMEOUT_SECONDS", 5
            ),
            statement_timeout_ms=_env_int(
                "AI_REPORT_DB_STATEMENT_TIMEOUT_MS", 5_000
            ),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        required = {
            "AI_REPORT_DB_HOST": self.host,
            "AI_REPORT_DB_NAME": self.database,
            "AI_REPORT_DB_USER": self.user,
            "AI_REPORT_DB_PASSWORD": self.password,
        }
        missing = [name for name, value in required.items() if not value]
        if missing:
            raise ValueError(
                "missing required report database settings: "
                + ", ".join(missing)
            )
        if self.port > 65_535:
            raise ValueError("AI_REPORT_DB_PORT must be at most 65535")


@dataclass(frozen=True)
class SttSettings:
    """Runtime settings for the Kafka-backed STT worker."""

    enabled: bool
    kafka_bootstrap_servers: str
    request_topic: str
    result_topic: str
    dlq_topic: str
    consumer_group: str
    kafka_client_id: str
    max_poll_interval_ms: int
    provider: str
    model: str
    device: str
    compute_type: str
    openai_api_key: str = field(repr=False)
    openai_base_url: str
    openai_timeout_seconds: int
    max_concurrency: int
    timeout_seconds: int
    warmup_timeout_seconds: int
    fake_text: str
    audio_store_provider: str
    audio_normalizer: str
    ffmpeg_binary: str
    ffmpeg_timeout_seconds: int
    media_gateway_base_url: str
    media_gateway_internal_token: str
    media_gateway_timeout_seconds: int
    media_download_timeout_seconds: int
    redis_host: str
    redis_port: int
    redis_password: str | None
    redis_key_prefix: str
    redis_lease_seconds: int
    redis_result_ttl_seconds: int
    retry_delay_seconds: int

    @classmethod
    def from_env(cls) -> "SttSettings":
        settings = cls(
            enabled=env_bool("STT_ENABLED", False),
            kafka_bootstrap_servers=os.getenv(
                "KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"
            ),
            request_topic=os.getenv(
                "STT_KAFKA_REQUEST_TOPIC", "field-visit.stt.request.v1"
            ),
            result_topic=os.getenv(
                "STT_KAFKA_RESULT_TOPIC", "field-visit.stt.result.v1"
            ),
            dlq_topic=os.getenv(
                "STT_KAFKA_DLQ_TOPIC", "field-visit.stt.request.dlq.v1"
            ),
            consumer_group=os.getenv(
                "STT_KAFKA_CONSUMER_GROUP", "ssabangpalbang-ai-stt-v1"
            ),
            kafka_client_id=os.getenv(
                "STT_KAFKA_CLIENT_ID", "ssabangpalbang-ai-stt"
            ),
            max_poll_interval_ms=_env_int(
                "STT_KAFKA_MAX_POLL_INTERVAL_MS", 900_000
            ),
            provider=os.getenv("STT_PROVIDER", "openai").strip().lower(),
            model=os.getenv("STT_MODEL", "whisper-1").strip(),
            device=os.getenv("STT_DEVICE", "cpu").strip().lower(),
            compute_type=os.getenv("STT_COMPUTE_TYPE", "int8").strip().lower(),
            openai_api_key=os.getenv("STT_OPENAI_API_KEY", "").strip(),
            openai_base_url=os.getenv(
                "STT_OPENAI_BASE_URL", "https://api.openai.com/v1"
            ).strip().rstrip("/"),
            openai_timeout_seconds=_env_int(
                "STT_OPENAI_TIMEOUT_SECONDS", 120
            ),
            max_concurrency=_env_int("STT_MAX_CONCURRENCY", 1),
            timeout_seconds=_env_int("STT_TIMEOUT_SECONDS", 600),
            warmup_timeout_seconds=_env_int(
                "STT_WARMUP_TIMEOUT_SECONDS", 300
            ),
            fake_text=os.getenv(
                "STT_FAKE_TEXT", "테스트 음성 변환 결과입니다."
            ).strip(),
            audio_store_provider=os.getenv(
                "AUDIO_STORE_PROVIDER", "media-gateway"
            ).strip().lower(),
            audio_normalizer=os.getenv(
                "STT_AUDIO_NORMALIZER", "auto"
            ).strip().lower(),
            ffmpeg_binary=os.getenv("STT_FFMPEG_BINARY", "ffmpeg").strip(),
            ffmpeg_timeout_seconds=_env_int(
                "STT_FFMPEG_TIMEOUT_SECONDS", 120
            ),
            media_gateway_base_url=os.getenv(
                "MEDIA_GATEWAY_BASE_URL", ""
            ).strip().rstrip("/"),
            media_gateway_internal_token=os.getenv(
                "MEDIA_GATEWAY_INTERNAL_TOKEN", ""
            ).strip(),
            media_gateway_timeout_seconds=_env_int(
                "MEDIA_GATEWAY_TIMEOUT_SECONDS", 15
            ),
            media_download_timeout_seconds=_env_int(
                "MEDIA_DOWNLOAD_TIMEOUT_SECONDS", 120
            ),
            redis_host=os.getenv("REDIS_HOST", "localhost").strip(),
            redis_port=_env_int("REDIS_PORT", 6379),
            redis_password=os.getenv("REDIS_PASSWORD") or None,
            redis_key_prefix=os.getenv(
                "STT_REDIS_KEY_PREFIX", "stt:attempt"
            ).strip(),
            redis_lease_seconds=_env_int("STT_REDIS_LEASE_SECONDS", 900),
            redis_result_ttl_seconds=_env_int(
                "STT_REDIS_RESULT_TTL_SECONDS", 604_800
            ),
            retry_delay_seconds=_env_int("STT_RETRY_DELAY_SECONDS", 2),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        if self.provider not in {"openai", "faster-whisper", "fake"}:
            raise ValueError(
                "STT_PROVIDER must be one of: openai, faster-whisper, fake"
            )
        if not self.model:
            raise ValueError("STT_MODEL must not be blank")
        topics = {self.request_topic, self.result_topic, self.dlq_topic}
        if len(topics) != 3:
            raise ValueError("STT Kafka request, result, and DLQ topics must differ")
        if self.timeout_seconds * 1000 >= self.max_poll_interval_ms:
            raise ValueError(
                "STT_KAFKA_MAX_POLL_INTERVAL_MS must exceed "
                "STT_TIMEOUT_SECONDS"
            )
        if self.redis_lease_seconds <= self.timeout_seconds:
            raise ValueError(
                "STT_REDIS_LEASE_SECONDS must exceed STT_TIMEOUT_SECONDS"
            )
        if self.max_concurrency != 1:
            raise ValueError(
                "STT_MAX_CONCURRENCY must be 1; scale with Kafka consumers"
            )
        if self.enabled and self.provider == "openai":
            if not self.openai_api_key:
                raise ValueError(
                    "STT_OPENAI_API_KEY is required when OpenAI STT is enabled"
                )
            if self.audio_normalizer not in {"auto", "ffmpeg"}:
                raise ValueError(
                    "OpenAI STT requires STT_AUDIO_NORMALIZER=auto or ffmpeg"
                )
            parsed_base_url = urlparse(self.openai_base_url)
            if parsed_base_url.scheme != "https" or not parsed_base_url.hostname:
                raise ValueError("STT_OPENAI_BASE_URL must be an absolute HTTPS URL")
            if self.openai_timeout_seconds >= self.timeout_seconds:
                raise ValueError(
                    "STT_OPENAI_TIMEOUT_SECONDS must be less than "
                    "STT_TIMEOUT_SECONDS"
                )


@dataclass(frozen=True)
class ReportWorkerSettings:
    """Runtime settings for the Kafka-backed report worker (AI-007 / INF-007).

    max_poll_interval rationale (defaults):
    - AI_READ_TIMEOUT default 20s per LLM call
    - AI-005 up to 3 provider attempts + retry delays
    - AI-006 up to 3 provider attempts + retry delays
    - Backend acquire/progress/complete/fail calls
    - safety margin for GC / network jitter
    Default 1_800_000 ms (30 min) exceeds the STT-style 15 min budget because
    report generation chains two LLM stages.
    """

    enabled: bool
    kafka_bootstrap_servers: str
    kafka_topic: str
    kafka_group_id: str
    kafka_dlt_topic: str
    kafka_rejection_policy: str
    kafka_dlt_retention_ms: int
    kafka_auto_offset_reset: str
    kafka_max_poll_interval_ms: int
    retry_backoff_seconds: float
    backend_base_url: str
    backend_timeout_seconds: float
    internal_token: str = field(repr=False)

    @classmethod
    def from_env(cls) -> "ReportWorkerSettings":
        settings = cls(
            enabled=env_bool("REPORT_WORKER_ENABLED", False),
            kafka_bootstrap_servers=os.getenv(
                "REPORT_KAFKA_BOOTSTRAP_SERVERS",
                os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
            ).strip(),
            kafka_topic=os.getenv(
                "REPORT_KAFKA_TOPIC", "field-visit.report.request.v1"
            ).strip(),
            kafka_group_id=os.getenv(
                "REPORT_KAFKA_GROUP_ID", "ai.report-worker.v1"
            ).strip(),
            kafka_dlt_topic=os.getenv(
                "REPORT_KAFKA_DLT_TOPIC",
                "field-visit.report.request.dlq.v1",
            ).strip(),
            kafka_rejection_policy=os.getenv(
                "REPORT_KAFKA_REJECTION_POLICY", "block"
            ).strip().lower(),
            kafka_dlt_retention_ms=_env_int(
                "REPORT_KAFKA_DLT_RETENTION_MS", 604_800_000
            ),
            kafka_auto_offset_reset=os.getenv(
                "REPORT_KAFKA_AUTO_OFFSET_RESET", "earliest"
            ).strip().lower(),
            kafka_max_poll_interval_ms=_env_int(
                "REPORT_KAFKA_MAX_POLL_INTERVAL_MS", 1_800_000
            ),
            retry_backoff_seconds=float(
                os.getenv("REPORT_RETRY_BACKOFF_SECONDS", "2")
            ),
            backend_base_url=os.getenv("REPORT_BACKEND_BASE_URL", "").strip(),
            backend_timeout_seconds=float(
                os.getenv("REPORT_BACKEND_TIMEOUT_SECONDS", "15")
            ),
            internal_token=os.getenv("REPORT_INTERNAL_TOKEN", "").strip(),
        )
        settings.validate_common()
        if settings.enabled:
            settings.validate_for_enabled()
        return settings

    def validate_common(self) -> None:
        if self.kafka_rejection_policy not in {"block", "dlt"}:
            raise ValueError(
                "REPORT_KAFKA_REJECTION_POLICY must be block|dlt"
            )
        if self.kafka_dlt_retention_ms <= 0:
            raise ValueError("REPORT_KAFKA_DLT_RETENTION_MS must be > 0")
        if (
            self.kafka_topic
            and self.kafka_dlt_topic
            and self.kafka_topic == self.kafka_dlt_topic
        ):
            raise ValueError(
                "REPORT_KAFKA_TOPIC and REPORT_KAFKA_DLT_TOPIC must differ"
            )

    def validate_for_enabled(self) -> None:
        self.validate_common()
        missing: list[str] = []
        if not self.kafka_bootstrap_servers:
            missing.append("REPORT_KAFKA_BOOTSTRAP_SERVERS")
        if not self.kafka_topic:
            missing.append("REPORT_KAFKA_TOPIC")
        if not self.kafka_group_id:
            missing.append("REPORT_KAFKA_GROUP_ID")
        if self.kafka_auto_offset_reset not in {"earliest", "latest", "none"}:
            raise ValueError(
                "REPORT_KAFKA_AUTO_OFFSET_RESET must be earliest|latest|none"
            )
        if self.kafka_rejection_policy == "dlt" and not self.kafka_dlt_topic:
            missing.append("REPORT_KAFKA_DLT_TOPIC")
        if not self.backend_base_url:
            missing.append("REPORT_BACKEND_BASE_URL")
        if not self.internal_token:
            missing.append("REPORT_INTERNAL_TOKEN")
        if self.retry_backoff_seconds <= 0:
            raise ValueError("REPORT_RETRY_BACKOFF_SECONDS must be > 0")
        if self.backend_timeout_seconds <= 0:
            raise ValueError("REPORT_BACKEND_TIMEOUT_SECONDS must be > 0")
        if self.kafka_max_poll_interval_ms < 60_000:
            raise ValueError(
                "REPORT_KAFKA_MAX_POLL_INTERVAL_MS must be at least 60000"
            )
        if missing:
            raise ValueError(
                "missing required report worker settings when enabled: "
                + ", ".join(missing)
            )

    @property
    def uses_dlt(self) -> bool:
        return self.kafka_rejection_policy == "dlt"


def is_report_worker_enabled() -> bool:
    load_ai_env()
    return env_bool("REPORT_WORKER_ENABLED", False)
