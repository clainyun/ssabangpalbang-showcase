"""Checklist generation orchestration for FastAPI."""

from __future__ import annotations

import json
from typing import Any

import jsonschema
from pydantic import ValidationError

from app.prompts.checklist_prompt import SYSTEM_PROMPT, build_user_prompt
from app.providers.llm_provider import LlmProvider, LlmProviderError
from app.schemas.checklist import (
    ChecklistAiGenerateRequest,
    ChecklistAiGenerateResponse,
    response_json_schema,
)


class ChecklistGenerationError(Exception):
    """Technical failure that Spring should map to fallback."""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


class ChecklistGenerationService:
    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def generate(
        self, request: ChecklistAiGenerateRequest
    ) -> ChecklistAiGenerateResponse:
        user_prompt = build_user_prompt(request.personalization)
        try:
            raw = await self.provider.complete_json(SYSTEM_PROMPT, user_prompt)
        except LlmProviderError as exc:
            raise ChecklistGenerationError("PROVIDER_FAILED", str(exc)) from exc

        return self._validate_payload(raw)

    def _validate_payload(self, raw: dict[str, Any]) -> ChecklistAiGenerateResponse:
        schema = response_json_schema()
        try:
            jsonschema.validate(instance=raw, schema=schema)
        except jsonschema.ValidationError as exc:
            raise ChecklistGenerationError(
                "SCHEMA_VALIDATION_FAILED",
                f"JSON Schema validation failed: {exc.message}",
            ) from exc

        try:
            return ChecklistAiGenerateResponse.model_validate(raw)
        except ValidationError as exc:
            raise ChecklistGenerationError(
                "PYDANTIC_VALIDATION_FAILED",
                f"Pydantic validation failed: {exc}",
            ) from exc


def dump_contract_fixture() -> str:
    """Helper for shared Spring/Python contract fixtures."""
    sample = {
        "personalization": {
            "member": {
                "memberPurpose": "RESIDENCE",
                "maritalStatus": "MARRIED",
                "hasVehicle": True,
                "hasChildren": False,
                "priorities": ["SAFETY", "TRANSPORT", "NOISE"],
                "ageGroup": "THIRTIES",
            },
            "apartment": {
                "apartmentId": 10,
                "name": "테스트아파트",
                "address": "서울특별시 강남구",
                "districtName": "강남구",
                "dongName": "역삼동",
                "householdCount": 500,
                "completionYearMonth": "2020-05",
                "parkingSpaceCount": 600,
            },
            "study": {
                "studyId": 7,
                "studyPurpose": "INVESTMENT",
                "goal": "내 집 마련",
            },
        }
    }
    return json.dumps(sample, ensure_ascii=False, indent=2)
