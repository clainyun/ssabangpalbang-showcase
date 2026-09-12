"""Tests for checklist itemCode selection (AI-002-1)."""

from __future__ import annotations

import pytest

from app.providers.llm_provider import FakeLlmProvider, LlmProviderError
from app.schemas.checklist_select import (
    ChecklistAiSelectRequest,
    ChecklistAiSelectResponse,
    ChecklistSelectShortlistItem,
)
from app.services.checklist_select_service import (
    ChecklistSelectionError,
    ChecklistSelectionService,
)


def sample_shortlist(count: int = 12) -> list[ChecklistSelectShortlistItem]:
    items: list[ChecklistSelectShortlistItem] = []
    for index in range(1, count + 1):
        items.append(
            ChecklistSelectShortlistItem(
                itemCode=f"TRN_{index:03d}",
                categoryCode="TRN" if index < count else "SUM",
                title=f"문항 {index}",
                priorityTags=["TRANSPORTATION"],
                conditionTags=["TRANSIT"],
                serverScore=100 - index,
                isCommonCore=index <= 2,
            )
        )
    return items


def sample_request(target: int = 25) -> ChecklistAiSelectRequest:
    return ChecklistAiSelectRequest(
        selectionVersion="v3-select-1",
        targetItemCount=target,
        mappedPurpose="LIVE",
        selectedPriorities=["TRANSPORTATION", "SAFETY"],
        shortlist=sample_shortlist(40),
    )


@pytest.mark.asyncio
async def test_select_returns_item_codes():
    codes = [f"TRN_{index:03d}" for index in range(1, 26)]
    provider = FakeLlmProvider(payload={"itemCodes": codes})
    service = ChecklistSelectionService(provider)

    response = await service.select(sample_request(25))

    assert response.itemCodes == codes
    assert 20 <= len(response.itemCodes) <= 30


@pytest.mark.asyncio
async def test_select_rejects_unknown_item_code():
    provider = FakeLlmProvider(
        payload={"itemCodes": [f"TRN_{index:03d}" for index in range(1, 25)] + ["ZZZ_999"]}
    )
    service = ChecklistSelectionService(provider)

    with pytest.raises(ChecklistSelectionError) as exc:
        await service.select(sample_request(25))
    assert exc.value.code == "UNKNOWN_ITEM_CODE"


@pytest.mark.asyncio
async def test_select_rejects_duplicate_item_code():
    with pytest.raises(Exception):
        ChecklistAiSelectResponse.model_validate(
            {"itemCodes": ["TRN_001", "TRN_001"] + [f"TRN_{i:03d}" for i in range(3, 26)]}
        )


@pytest.mark.asyncio
async def test_select_rejects_count_below_range():
    """Fewer than 20 codes must be rejected (schema minItems now 20)."""
    provider = FakeLlmProvider(
        payload={"itemCodes": [f"TRN_{index:03d}" for index in range(1, 9)]}
    )
    service = ChecklistSelectionService(provider)

    with pytest.raises(ChecklistSelectionError) as exc:
        await service.select(sample_request(25))
    assert exc.value.code == "SCHEMA_VALIDATION_FAILED"


@pytest.mark.asyncio
async def test_select_accepts_count_within_range_off_target():
    """Count within 20-30 but not equal to targetItemCount is now allowed."""
    codes = [f"TRN_{index:03d}" for index in range(1, 23)]  # 22 codes
    provider = FakeLlmProvider(payload={"itemCodes": codes})
    service = ChecklistSelectionService(provider)

    response = await service.select(sample_request(25))

    assert response.itemCodes == codes


@pytest.mark.asyncio
async def test_select_provider_timeout_maps_to_error():
    provider = FakeLlmProvider(error=LlmProviderError("timeout"))
    service = ChecklistSelectionService(provider)

    with pytest.raises(ChecklistSelectionError) as exc:
        await service.select(sample_request(25))
    assert exc.value.code == "PROVIDER_FAILED"


def test_select_request_schema_forbids_extra_fields():
    payload = sample_request().model_dump()
    payload["extra"] = True
    with pytest.raises(Exception):
        ChecklistAiSelectRequest.model_validate(payload)


def test_system_prompt_requires_common_core_and_sum():
    from app.prompts.checklist_select_prompt import SYSTEM_PROMPT

    assert "isCommonCore=true" in SYSTEM_PROMPT
    assert "최소 1개 반드시 포함" in SYSTEM_PROMPT
    assert "categoryCode가 SUM" in SYSTEM_PROMPT
    assert "가능하면" not in SYSTEM_PROMPT
    assert SYSTEM_PROMPT.count("최소 1개 반드시 포함") >= 2


def test_shared_select_contract_fixtures():
    """Canonical select fixtures must match backend classpath copies."""
    import hashlib
    from pathlib import Path

    repo_root = Path(__file__).resolve().parents[2]
    canonical_root = repo_root / "contracts" / "ai-002"
    backend_copy_root = (
        repo_root / "backend" / "src" / "test" / "resources" / "contracts" / "ai-002"
    )

    request_name = "checklist_select_request.json"
    response_name = "checklist_select_response.json"

    request_bytes = (canonical_root / request_name).read_bytes()
    response_bytes = (canonical_root / response_name).read_bytes()

    request = ChecklistAiSelectRequest.model_validate_json(request_bytes)
    response = ChecklistAiSelectResponse.model_validate_json(response_bytes)

    shortlist_codes = {item.itemCode for item in request.shortlist}
    assert 20 <= request.targetItemCount <= 30
    assert 20 <= len(response.itemCodes) <= 30
    assert len(response.itemCodes) == len(set(response.itemCodes))
    assert set(response.itemCodes).issubset(shortlist_codes)
    assert any(item.isCommonCore for item in request.shortlist if item.itemCode in response.itemCodes)
    assert any(
        item.categoryCode == "SUM"
        for item in request.shortlist
        if item.itemCode in response.itemCodes
    )

    backend_request = backend_copy_root / request_name
    backend_response = backend_copy_root / response_name
    assert backend_request.is_file()
    assert backend_response.is_file()
    assert hashlib.sha256(request_bytes).hexdigest() == hashlib.sha256(
        backend_request.read_bytes()
    ).hexdigest()
    assert hashlib.sha256(response_bytes).hexdigest() == hashlib.sha256(
        backend_response.read_bytes()
    ).hexdigest()
