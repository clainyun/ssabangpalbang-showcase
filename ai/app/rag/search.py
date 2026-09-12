"""Read-only vector search over indexed report documents (AI-009).

Separate from ``app.rag.db``, which owns the indexing writes.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime
from typing import Any


SEARCH_REPORT_DOCUMENTS = """
SELECT d.source_id,
       d.content,
       d.source_at,
       1 - (d.embedding <=> %(query_vec)s) AS similarity
FROM apartment_rag_document d
JOIN report r
  ON r.id = d.source_id
 AND r.apartment_id = d.apartment_id
JOIN study s ON s.id = r.study_id
WHERE d.apartment_id = %(apartment_id)s
  AND d.source_type = 'REPORT'
  AND d.embedding IS NOT NULL
  AND r.status = 'DONE'
  AND s.deleted_at IS NULL
  AND s.status <> 'CANCELED'
ORDER BY d.embedding <=> %(query_vec)s
LIMIT %(top_k)s
"""
"""Cosine *distance* ordering (ascending) with similarity flipped to 1 - d.

Correct only because indexing normalizes embeddings (slice 3, ``normalize_embeddings=True``).
``source_type = 'REPORT'`` is mandatory: public apartment documents leaking in
would make a "report-based" answer cite public data instead.
"""

SELECT_APARTMENT_PROFILE = """
SELECT name,
       address,
       district_name,
       dong_name,
       household_count,
       completion_year_month,
       parking_space_count
FROM apartment
WHERE id = %(apartment_id)s
"""

REPORT_DOCUMENTS_EXIST = """
SELECT EXISTS (
    SELECT 1
    FROM apartment_rag_document d
    JOIN report r
      ON r.id = d.source_id
     AND r.apartment_id = d.apartment_id
    JOIN study s ON s.id = r.study_id
    WHERE d.apartment_id = %(apartment_id)s
      AND d.source_type = 'REPORT'
      AND d.embedding IS NOT NULL
      AND r.status = 'DONE'
      AND s.deleted_at IS NULL
      AND s.status <> 'CANCELED'
    LIMIT 1
)
"""


@dataclass(frozen=True)
class ReportChunkHit:
    source_id: int
    content: str
    source_at: datetime | None
    similarity: float


@dataclass(frozen=True)
class ApartmentProfile:
    name: str
    address: str | None
    district_name: str | None
    dong_name: str | None
    household_count: int | None
    completion_year_month: str | None
    parking_space_count: int | None


def search_report_documents(
    connection: Any,
    apartment_id: int,
    query_vector: Any,
    top_k: int,
) -> list[ReportChunkHit]:
    with connection.cursor() as cursor:
        cursor.execute(
            SEARCH_REPORT_DOCUMENTS,
            {
                "query_vec": query_vector,
                "apartment_id": apartment_id,
                "top_k": top_k,
            },
        )
        rows = cursor.fetchall()
    return [
        ReportChunkHit(
            source_id=int(row[0]),
            content=row[1],
            source_at=row[2],
            similarity=float(row[3]),
        )
        for row in rows
    ]


def fetch_apartment_profile(
    connection: Any, apartment_id: int
) -> ApartmentProfile | None:
    with connection.cursor() as cursor:
        cursor.execute(
            SELECT_APARTMENT_PROFILE, {"apartment_id": apartment_id}
        )
        row = cursor.fetchone()
    if row is None:
        return None
    return ApartmentProfile(
        name=row[0],
        address=row[1],
        district_name=row[2],
        dong_name=row[3],
        household_count=row[4],
        completion_year_month=row[5],
        parking_space_count=row[6],
    )


def report_documents_exist(connection: Any, apartment_id: int) -> bool:
    with connection.cursor() as cursor:
        cursor.execute(
            REPORT_DOCUMENTS_EXIST, {"apartment_id": apartment_id}
        )
        row = cursor.fetchone()
    return bool(row and row[0])


def truncate_to_char_limit(
    hits: Sequence[ReportChunkHit],
    max_context_chars: int,
) -> list[ReportChunkHit]:
    """Keep hits, best first, until the character budget runs out.

    The returned list is the single source of truth for both the prompt context
    and the response citations — see ``chatbot_answer_service``. Dropping a hit
    here drops it from the citations too, which is what AC-3 requires.
    """
    used: list[ReportChunkHit] = []
    consumed = 0
    for hit in hits:
        length = len(hit.content)
        if used and consumed + length > max_context_chars:
            break
        used.append(hit)
        consumed += length
        if consumed >= max_context_chars:
            break
    return used
