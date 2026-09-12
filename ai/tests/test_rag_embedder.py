"""Embedder prefix, dimension, and default-setting tests. The model is never loaded."""

import pytest

from app.rag.config import RagSettings
from app.rag.embedder import (
    EXPECTED_EMBEDDING_DIMENSION,
    PASSAGE_PREFIX,
    QUERY_PREFIX,
    RagEmbedder,
)


EXPECTED_MODEL = "dragonkue/multilingual-e5-small-ko-v2"


class FakeSentenceTransformer:
    def __init__(self, dimension: int = EXPECTED_EMBEDDING_DIMENSION) -> None:
        self.dimension = dimension
        self.seen_texts: list[str] = []
        self.seen_kwargs: dict = {}

    def get_sentence_embedding_dimension(self) -> int:
        return self.dimension

    def encode(self, texts: list[str], **kwargs: object) -> list[list[float]]:
        self.seen_texts = list(texts)
        self.seen_kwargs = dict(kwargs)
        return [[0.0] * self.dimension for _ in texts]


def _embedder(dimension: int = EXPECTED_EMBEDDING_DIMENSION) -> tuple[RagEmbedder, FakeSentenceTransformer]:
    model = FakeSentenceTransformer(dimension)
    return RagEmbedder(EXPECTED_MODEL, "cpu", model=model), model


def test_expected_dimension_matches_the_migration() -> None:
    assert EXPECTED_EMBEDDING_DIMENSION == 384


def test_expected_prefix_constants_are_asymmetric() -> None:
    assert PASSAGE_PREFIX == "passage: "
    assert QUERY_PREFIX == "query: "
    assert PASSAGE_PREFIX != QUERY_PREFIX


def test_e1_documents_get_the_passage_prefix() -> None:
    embedder, model = _embedder()

    embedder.encode_documents(["래미안 옥수 리버젠 임장 리포트 본문"], 16)

    assert model.seen_texts == ["passage: 래미안 옥수 리버젠 임장 리포트 본문"]


def test_e2_queries_get_the_query_prefix() -> None:
    embedder, model = _embedder()

    embedder.encode_query("교통 어때요?")

    assert model.seen_texts == ["query: 교통 어때요?"]


def test_e3_encode_query_returns_a_single_vector() -> None:
    embedder, _ = _embedder()

    vector = embedder.encode_query("교통 어때요?")

    assert len(vector) == EXPECTED_EMBEDDING_DIMENSION
    assert all(isinstance(value, float) for value in vector)


def test_e4_wrong_model_dimension_is_rejected_at_construction() -> None:
    with pytest.raises(ValueError) as exc:
        _embedder(dimension=768)

    assert "vector(384)" in str(exc.value)
    assert "768" in str(exc.value)


@pytest.mark.parametrize("dimension", [383, 385])
def test_adjacent_wrong_model_dimensions_are_rejected(dimension: int) -> None:
    with pytest.raises(ValueError) as exc:
        _embedder(dimension=dimension)

    assert "vector(384)" in str(exc.value)
    assert str(dimension) in str(exc.value)


def test_e4_wrong_vector_dimension_is_rejected_at_encode() -> None:
    embedder, model = _embedder()
    model.dimension = 1024  # model starts returning a different width

    with pytest.raises(ValueError) as exc:
        embedder.encode_documents(["본문"], 16)

    assert "vector(384)" in str(exc.value)
    assert "1024" in str(exc.value)


def test_e5_the_unprefixed_encode_method_is_gone() -> None:
    embedder, _ = _embedder()

    assert not hasattr(embedder, "encode")


def test_e6_every_document_in_a_batch_is_prefixed() -> None:
    embedder, model = _embedder()

    embedder.encode_documents(["첫 문단", "둘째 문단", "셋째 문단"], 16)

    assert model.seen_texts == [
        "passage: 첫 문단",
        "passage: 둘째 문단",
        "passage: 셋째 문단",
    ]


def test_encoding_does_not_mutate_the_document_content() -> None:
    embedder, model = _embedder()
    contents = ["DB에 저장할 원문"]

    embedder.encode_documents(contents, 16)

    assert contents == ["DB에 저장할 원문"]
    assert model.seen_texts == ["passage: DB에 저장할 원문"]


def test_embeddings_are_normalized() -> None:
    # search.py computes similarity as 1 - cosine_distance, which only holds
    # for normalized vectors.
    embedder, model = _embedder()

    embedder.encode_documents(["본문"], 16)

    assert model.seen_kwargs["normalize_embeddings"] is True


def test_batch_size_is_passed_through() -> None:
    embedder, model = _embedder()

    embedder.encode_documents(["본문"], 32)

    assert model.seen_kwargs["batch_size"] == 32


def _settings(**overrides: object) -> RagSettings:
    values = {
        "host": "localhost",
        "port": 5432,
        "database": "db",
        "user": "user",
        "password": "secret-password",
    }
    values.update(overrides)
    return RagSettings(**values)


def test_dataclass_embedding_defaults_match_the_new_model() -> None:
    settings = _settings()

    assert settings.embedding_model == EXPECTED_MODEL
    assert settings.embedding_device == "cpu"
    assert settings.embedding_batch_size == 16


def test_from_env_uses_the_new_model_default(monkeypatch) -> None:
    monkeypatch.setattr("app.rag.config.load_ai_env", lambda: None)
    monkeypatch.setenv("RAG_DB_HOST", "localhost")
    monkeypatch.setenv("RAG_DB_NAME", "db")
    monkeypatch.setenv("RAG_DB_USER", "user")
    monkeypatch.setenv("RAG_DB_PASSWORD", "password")
    monkeypatch.delenv("RAG_EMBEDDING_MODEL", raising=False)

    settings = RagSettings.from_env()

    assert settings.embedding_model == EXPECTED_MODEL


def test_from_env_prefers_configured_embedding_model(monkeypatch) -> None:
    monkeypatch.setenv("RAG_DB_HOST", "localhost")
    monkeypatch.setenv("RAG_DB_NAME", "db")
    monkeypatch.setenv("RAG_DB_USER", "user")
    monkeypatch.setenv("RAG_DB_PASSWORD", "password")
    monkeypatch.setenv("RAG_EMBEDDING_MODEL", "custom/model")

    assert RagSettings.from_env().embedding_model == "custom/model"


def test_rag_settings_repr_hides_password() -> None:
    assert "secret-password" not in repr(_settings())
