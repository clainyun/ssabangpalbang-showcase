"""Internal endpoint for indexing one completed report."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, HTTPException, Path
from pydantic import BaseModel

from app.rag.config import RagSettings, is_rag_enabled
from app.rag.embedder import get_shared_embedder
from app.rag.report_indexer import (
    ReportRagDatabaseError,
    index_report,
)


router = APIRouter(prefix="/internal/v1/rag", tags=["rag"])


class ReportRagIndexResponse(BaseModel):
    reportId: int
    apartmentId: int | None
    indexed: bool
    chunkCount: int
    deletedCount: int
    skipReason: str | None


@router.post(
    "/reports/{reportId}",
    response_model=ReportRagIndexResponse,
)
def index_completed_report(
    reportId: Annotated[int, Path(ge=1)],
) -> ReportRagIndexResponse:
    if not is_rag_enabled():
        raise HTTPException(
            status_code=503,
            detail={
                "code": "RAG_DISABLED",
                "message": "Database-backed RAG indexing is disabled",
            },
        )

    try:
        settings = RagSettings.from_env()
    except Exception as exc:
        raise _database_failure(exc) from exc

    embedder = get_shared_embedder(settings)
    try:
        result = index_report(reportId, settings, embedder)
    except ReportRagDatabaseError as exc:
        raise _database_failure(exc) from exc

    return ReportRagIndexResponse(
        reportId=result.report_id,
        apartmentId=result.apartment_id,
        indexed=result.indexed,
        chunkCount=result.chunk_count,
        deletedCount=result.deleted_count,
        skipReason=result.skip_reason,
    )


def _database_failure(exc: Exception) -> HTTPException:
    return HTTPException(
        status_code=502,
        detail={"code": "RAG_DB_FAILED", "message": str(exc)},
    )
