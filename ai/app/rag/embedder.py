"""Embedding model adapter with database dimension safeguards.

Runs on EC2 CPU with ``dragonkue/multilingual-e5-small-ko-v2``. The e5 family
needs an asymmetric prefix on every input — documents get ``passage: `` and
queries get ``query: ``. Omitting it raises no error, it just quietly degrades
retrieval, so the prefixes are applied here and nowhere else.
"""

from __future__ import annotations

import threading
from typing import Any

from app.rag.config import RagSettings


EXPECTED_EMBEDDING_DIMENSION = 384

PASSAGE_PREFIX = "passage: "
QUERY_PREFIX = "query: "

_embedder: RagEmbedder | None = None
_embedder_lock = threading.Lock()


class RagEmbedder:
    def __init__(
        self,
        model_name: str,
        device: str,
        model: Any | None = None,
    ) -> None:
        if model is None:
            from sentence_transformers import SentenceTransformer

            model = SentenceTransformer(model_name, device=device)
        self._model = model
        self.model_name = model_name
        self.device = device
        dimension = self._model.get_sentence_embedding_dimension()
        if dimension != EXPECTED_EMBEDDING_DIMENSION:
            raise ValueError(_dimension_message(dimension))
        self.dimension = dimension

    def encode_documents(self, contents: list[str], batch_size: int) -> Any:
        """Embed documents for indexing. The prefix never reaches the database."""
        return self._encode(
            [f"{PASSAGE_PREFIX}{content}" for content in contents],
            batch_size,
        )

    def encode_query(self, question: str) -> Any:
        """Embed one search query and return a single vector."""
        return self._encode([f"{QUERY_PREFIX}{question}"], 1)[0]

    def _encode(self, texts: list[str], batch_size: int) -> Any:
        vectors = self._model.encode(
            texts,
            batch_size=batch_size,
            normalize_embeddings=True,
        )
        for vector in vectors:
            if len(vector) != self.dimension:
                raise ValueError(_dimension_message(len(vector)))
        return vectors


def get_shared_embedder(settings: RagSettings) -> RagEmbedder:
    """Load one embedding model per process for every HTTP RAG route."""
    global _embedder
    if _embedder is None:
        with _embedder_lock:
            if _embedder is None:
                _embedder = RagEmbedder(
                    settings.embedding_model,
                    settings.embedding_device,
                )
    return _embedder


def _dimension_message(actual: Any) -> str:
    return (
        f"임베딩 차원이 {EXPECTED_EMBEDDING_DIMENSION}이 아닙니다: {actual}. "
        f"apartment_rag_document.embedding은 "
        f"vector({EXPECTED_EMBEDDING_DIMENSION})입니다."
    )
