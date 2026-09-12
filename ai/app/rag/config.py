"""Environment-backed settings for the apartment RAG indexer."""

from __future__ import annotations

import os
from dataclasses import dataclass, field

from app.config import env_bool, load_ai_env


def _positive_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    value = default if raw is None else int(raw)
    if value < 1:
        raise ValueError(f"{name} must be at least 1")
    return value


def is_rag_enabled() -> bool:
    """Return whether database-backed RAG requests are available."""
    load_ai_env()
    return env_bool("RAG_ENABLED", False)


@dataclass(frozen=True)
class RagSettings:
    """Database and embedding settings without secret-bearing repr output."""

    host: str
    port: int
    database: str
    user: str
    password: str = field(repr=False)
    embedding_model: str = "dragonkue/multilingual-e5-small-ko-v2"
    embedding_device: str = "cpu"
    embedding_batch_size: int = 16

    @classmethod
    def from_env(cls) -> "RagSettings":
        load_ai_env()
        settings = cls(
            host=os.getenv("RAG_DB_HOST", "").strip(),
            port=_positive_int("RAG_DB_PORT", 5432),
            database=os.getenv("RAG_DB_NAME", "").strip(),
            user=os.getenv("RAG_DB_USER", "").strip(),
            password=os.getenv("RAG_DB_PASSWORD", ""),
            embedding_model=os.getenv(
                "RAG_EMBEDDING_MODEL",
                "dragonkue/multilingual-e5-small-ko-v2",
            ).strip(),
            embedding_device=os.getenv(
                "RAG_EMBEDDING_DEVICE", "cpu"
            ).strip(),
            embedding_batch_size=_positive_int(
                "RAG_EMBEDDING_BATCH_SIZE", 16
            ),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        required = {
            "RAG_DB_HOST": self.host,
            "RAG_DB_NAME": self.database,
            "RAG_DB_USER": self.user,
            "RAG_DB_PASSWORD": self.password,
            "RAG_EMBEDDING_MODEL": self.embedding_model,
            "RAG_EMBEDDING_DEVICE": self.embedding_device,
        }
        missing = [name for name, value in required.items() if not value]
        if missing:
            raise ValueError(
                "missing required RAG settings: " + ", ".join(missing)
            )
        if self.port > 65_535:
            raise ValueError("RAG_DB_PORT must be at most 65535")
