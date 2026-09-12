"""Pydantic schemas for Spring ↔ FastAPI checklist itemCode selection (AI-002-1).

Kept separate from the legacy generate contract in checklist.py.
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, field_validator


class ChecklistSelectShortlistItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    itemCode: str = Field(min_length=1)
    categoryCode: str = Field(min_length=1)
    title: str = Field(min_length=1)
    priorityTags: list[str] = Field(default_factory=list)
    conditionTags: list[str] = Field(default_factory=list)
    serverScore: int
    isCommonCore: bool

    @field_validator("itemCode", "categoryCode", "title")
    @classmethod
    def not_blank(cls, value: str) -> str:
        if value.strip() == "":
            raise ValueError("must not be blank")
        return value


class ChecklistAiSelectRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    selectionVersion: str = Field(min_length=1)
    targetItemCount: int = Field(ge=20, le=30)
    mappedPurpose: str | None = None
    selectedPriorities: list[str] = Field(default_factory=list)
    shortlist: list[ChecklistSelectShortlistItem] = Field(min_length=1)


class ChecklistAiSelectResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    itemCodes: list[str] = Field(min_length=20, max_length=30)

    @field_validator("itemCodes")
    @classmethod
    def validate_item_codes(cls, value: list[str]) -> list[str]:
        if any(not isinstance(code, str) or code.strip() == "" for code in value):
            raise ValueError("itemCodes must contain non-blank strings")
        if len(value) != len(set(value)):
            raise ValueError("itemCodes must not contain duplicates")
        return value


def select_response_json_schema() -> dict:
    return ChecklistAiSelectResponse.model_json_schema()
