"""Pydantic schemas for Spring ↔ FastAPI checklist generation contract (AI-002).

Field names must match Java DTOs:
- ChecklistAiGenerateRequest / ChecklistPersonalizationInput
- ChecklistAiGenerateResponse
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class MemberOnboardingSection(BaseModel):
    model_config = ConfigDict(extra="forbid")

    memberPurpose: str
    maritalStatus: str
    hasVehicle: bool
    hasChildren: bool
    priorities: list[str] = Field(min_length=1, max_length=4)
    ageGroup: str

    @field_validator("priorities")
    @classmethod
    def priorities_unique(cls, value: list[str]) -> list[str]:
        if len(set(value)) != len(value):
            raise ValueError("priorities must not contain duplicates")
        return value


class ApartmentSection(BaseModel):
    model_config = ConfigDict(extra="forbid")

    apartmentId: int
    name: str
    address: str | None = None
    districtName: str | None = None
    dongName: str | None = None
    householdCount: int | None = None
    completionYearMonth: str | None = None
    parkingSpaceCount: int | None = None


class StudySection(BaseModel):
    model_config = ConfigDict(extra="forbid")

    studyId: int
    studyPurpose: str | None = None
    goal: str


class ChecklistPersonalizationInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    member: MemberOnboardingSection
    apartment: ApartmentSection
    study: StudySection


class ChecklistAiGenerateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    personalization: ChecklistPersonalizationInput


class ChecklistItemOut(BaseModel):
    model_config = ConfigDict(extra="forbid")

    category: str = Field(min_length=1, max_length=30)
    title: str = Field(min_length=1)
    subtitle: str | None = None
    displayOrder: int = Field(ge=1)
    # 항목별 예시 메모: 현장에서 이 항목을 확인한 뒤 회원이 남길 법한 한 줄 예시.
    # LLM이 항상 채우도록 프롬프트에 지시하지만, 누락돼도 계약이 깨지지 않게 nullable로 둔다.
    example: str | None = Field(default=None, max_length=200)

    @field_validator("category", "title")
    @classmethod
    def not_blank(cls, value: str) -> str:
        if value.strip() == "":
            raise ValueError("must not be blank")
        return value


class ChecklistAiGenerateResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    items: list[ChecklistItemOut] = Field(min_length=1)

    @field_validator("items")
    @classmethod
    def unique_display_orders(
        cls, value: list[ChecklistItemOut]
    ) -> list[ChecklistItemOut]:
        orders = [item.displayOrder for item in value]
        if len(orders) != len(set(orders)):
            raise ValueError("displayOrder must be unique within items")
        return value


def response_json_schema() -> dict:
    """JSON Schema used for secondary validation of LLM output."""
    return ChecklistAiGenerateResponse.model_json_schema()
