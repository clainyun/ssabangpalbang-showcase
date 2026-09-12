"""Single-connection PostgreSQL access for apartment and report indexing."""

from __future__ import annotations

from collections.abc import Iterator, Sequence
import logging
from typing import Any

from app.rag.config import RagSettings
from app.rag.documents import ApartmentRow, RagDocument
from app.rag.report_documents import ReportRow


LOGGER = logging.getLogger("index_public")


_SELECT_APARTMENTS = """
SELECT id, name, address, district_name, dong_name,
       household_count, completion_year_month, parking_space_count, updated_at
FROM apartment
WHERE (%(ids)s::bigint[] IS NULL OR id = ANY(%(ids)s))
ORDER BY id
"""

_COUNT_APARTMENTS = """
SELECT count(*)
FROM apartment
WHERE (%(ids)s::bigint[] IS NULL OR id = ANY(%(ids)s))
"""

_UPSERT_DOCUMENT = """
INSERT INTO apartment_rag_document
    (apartment_id, source_type, source_id, content, embedding, source_at,
     reindex_key)
VALUES (%s, %s, %s, %s, %s, %s, %s)
ON CONFLICT (reindex_key) DO UPDATE SET
    content = EXCLUDED.content,
    embedding = EXCLUDED.embedding,
    source_at = EXCLUDED.source_at
RETURNING (xmax = 0) AS inserted
"""


_REPORT_VISIBILITY = """
    r.status = 'DONE'
    AND s.deleted_at IS NULL
    AND s.status <> 'CANCELED'
"""

_SELECT_REPORTS = f"""
SELECT r.id, r.apartment_id, r.result_json, r.completed_at, r.updated_at,
       a.name AS apartment_name
FROM report r
JOIN study s ON s.id = r.study_id
JOIN apartment a ON a.id = r.apartment_id
WHERE {_REPORT_VISIBILITY}
  AND (%(ids)s::bigint[] IS NULL OR r.apartment_id = ANY(%(ids)s))
ORDER BY r.id
"""

_SELECT_REPORT_BY_ID = f"""
SELECT r.id, r.apartment_id, r.result_json, r.completed_at, r.updated_at,
       a.name AS apartment_name
FROM report r
JOIN study s ON s.id = r.study_id
JOIN apartment a ON a.id = r.apartment_id
WHERE r.id = %(report_id)s
  AND {_REPORT_VISIBILITY}
"""

_REPORT_EXISTS = """
SELECT 1
FROM report
WHERE id = %(report_id)s
"""

_COUNT_REPORTS = f"""
SELECT
    count(*) FILTER (WHERE {_REPORT_VISIBILITY}) AS indexable,
    count(*) FILTER (WHERE r.status <> 'DONE') AS not_done,
    count(*) FILTER (
        WHERE r.status = 'DONE'
          AND (s.deleted_at IS NOT NULL OR s.status = 'CANCELED')
    ) AS study_excluded
FROM report r
JOIN study s ON s.id = r.study_id
WHERE (%(ids)s::bigint[] IS NULL OR r.apartment_id = ANY(%(ids)s))
"""

_DELETE_STALE_REPORT_CHUNKS = """
DELETE FROM apartment_rag_document
WHERE source_type = 'REPORT'
  AND source_id = %(report_id)s
  AND NOT (reindex_key = ANY(%(current_keys)s::varchar[]))
"""

_DELETE_ALL_REPORT_CHUNKS = """
DELETE FROM apartment_rag_document
WHERE source_type = 'REPORT'
  AND source_id = %(report_id)s
"""

_DELETE_INELIGIBLE_REPORT_DOCUMENTS = f"""
DELETE FROM apartment_rag_document d
WHERE d.source_type = 'REPORT'
  AND (%(ids)s::bigint[] IS NULL OR d.apartment_id = ANY(%(ids)s::bigint[]))
  AND NOT EXISTS (
      SELECT 1
      FROM report r
      JOIN study s ON s.id = r.study_id
      WHERE r.id = d.source_id
        AND r.apartment_id = d.apartment_id
        AND {_REPORT_VISIBILITY}
  )
"""


def connect(settings: RagSettings) -> Any:
    import psycopg
    from pgvector.psycopg import register_vector

    connection = psycopg.connect(
        host=settings.host,
        port=settings.port,
        dbname=settings.database,
        user=settings.user,
        password=settings.password,
    )
    register_vector(connection)
    return connection


