"""AI-005 conclusion quote validation and fallback tests."""

from __future__ import annotations

import logging

import pytest

from app.exceptions.report_generation import ReportGenerationError
from app.providers.llm_provider import FakeLlmProvider
from app.schemas.report_generation import OpinionType
from app.services.report_generation import ReportGenerationService, collect_usable_sources
from tests.fixtures.report_generation.builders import (
    build_normalized_input,
    build_opinion_candidate,
    sample_llm_draft,
)


def _parking_input(*, semantic_text: str = "주차가 힘들어요."):
    return build_normalized_input(
        participants=["P1"],
        checklist_items=[
            {
                "checklistItemId": 501,
                "participantRef": "P1",
                "category": "주차",
                "title": "주차 여건",
                "subtitle": None,
                "displayOrder": 1,
                "fallback": False,
                "completed": True,
                "completedAt": None,
            }
        ],
        sources=[
            {
                "sourceId": 201,
                "participantRef": "P1",
                "checklistItemId": 501,
                "recordedAt": "2026-07-20T14:20:00+09:00",
                "sourceType": "TEXT",
                "semanticText": semantic_text,
            }
        ],
    )


def _parking_draft(*, conclusion_quote: str | None):
    return sample_llm_draft(
        candidates=[
            build_opinion_candidate(
                category="주차",
                opinion_type="CAUTION",
                label="주차 여건",
                summary="주차가 어렵다는 의견입니다.",
                conclusion_quote=conclusion_quote,
            )
        ],
        category_summaries=[
            {
                "category": "주차",
                "summary": "주차 여건에 관한 기록입니다.",
                "sourceIndexes": [0],
            }
        ],
    )


def _validate_quote(*, conclusion_quote: str | None, semantic_text: str):
    normalized_input = _parking_input(semantic_text=semantic_text)
    service = ReportGenerationService(FakeLlmProvider(payload={}))
    draft = service._validate_draft_payload(
        _parking_draft(conclusion_quote=conclusion_quote)
    )
    service._validate_draft_references(
        draft=draft,
        categories=["주차"],
        usable=collect_usable_sources(normalized_input),
        report_id=normalized_input.reportId,
    )
    return draft.opinionCandidates[0]


def test_matching_conclusion_quote_is_retained() -> None:
    candidate = _validate_quote(
        conclusion_quote="주차가 힘들어요",
        semantic_text="차가 많아서 주차가 힘들어요.",
    )

    assert candidate.conclusionQuote == "주차가 힘들어요"


@pytest.mark.parametrize("conclusion_quote", [None, "", "   "])
def test_missing_or_blank_conclusion_quote_becomes_none(
    conclusion_quote: str | None,
) -> None:
    candidate = _validate_quote(
        conclusion_quote=conclusion_quote,
        semantic_text="주차장이 좁다",
    )

    assert candidate.conclusionQuote is None


def test_conclusion_quote_matches_after_whitespace_and_punctuation_normalization() -> None:
    candidate = _validate_quote(
        conclusion_quote="주차가힘들어요",
        semantic_text="주차가 힘들어요.",
    )

    assert candidate.conclusionQuote == "주차가힘들어요"


@pytest.mark.asyncio
async def test_unmatched_quote_is_dropped_without_changing_verdict_or_result(
    caplog: pytest.LogCaptureFixture,
) -> None:
    semantic_text = "주차가 힘들어요."
    fabricated_quote = "주차가 아주 편해요"
    payload = _parking_input(semantic_text=semantic_text)
    provider = FakeLlmProvider(
        payload=_parking_draft(conclusion_quote=fabricated_quote)
    )

    with caplog.at_level(logging.INFO, logger="app.services.report_generation"):
        result = await ReportGenerationService(provider).generate_report(payload)

    opinion = result.categories[0].participantOpinions[0]
    assert opinion.opinionType == OpinionType.CAUTION
    assert len(result.topCautionFeatures) == 1
    assert result.topCautionFeatures[0].label == "주차 여건"
    assert result.topCautionFeatures[0].mentionCount == 1
    assert result.categories[0].cautionOpinionCount == 1
    assert result.categories[0].positiveOpinionCount == 0
    assert "conclusionQuote" not in result.model_dump()
    assert "sourceIndex=0" in caplog.text
    assert semantic_text not in caplog.text
    assert fabricated_quote not in caplog.text


@pytest.mark.asyncio
async def test_conclusion_quote_over_60_characters_fails_schema_validation() -> None:
    payload = _parking_input()
    provider = FakeLlmProvider(
        payload=_parking_draft(conclusion_quote="가" * 61)
    )

    with pytest.raises(ReportGenerationError) as exc_info:
        await ReportGenerationService(provider).generate_report(payload)

    assert exc_info.value.code == "SCHEMA_VALIDATION_FAILED"
    assert exc_info.value.retryable is True
