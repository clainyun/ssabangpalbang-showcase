"""Checklist itemCode selection orchestration for FastAPI (AI-002-1)."""

from __future__ import annotations

from typing import Any

import jsonschema
from pydantic import ValidationError

from app.prompts.checklist_select_prompt import SYSTEM_PROMPT, build_select_user_prompt
from app.providers.llm_provider import LlmProvider, LlmProviderError
from app.schemas.checklist_select import (
    ChecklistAiSelectRequest,
    ChecklistAiSelectResponse,
    select_response_json_schema,
)


class ChecklistSelectionError(Exception):
    """Technical failure that Spring should map to catalog/hardcoded fallback."""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


class ChecklistSelectionService:
    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def select(
        self, request: ChecklistAiSelectRequest
    ) -> ChecklistAiSelectResponse:
        user_prompt = build_select_user_prompt(request)
        try:
            raw = await self.provider.complete_json(SYSTEM_PROMPT, user_prompt)
        except LlmProviderError as exc:
            raise ChecklistSelectionError("PROVIDER_FAILED", str(exc)) from exc

        return self._validate_payload(raw, request)

    def _validate_payload(
        self, raw: dict[str, Any], request: ChecklistAiSelectRequest
    ) -> ChecklistAiSelectResponse:
        schema = select_response_json_schema()
        try:
            jsonschema.validate(instance=raw, schema=schema)
        except jsonschema.ValidationError as exc:
            raise ChecklistSelectionError(
                "SCHEMA_VALIDATION_FAILED",
                f"JSON Schema validation failed: {exc.message}",
            ) from exc

        try:
            response = ChecklistAiSelectResponse.model_validate(raw)
        except ValidationError as exc:
            raise ChecklistSelectionError(
                "PYDANTIC_VALIDATION_FAILED",
                f"Pydantic validation failed: {exc}",
            ) from exc

        allowed = {item.itemCode for item in request.shortlist}
        if not 20 <= len(response.itemCodes) <= 30:
            raise ChecklistSelectionError(
                "COUNT_OUT_OF_RANGE",
                "itemCodes size must be between 20 and 30",
            )
        if not set(response.itemCodes).issubset(allowed):
            raise ChecklistSelectionError(
                "UNKNOWN_ITEM_CODE",
                "itemCodes must be a subset of the request shortlist",
            )
        return response
