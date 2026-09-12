"""Internal single-report RAG indexing API tests."""

from datetime import datetime, timezone
from types import SimpleNamespace

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api import rag_index
from app.rag import embedder as embedder_module
from app.rag import report_indexer
from app.rag.documents import SourceType
from app.rag.report_documents import ReportRow


class FakeConnection:
    def __init__(self) -> None:
        self.commit_count = 0
        self.closed = False

    def commit(self) -> None:
        self.commit_count += 1

    def close(self) -> None:
        self.closed = True


class FakeEmbedder:
    def encode_documents(
        self,
        contents: list[str],
        batch_size: int,
    ) -> list[list[float]]:
        return [[float(index), 1.0] for index, _ in enumerate(contents)]


class FakeRagStore:
    def __init__(self) -> None:
        self.reports: dict[int, ReportRow] = {}
        self.visibility: dict[int, str] = {}
        self.existing: set[int] = set()
        self.documents: dict[str, object] = {}

    def find_report(self, connection: object, report_id: int):
        if self.visibility.get(report_id) != "DONE":
            return None
        return self.reports.get(report_id)

    def add_report(
        self,
        row: ReportRow,
        visibility: str = "DONE",
    ) -> None:
        self.existing.add(row.id)
        self.reports[row.id] = row
        self.visibility[row.id] = visibility

    def report_exists(self, connection: object, report_id: int) -> bool:
        return report_id in self.existing

    def delete_stale(
        self,
        connection: object,
        report_id: int,
        current_keys: list[str],
    ) -> int:
        prefix = f"REPORT:{report_id}:"
        stale = [
            key
            for key in self.documents
            if key.startswith(prefix) and key not in current_keys
        ]
        for key in stale:
            del self.documents[key]
        return len(stale)

    def upsert(self, connection, documents, vectors):
        inserted = updated = 0
        for document, _vector in zip(documents, vectors, strict=True):
            if document.reindex_key in self.documents:
                updated += 1
            else:
                inserted += 1
            self.documents[document.reindex_key] = document
        connection.commit()
        return inserted, updated, 0


@pytest.fixture
def api(monkeypatch):
    settings = SimpleNamespace(
        embedding_batch_size=16,
        embedding_model="test-model",
        embedding_device="cpu",
    )
    store = FakeRagStore()
    model_loads = {"count": 0}

    def make_embedder(model_name: str, device: str) -> FakeEmbedder:
        model_loads["count"] += 1
        return FakeEmbedder()

    embedder_module._embedder = None
    monkeypatch.setattr(embedder_module, "RagEmbedder", make_embedder)
    monkeypatch.setattr(rag_index, "is_rag_enabled", lambda: True)
    monkeypatch.setattr(
        rag_index.RagSettings,
        "from_env",
        classmethod(lambda cls: settings),
    )
    monkeypatch.setattr(
        report_indexer,
        "connect",
        lambda current_settings: FakeConnection(),
    )
    monkeypatch.setattr(report_indexer, "find_report_by_id", store.find_report)
    monkeypatch.setattr(report_indexer, "report_exists", store.report_exists)
    monkeypatch.setattr(
        report_indexer,
        "delete_stale_report_chunks",
        store.delete_stale,
    )
    monkeypatch.setattr(report_indexer, "upsert_batch", store.upsert)

    app = FastAPI()
    app.include_router(rag_index.router)
    with TestClient(app) as client:
        yield client, store, model_loads
    embedder_module._embedder = None


def _done_report(report_id: int = 73, apartment_id: int = 44) -> ReportRow:
    completed_at = datetime(2026, 8, 5, tzinfo=timezone.utc)
    return ReportRow(
        id=report_id,
        apartment_id=apartment_id,
        result_json={
            "summary": "교통과 생활 편의 시설을 함께 확인한 공개 리포트 요약입니다.",
        },
        completed_at=completed_at,
        updated_at=completed_at,
        apartment_name="테스트 아파트",
    )


def test_done_report_is_indexed_with_source_metadata(api) -> None:
    client, store, _ = api
    store.add_report(_done_report())

    response = client.post("/internal/v1/rag/reports/73")

    assert response.status_code == 200
    assert response.json() == {
        "reportId": 73,
        "apartmentId": 44,
        "indexed": True,
        "chunkCount": 1,
        "deletedCount": 0,
        "skipReason": None,
    }
    document = store.documents["REPORT:73:0"]
    assert document.apartment_id == 44
    assert document.source_type is SourceType.REPORT
    assert document.source_id == 73
    assert document.source_at == datetime(2026, 8, 5, tzinfo=timezone.utc)


def test_reindexing_same_report_does_not_add_chunks(api) -> None:
    client, store, _ = api
    store.add_report(_done_report())

    first = client.post("/internal/v1/rag/reports/73")
    count_after_first = len(store.documents)
    second = client.post("/internal/v1/rag/reports/73")

    assert first.status_code == 200
    assert second.status_code == 200
    assert len(store.documents) == count_after_first == 1


def test_missing_report_returns_not_found_skip(api) -> None:
    client, _, _ = api

    response = client.post("/internal/v1/rag/reports/999999")

    assert response.status_code == 200
    assert response.json()["indexed"] is False
    assert response.json()["skipReason"] == "NOT_FOUND"


@pytest.mark.parametrize(
    "visibility_case",
    ["PENDING", "STUDY_DELETED", "STUDY_CANCELED"],
)
def test_non_visible_report_returns_not_visible_skip(
    api,
    visibility_case: str,
) -> None:
    client, store, _ = api
    store.add_report(_done_report(), visibility_case)

    response = client.post("/internal/v1/rag/reports/73")

    assert visibility_case
    assert response.status_code == 200
    assert response.json()["indexed"] is False
    assert response.json()["skipReason"] == "NOT_VISIBLE"


def test_report_without_public_content_deletes_old_chunks(api) -> None:
    client, store, _ = api
    row = _done_report()
    store.add_report(ReportRow(
        id=row.id,
        apartment_id=row.apartment_id,
        result_json={},
        completed_at=row.completed_at,
        updated_at=row.updated_at,
        apartment_name=row.apartment_name,
    ))
    store.documents["REPORT:73:0"] = object()

    response = client.post("/internal/v1/rag/reports/73")

    assert response.status_code == 200
    assert response.json()["skipReason"] == "NO_CONTENT"
    assert response.json()["deletedCount"] == 1
    assert store.documents == {}


def test_rag_disabled_returns_existing_error_code(api, monkeypatch) -> None:
    client, _, _ = api
    monkeypatch.setattr(rag_index, "is_rag_enabled", lambda: False)

    response = client.post("/internal/v1/rag/reports/73")

    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "RAG_DISABLED"


def test_report_id_must_be_positive(api) -> None:
    client, _, _ = api

    response = client.post("/internal/v1/rag/reports/0")

    assert response.status_code == 422


def test_consecutive_requests_load_embedding_model_once(api) -> None:
    client, store, model_loads = api
    store.add_report(_done_report())

    assert client.post("/internal/v1/rag/reports/73").status_code == 200
    assert client.post("/internal/v1/rag/reports/73").status_code == 200

    assert model_loads["count"] == 1
