"""Builders for AI-006 evidence-link fixtures."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any

from app.schemas.report_evidence import ClaimType, EvidenceLinkRequest
from app.schemas.report_generation import ReportGenerationResult
from app.schemas.report_input import NormalizedReportInput
from app.services.report_evidence import (
    ClaimSpec,
    UsableEvidenceSource,
    build_claim_specs,
    collect_usable_evidence_sources,
)
from tests.fixtures.report_generation.builders import build_normalized_input

KST = timezone(timedelta(hours=9))
BASE = datetime(2026, 7, 20, 14, 0, 0, tzinfo=KST)


def multi_participant_noise_input() -> NormalizedReportInput:
    """Three participants with TEXT/STT across 교통/소음 (+ PHOTO noise)."""
    checklist = []
    sources = []
    categories = [("교통", "지하철"), ("소음", "단지소음")]
    source_id = 300
    item_id = 600
    for ref_index, ref in enumerate(["P1", "P2", "P3"], start=1):
        for cat_index, (category, title) in enumerate(categories, start=1):
            item_id += 1
            checklist.append(
                {
                    "checklistItemId": item_id,
                    "participantRef": ref,
                    "category": category,
                    "title": title,
                    "subtitle": None,
                    "displayOrder": (ref_index - 1) * 2 + cat_index,
                    "fallback": False,
                    "completed": True,
                    "completedAt": (BASE + timedelta(minutes=item_id)).isoformat(),
                }
            )
            source_id += 1
            source_type = "TEXT" if cat_index == 1 else "STT"
            sources.append(
                {
                    "sourceId": source_id,
                    "participantRef": ref,
                    "checklistItemId": item_id,
                    "recordedAt": (
                        BASE + timedelta(minutes=10 * ref_index + cat_index)
                    ).isoformat(),
                    "sourceType": source_type,
                    "semanticText": f"{ref} {category} 기록입니다.",
                }
            )
    # PHOTO should never become usable evidence.
    sources.append(
        {
            "sourceId": 999,
            "participantRef": "P1",
            "checklistItemId": checklist[0]["checklistItemId"],
            "recordedAt": (BASE + timedelta(minutes=90)).isoformat(),
            "sourceType": "PHOTO",
            "photoMetadata": {
                "fileId": 77,
                "contentType": "image/jpeg",
                "sizeBytes": 1024,
            },
        }
    )
    return build_normalized_input(
        participants=["P1", "P2", "P3"],
        checklist_items=checklist,
        sources=sources,
    )


def build_generation_result(
    *,
    top_positive: list[dict[str, Any]] | None = None,
    top_caution: list[dict[str, Any]] | None = None,
    common: list[dict[str, Any]] | None = None,
    conflict: list[dict[str, Any]] | None = None,
    categories: list[dict[str, Any]] | None = None,
    title: str = "테스트 임장 통합 리포트",
    summary: str = "참여자들이 교통과 소음에 대해 의견을 남겼습니다.",
) -> ReportGenerationResult:
    if categories is None:
        categories = [
            {
                "category": "교통",
                "summary": "교통 접근성에 대한 의견이 있습니다.",
                "positiveOpinionCount": 2,
                "cautionOpinionCount": 0,
                "dataSufficient": True,
                "participantOpinions": [
                    {
                        "participantRef": "P1",
                        "participantLabel": "참여자 1",
                        "opinionType": "POSITIVE",
                        "summary": "지하철이 가깝습니다.",
                    },
                    {
                        "participantRef": "P2",
                        "participantLabel": "참여자 2",
                        "opinionType": "POSITIVE",
                        "summary": "버스 노선이 편리합니다.",
                    },
                ],
            },
            {
                "category": "소음",
                "summary": "소음에 대한 의견이 갈립니다.",
                "positiveOpinionCount": 1,
                "cautionOpinionCount": 1,
                "dataSufficient": True,
                "participantOpinions": [
                    {
                        "participantRef": "P1",
                        "participantLabel": "참여자 1",
                        "opinionType": "POSITIVE",
                        "summary": "단지 안은 조용합니다.",
                    },
                    {
                        "participantRef": "P2",
                        "participantLabel": "참여자 2",
                        "opinionType": "CAUTION",
                        "summary": "대로변은 차 소리가 들립니다.",
                    },
                ],
            },
            {
                "category": "주차",
                "summary": "해당 카테고리에 대한 현장 기록이 부족하여 "
                "단정적인 분석을 제공하기 어렵습니다.",
                "positiveOpinionCount": 0,
                "cautionOpinionCount": 0,
                "dataSufficient": False,
                "participantOpinions": [],
            },
        ]
    payload = {
        "title": title,
        "summary": summary,
        "metrics": {
            "totalChecklistItemCount": 6,
            "completedChecklistItemCount": 6,
            "averageCompletionRate": 100.0,
            "fieldRecordCount": 6,
        },
        "topPositiveFeatures": top_positive
        if top_positive is not None
        else [
            {
                "rank": 1,
                "label": "지하철 접근성",
                "summary": "여러 참여자가 지하철 접근성을 긍정했습니다.",
                "mentionCount": 3,
                "participantRefs": ["P1", "P2", "P3"],
            }
        ],
        "topCautionFeatures": top_caution
        if top_caution is not None
        else [
            {
                "rank": 1,
                "label": "대로변 소음",
                "summary": "대로변 소음에 대한 주의 의견이 있습니다.",
                "mentionCount": 2,
                "participantRefs": ["P2", "P3"],
            }
        ],
        "commonOpinions": common
        if common is not None
        else [
            {
                "category": "교통",
                "label": "지하철 접근성",
                "opinionType": "POSITIVE",
                "summary": "참여자들이 지하철 접근성을 공통으로 언급했습니다.",
                "participantCount": 2,
                "participantRefs": ["P1", "P2"],
            }
        ],
        "conflictingOpinions": conflict
        if conflict is not None
        else [
            {
                "category": "소음",
                "label": "단지 소음",
                "summary": "단지 소음에 대해 긍정과 주의 의견이 갈립니다.",
                "positiveParticipantCount": 1,
                "cautionParticipantCount": 1,
                "positiveParticipantRefs": ["P1"],
                "cautionParticipantRefs": ["P2"],
            }
        ],
        "categories": categories,
    }
    return ReportGenerationResult.model_validate(payload)


def build_link_request(
    *,
    normalized_input: NormalizedReportInput | None = None,
    generation_result: ReportGenerationResult | None = None,
) -> EvidenceLinkRequest:
    return EvidenceLinkRequest(
        normalizedInput=normalized_input or multi_participant_noise_input(),
        generationResult=generation_result or build_generation_result(),
    )


def auto_evidence_draft(
    request: EvidenceLinkRequest,
    *,
    drop_participant_for_claim_key: str | None = None,
    omit_claim_key: str | None = None,
    extra_normal: dict[str, Any] | None = None,
    force_conflict_source_on_both_sides: bool = False,
    remap: dict[str, list[int]] | None = None,
    conflict_remap: dict[str, tuple[list[int], list[int]]] | None = None,
) -> dict[str, Any]:
    """Build a structurally valid LLM draft that covers all claim participants."""
    claims = build_claim_specs(request.generationResult, request.normalizedInput)
    usable = collect_usable_evidence_sources(request.normalizedInput)
    normal_mappings: list[dict[str, Any]] = []
    conflict_mappings: list[dict[str, Any]] = []

    for claim in claims:
        if omit_claim_key is not None and claim.claim_key == omit_claim_key:
            continue
        if claim.claim_type == ClaimType.CONFLICT:
            if conflict_remap and claim.claim_key in conflict_remap:
                positive, caution = conflict_remap[claim.claim_key]
            else:
                positive = _indexes_for_refs(
                    usable,
                    claim.positive_participant_refs,
                    category=claim.category,
                )
                caution = _indexes_for_refs(
                    usable,
                    claim.caution_participant_refs,
                    category=claim.category,
                )
                if force_conflict_source_on_both_sides and positive:
                    caution = [positive[0]] + [
                        index for index in caution if index != positive[0]
                    ]
            conflict_mappings.append(
                {
                    "claimKey": claim.claim_key,
                    "positiveSourceIndexes": positive,
                    "cautionSourceIndexes": caution,
                }
            )
            continue

        if remap and claim.claim_key in remap:
            indexes = list(remap[claim.claim_key])
        else:
            indexes = _indexes_for_refs(
                usable,
                claim.participant_refs,
                category=claim.category,
            )
            if (
                drop_participant_for_claim_key == claim.claim_key
                and claim.participant_refs
            ):
                drop_ref = claim.participant_refs[-1]
                indexes = [
                    index
                    for index in indexes
                    if usable[index].participant_ref != drop_ref
                ]
        normal_mappings.append(
            {
                "claimKey": claim.claim_key,
                "sourceIndexes": indexes or [0],
            }
        )

    if extra_normal is not None:
        normal_mappings.append(extra_normal)

    return {
        "normalMappings": normal_mappings,
        "conflictMappings": conflict_mappings,
    }


def _indexes_for_refs(
    usable: list[UsableEvidenceSource],
    refs: list[str],
    *,
    category: str | None,
) -> list[int]:
    indexes: list[int] = []
    covered: set[str] = set()
    for item in usable:
        if item.participant_ref not in refs:
            continue
        if category is not None and item.category != category:
            continue
        if item.participant_ref in covered:
            continue
        indexes.append(item.index)
        covered.add(item.participant_ref)
    return indexes


def claim_specs_for(request: EvidenceLinkRequest) -> list[ClaimSpec]:
    return build_claim_specs(request.generationResult, request.normalizedInput)
