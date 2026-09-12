import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.providers.llm_provider import FakeLlmProvider, LlmProviderError
from app.schemas.checklist import (
    ChecklistAiGenerateRequest,
    ChecklistAiGenerateResponse,
    ChecklistItemOut,
    response_json_schema,
)
from app.services.checklist_service import (
    ChecklistGenerationError,
    ChecklistGenerationService,
)
from app.prompts.checklist_prompt import build_user_prompt


def sample_request_payload() -> dict:
    return {
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


def sample_items() -> dict:
    return {
        "items": [
            {
                "category": "안전",
                "title": "단지 보안 상태",
                "subtitle": "출입구 CCTV와 경비실 위치를 확인하세요.",
                "displayOrder": 1,
            },
            {
                "category": "교통",
                "title": "역까지 도보 시간",
                "subtitle": None,
                "displayOrder": 2,
            },
        ]
    }


def test_request_accepts_valid_payload():
    request = ChecklistAiGenerateRequest.model_validate(sample_request_payload())
    assert request.personalization.member.priorities == [
        "SAFETY",
        "TRANSPORT",
        "NOISE",
    ]


def test_request_rejects_extra_field():
    payload = sample_request_payload()
    payload["unexpected"] = True
    with pytest.raises(ValidationError):
        ChecklistAiGenerateRequest.model_validate(payload)


def test_response_rejects_empty_items():
    with pytest.raises(ValidationError):
        ChecklistAiGenerateResponse.model_validate({"items": []})


def test_response_rejects_blank_category_and_title():
    with pytest.raises(ValidationError):
        ChecklistItemOut.model_validate(
            {"category": " ", "title": "ok", "subtitle": None, "displayOrder": 1}
        )
    with pytest.raises(ValidationError):
        ChecklistItemOut.model_validate(
            {"category": "교통", "title": " ", "subtitle": None, "displayOrder": 1}
        )


def test_response_rejects_category_over_30():
    with pytest.raises(ValidationError):
        ChecklistItemOut.model_validate(
            {
                "category": "가" * 31,
                "title": "제목",
                "subtitle": None,
                "displayOrder": 1,
            }
        )


def test_response_rejects_non_positive_or_duplicate_display_order():
    with pytest.raises(ValidationError):
        ChecklistItemOut.model_validate(
            {"category": "교통", "title": "제목", "subtitle": None, "displayOrder": 0}
        )
    with pytest.raises(ValidationError):
        ChecklistAiGenerateResponse.model_validate(
            {
                "items": [
                    {
                        "category": "교통",
                        "title": "A",
                        "subtitle": None,
                        "displayOrder": 1,
                    },
                    {
                        "category": "소음",
                        "title": "B",
                        "subtitle": None,
                        "displayOrder": 1,
                    },
                ]
            }
        )


def test_priorities_order_preserved_in_prompt():
    request = ChecklistAiGenerateRequest.model_validate(sample_request_payload())
    prompt = build_user_prompt(request.personalization)
    assert "SAFETY, TRANSPORT, NOISE" in prompt


@pytest.mark.asyncio
async def test_service_success_with_fake_provider():
    provider = FakeLlmProvider(payload=sample_items())
    service = ChecklistGenerationService(provider)
    request = ChecklistAiGenerateRequest.model_validate(sample_request_payload())
    result = await service.generate(request)
    assert len(result.items) == 2
    assert result.items[0].displayOrder == 1


@pytest.mark.asyncio
async def test_service_maps_provider_failure():
    provider = FakeLlmProvider(error=LlmProviderError("timeout"))
    service = ChecklistGenerationService(provider)
    request = ChecklistAiGenerateRequest.model_validate(sample_request_payload())
    with pytest.raises(ChecklistGenerationError) as exc:
        await service.generate(request)
    assert exc.value.code == "PROVIDER_FAILED"


@pytest.mark.asyncio
async def test_service_rejects_invalid_llm_json_shape():
    provider = FakeLlmProvider(payload={"items": []})
    service = ChecklistGenerationService(provider)
    request = ChecklistAiGenerateRequest.model_validate(sample_request_payload())
    with pytest.raises(ChecklistGenerationError):
        await service.generate(request)


def test_json_schema_is_generated():
    schema = response_json_schema()
    assert schema["type"] == "object"
    assert "items" in schema["properties"]


def test_shared_contract_fixtures():
    """Canonical fixtures live at repo root contracts/ai-002.

    Backend Jenkins Docker context only includes backend/, so Java tests load
    the same JSON from classpath test resources. Keep both copies identical.
    """
    import hashlib

    repo_root = Path(__file__).resolve().parents[2]
    canonical_root = repo_root / "contracts" / "ai-002"
    backend_copy_root = (
        repo_root / "backend" / "src" / "test" / "resources" / "contracts" / "ai-002"
    )

    request_name = "checklist_generate_request.json"
    response_name = "checklist_generate_response.json"

    request_bytes = (canonical_root / request_name).read_bytes()
    response_bytes = (canonical_root / response_name).read_bytes()

    request = ChecklistAiGenerateRequest.model_validate_json(request_bytes)
    response = ChecklistAiGenerateResponse.model_validate_json(response_bytes)
    assert request.personalization.member.priorities == [
        "TRANSPORT",
        "SAFETY",
        "NOISE",
    ]
    assert response.items[0].category == "교통"
    assert response.items[1].subtitle is None

    backend_request = backend_copy_root / request_name
    backend_response = backend_copy_root / response_name
    assert backend_request.is_file(), (
        "backend classpath fixture missing; copy from contracts/ai-002"
    )
    assert backend_response.is_file(), (
        "backend classpath fixture missing; copy from contracts/ai-002"
    )
    assert hashlib.sha256(request_bytes).hexdigest() == hashlib.sha256(
        backend_request.read_bytes()
    ).hexdigest()
    assert hashlib.sha256(response_bytes).hexdigest() == hashlib.sha256(
        backend_response.read_bytes()
    ).hexdigest()
