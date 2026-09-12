"""Single-report RAG indexing shared by the internal HTTP route."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.rag.config import RagSettings
from app.rag.db import (
    connect,
    delete_stale_report_chunks,
    find_report_by_id,
    report_exists,
    upsert_batch,
)
from app.rag.embedder import RagEmbedder
from app.rag.report_documents import build_report_documents


@dataclass(frozen=True)
class ReportIndexResult:
    report_id: int
    apartment_id: int | None
    indexed: bool
    chunk_count: int
    deleted_count: int
    skip_reason: str | None


class ReportRagDatabaseError(RuntimeError):
    """Database connection or operation failure during report indexing."""


def index_report(
    report_id: int,
    settings: RagSettings,
    embedder: RagEmbedder,
) -> ReportIndexResult:
    connection = _connect(settings)
    try:
        row = _find_report(connection, report_id)
        if row is None:
            reason = "NOT_VISIBLE" if _exists(connection, report_id) else "NOT_FOUND"
            return _skipped(report_id, reason)

        documents = build_report_documents(row)
        if not documents:
            deleted = _delete_stale(connection, report_id, [])
            _commit(connection)
            return ReportIndexResult(
                report_id=report_id,
                apartment_id=None,
                indexed=False,
                chunk_count=0,
                deleted_count=deleted,
                skip_reason="NO_CONTENT",
            )

        vectors = embedder.encode_documents(
            [document.content for document in documents],
            settings.embedding_batch_size,
        )
        current_keys = [document.reindex_key for document in documents]
        deleted = _delete_stale(connection, report_id, current_keys)
        inserted, updated, failed = _upsert(connection, documents, vectors)
        if failed:
            raise RuntimeError(
                f"리포트 RAG 문서 {failed}건을 저장하지 못했습니다."
            )
        return ReportIndexResult(
            report_id=report_id,
            apartment_id=row.apartment_id,
            indexed=True,
            chunk_count=inserted + updated,
            deleted_count=deleted,
            skip_reason=None,
        )
    finally:
        connection.close()


def _connect(settings: RagSettings) -> Any:
    try:
        return connect(settings)
    except Exception as exc:
        raise ReportRagDatabaseError(str(exc)) from exc


def _find_report(connection: Any, report_id: int) -> Any:
    try:
        return find_report_by_id(connection, report_id)
    except Exception as exc:
        raise ReportRagDatabaseError(str(exc)) from exc


def _exists(connection: Any, report_id: int) -> bool:
    try:
        return report_exists(connection, report_id)
    except Exception as exc:
        raise ReportRagDatabaseError(str(exc)) from exc


def _delete_stale(
    connection: Any,
    report_id: int,
    current_keys: list[str],
) -> int:
    try:
        return delete_stale_report_chunks(
            connection,
            report_id,
            current_keys,
        )
    except Exception as exc:
        raise ReportRagDatabaseError(str(exc)) from exc


def _upsert(
    connection: Any,
    documents: list[Any],
    vectors: Any,
) -> tuple[int, int, int]:
    try:
        return upsert_batch(connection, documents, vectors)
    except Exception as exc:
        raise ReportRagDatabaseError(str(exc)) from exc


def _commit(connection: Any) -> None:
    try:
        connection.commit()
    except Exception as exc:
        raise ReportRagDatabaseError(str(exc)) from exc


def _skipped(report_id: int, reason: str) -> ReportIndexResult:
    return ReportIndexResult(
        report_id=report_id,
        apartment_id=None,
        indexed=False,
        chunk_count=0,
        deleted_count=0,
        skip_reason=reason,
    )
