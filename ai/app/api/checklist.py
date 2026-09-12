"""Internal checklist generation and selection routes."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.providers.llm_provider import create_llm_provider
from app.schemas.checklist import (
    ChecklistAiGenerateRequest,
    ChecklistAiGenerateResponse,
)
from app.schemas.checklist_select import (
    ChecklistAiSelectRequest,
    ChecklistAiSelectResponse,
)
from app.services.checklist_service import (
    ChecklistGenerationError,
    ChecklistGenerationService,
)
from app.services.checklist_select_service import (
    ChecklistSelectionError,
    ChecklistSelectionService,
)

router = APIRouter(prefix="/internal/v1/checklists", tags=["checklist"])


def _build_service() -> ChecklistGenerationService:
    return ChecklistGenerationService(create_llm_provider())


def _build_selection_service() -> ChecklistSelectionService:
    return ChecklistSelectionService(create_llm_provider())


@router.post("/generate", response_model=ChecklistAiGenerateResponse)
async def generate_checklist(
    request: ChecklistAiGenerateRequest,
) -> ChecklistAiGenerateResponse:
    service = _build_service()
    try:
        return await service.generate(request)
    except ChecklistGenerationError as exc:
        # Spring treats any 5xx / invalid body as technical AI failure → fallback.
        raise HTTPException(
            status_code=502,
            detail={"code": exc.code, "message": exc.message},
        ) from exc


@router.post("/select", response_model=ChecklistAiSelectResponse)
async def select_checklist(
    request: ChecklistAiSelectRequest,
) -> ChecklistAiSelectResponse:
    service = _build_selection_service()
    try:
        return await service.select(request)
    except ChecklistSelectionError as exc:
        raise HTTPException(
            status_code=502,
            detail={"code": exc.code, "message": exc.message},
        ) from exc