def count_apartments(connection: Any, apartment_ids: list[int] | None) -> int:
    with connection.cursor() as cursor:
        cursor.execute(_COUNT_APARTMENTS, {"ids": apartment_ids})
        return int(cursor.fetchone()[0])


def stream_apartment_batches(
    connection: Any,
    apartment_ids: list[int] | None,
    batch_size: int,
) -> Iterator[list[ApartmentRow]]:
    with connection.cursor(name="rag_public_apartments", withhold=True) as cursor:
        cursor.execute(_SELECT_APARTMENTS, {"ids": apartment_ids})
        while rows := cursor.fetchmany(batch_size):
            yield [ApartmentRow(*row) for row in rows]


def count_reports(
    connection: Any,
    apartment_ids: list[int] | None,
) -> tuple[int, int, int]:
    """Return ``(indexable, excluded_not_done, excluded_study)`` counts."""
    with connection.cursor() as cursor:
        cursor.execute(_COUNT_REPORTS, {"ids": apartment_ids})
        indexable, not_done, study_excluded = cursor.fetchone()
        return int(indexable), int(not_done), int(study_excluded)


def stream_report_batches(
    connection: Any,
    apartment_ids: list[int] | None,
    batch_size: int,
) -> Iterator[list[ReportRow]]:
    with connection.cursor(name="rag_reports", withhold=True) as cursor:
        cursor.execute(_SELECT_REPORTS, {"ids": apartment_ids})
        while rows := cursor.fetchmany(batch_size):
            yield [ReportRow(*row) for row in rows]


def find_report_by_id(connection: Any, report_id: int) -> ReportRow | None:
    with connection.cursor() as cursor:
        cursor.execute(_SELECT_REPORT_BY_ID, {"report_id": report_id})
        row = cursor.fetchone()
        return None if row is None else ReportRow(*row)


def report_exists(connection: Any, report_id: int) -> bool:
    with connection.cursor() as cursor:
        cursor.execute(_REPORT_EXISTS, {"report_id": report_id})
        return cursor.fetchone() is not None


def delete_stale_report_chunks(
    connection: Any,
    report_id: int,
    current_keys: Sequence[str],
) -> int:
    """Drop chunks left behind when a report shrinks to fewer chunks.

    Without this, a report that goes from five chunks to three keeps
    ``REPORT:{id}:3`` and ``REPORT:{id}:4`` alive and searchable.
    """
    with connection.cursor() as cursor:
        if current_keys:
            cursor.execute(
                _DELETE_STALE_REPORT_CHUNKS,
                {"report_id": report_id, "current_keys": list(current_keys)},
            )
        else:
            cursor.execute(
                _DELETE_ALL_REPORT_CHUNKS,
                {"report_id": report_id},
            )
        return cursor.rowcount


def delete_ineligible_report_documents(
    connection: Any,
    apartment_ids: list[int] | None,
) -> int:
    """Delete REPORT chunks whose live report is no longer public/indexable."""
    if apartment_ids == []:
        return 0
    with connection.cursor() as cursor:
        cursor.execute(
            _DELETE_INELIGIBLE_REPORT_DOCUMENTS,
            {"ids": apartment_ids},
        )
        return cursor.rowcount


def upsert_batch(
    connection: Any,
    documents: Sequence[RagDocument],
    vectors: Sequence[Any],
) -> tuple[int, int, int]:
    inserted = 0
    updated = 0
    failed = 0
    with connection.cursor() as cursor:
        for document, vector in zip(documents, vectors, strict=True):
            cursor.execute("SAVEPOINT rag_document_item")
            try:
                cursor.execute(
                    _UPSERT_DOCUMENT,
                    (
                        document.apartment_id,
                        document.source_type.value,
                        document.source_id,
                        document.content,
                        vector,
                        document.source_at,
                        document.reindex_key,
                    ),
                )
                was_inserted = bool(cursor.fetchone()[0])
                cursor.execute("RELEASE SAVEPOINT rag_document_item")
                inserted += int(was_inserted)
                updated += int(not was_inserted)
            except Exception as exc:
                cursor.execute("ROLLBACK TO SAVEPOINT rag_document_item")
                cursor.execute("RELEASE SAVEPOINT rag_document_item")
                LOGGER.warning(
                    "문서 저장 실패(reindex_key=%s): %s",
                    document.reindex_key,
                    exc,
                )
                failed += 1
    connection.commit()
    return inserted, updated, failed
