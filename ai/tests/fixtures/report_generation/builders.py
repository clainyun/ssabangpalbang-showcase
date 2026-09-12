"""Builders for synthetic NormalizedReportInput fixtures (AI-005)."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any

from app.schemas.report_input import NormalizedReportInput

KST = timezone(timedelta(hours=9))
BASE = datetime(2026, 7, 20, 14, 0, 0, tzinfo=KST)
_UNSET = object()


def build_opinion_candidate(
    *,
    participant_ref: str = "P1",
    category: str = "교통",
    opinion_type: str = "POSITIVE",
    label: str = "지하철 접근성",
    summary: str = "역까지 이동이 편리합니다.",
    source_index: int = 0,
    conclusion_quote: str | None | object = _UNSET,
) -> dict[str, Any]:
    candidate: dict[str, Any] = {
        "participantRef": participant_ref,
        "category": category,
        "opinionType": opinion_type,
        "label": label,
        "summary": summary,
        "sourceIndex": source_index,
    }
    if conclusion_quote is not _UNSET:
        candidate["conclusionQuote"] = conclusion_quote
    return candidate


def build_normalized_input(
    *,
    report_id: int = 48,
    participants: list[str] | None = None,
    checklist_items: list[dict[str, Any]] | None = None,
    sources: list[dict[str, Any]] | None = None,
    excluded_sources: list[dict[str, Any]] | None = None,
    quality_issues: list[dict[str, Any]] | None = None,
) -> NormalizedReportInput:
    refs = participants if participants is not None else ["P1", "P2"]
    if checklist_items is not None:
        checklist = checklist_items
    else:
        checklist = [
            {
                "checklistItemId": 501,
                "participantRef": "P1",
                "category": "교통",
                "title": "지하철역 접근성",
                "subtitle": None,
                "displayOrder": 1,
                "fallback": False,
                "completed": True,
                "completedAt": (BASE + timedelta(minutes=30)).isoformat(),
            },
            {
                "checklistItemId": 502,
                "participantRef": "P2",
                "category": "소음",
                "title": "시간대별 소음",
                "subtitle": None,
                "displayOrder": 2,
                "fallback": False,
                "completed": True,
                "completedAt": None,
            },
        ]
    source_list = sources if sources is not None else [
        {
            "sourceId": 201,
            "participantRef": "P1",
            "checklistItemId": 501,
            "recordedAt": (BASE + timedelta(minutes=20)).isoformat(),
            "sourceType": "TEXT",
            "semanticText": "현관에서 지하철역까지 도보로 이동이 편리했습니다.",
        },
        {
            "sourceId": 228,
            "participantRef": "P2",
            "checklistItemId": 502,
            "recordedAt": (BASE + timedelta(minutes=50)).isoformat(),
            "sourceType": "STT",
            "semanticText": "주간 소음은 양호하지만 출퇴근 시간대는 다시 확인이 필요합니다.",
        },
    ]
    excluded = excluded_sources if excluded_sources is not None else []
    issues = quality_issues if quality_issues is not None else []
    if not source_list and not any(
        issue.get("code") == "NO_USABLE_SOURCES" for issue in issues
    ):
        issues = [
            {
                "code": "NO_USABLE_SOURCES",
                "participantRef": None,
                "referenceId": None,
                "observedAt": (BASE + timedelta(hours=2)).isoformat(),
                "detail": None,
            }
        ]

    session_ended = BASE + timedelta(hours=2)
    snapshot = BASE + timedelta(hours=2, minutes=5)
    participant_payload = []
    for index, ref in enumerate(refs):
        participant_payload.append(
            {
                "participantRef": ref,
                "startedAt": (BASE + timedelta(minutes=index)).isoformat(),
                "endedAt": session_ended.isoformat(),
            }
        )

    completed_count = sum(1 for item in checklist if item["completed"])
    payload = {
        "schemaVersion": 1,
        "reportId": report_id,
        "studyId": 7,
        "apartmentId": 15,
        "fieldSessionId": 900,
        "sessionEndedAt": session_ended.isoformat(),
        "snapshotAt": snapshot.isoformat(),
        "participants": participant_payload,
        "checklistItems": checklist,
        "sources": source_list,
        "excludedSources": excluded,
        "qualityIssues": issues,
        "normalizationSummary": {
            "participantCount": len(refs),
            "checklistItemCount": len(checklist),
            "completedChecklistItemCount": completed_count,
            "inputRecordCount": len(source_list) + len(excluded),
            "includedSourceCount": len(source_list),
            "excludedRecordCount": len(excluded),
            "duplicateRecordCount": sum(
                1 for item in excluded if item.get("reason") == "DUPLICATED"
            ),
            "incompleteSttJobCount": sum(
                1
                for issue in issues
                if issue.get("code") in {"STT_NOT_DONE", "STT_RESULT_MISSING"}
            ),
        },
    }
    return NormalizedReportInput.model_validate(payload)


def three_participant_transport_input() -> NormalizedReportInput:
    checklist = []
    sources = []
    for index, ref in enumerate(["P1", "P2", "P3"], start=1):
        item_id = 500 + index
        checklist.append(
            {
                "checklistItemId": item_id,
                "participantRef": ref,
                "category": "교통",
                "title": "지하철역 접근성",
                "subtitle": None,
                "displayOrder": index,
                "fallback": False,
                "completed": True,
                "completedAt": (BASE + timedelta(minutes=10 * index)).isoformat(),
            }
        )
        sources.append(
            {
                "sourceId": 200 + index,
                "participantRef": ref,
                "checklistItemId": item_id,
                "recordedAt": (BASE + timedelta(minutes=15 * index)).isoformat(),
                "sourceType": "TEXT",
                "semanticText": f"{ref} 지하철 접근성이 좋습니다.",
            }
        )
    # second category without opinions
    checklist.append(
        {
            "checklistItemId": 590,
            "participantRef": "P1",
            "category": "주차",
            "title": "주차 여유",
            "subtitle": None,
            "displayOrder": 4,
            "fallback": False,
            "completed": False,
            "completedAt": None,
        }
    )
    return build_normalized_input(
        participants=["P1", "P2", "P3"],
        checklist_items=checklist,
        sources=sources,
    )


def sample_llm_draft(
    *,
    candidates: list[dict[str, Any]] | None = None,
    title: str = "테스트 임장 통합 리포트",
    summary: str = "여러 참여자가 교통 접근성을 긍정적으로 평가했습니다.",
    category_summaries: list[dict[str, Any]] | None = None,
    common_summaries: list[dict[str, Any]] | None = None,
    conflict_summaries: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    candidate_list = candidates or []
    candidate_categories = {item.get("category") for item in candidate_list}
    candidate_source_indexes = {
        item.get("sourceIndex")
        for item in candidate_list
        if isinstance(item.get("sourceIndex"), int) and item["sourceIndex"] >= 0
    }
    if len(candidate_categories) == 1 and candidate_source_indexes and max(candidate_source_indexes) >= 2:
        only_category = next(iter(candidate_categories))
        default_category_summaries = [
            {
                "category": only_category,
                "summary": "입력된 현장 기록의 구체적인 내용을 반영한 카테고리 요약입니다.",
                "sourceIndexes": list(range(max(candidate_source_indexes) + 1)),
            }
        ]
    else:
        default_category_summaries = [
            {
                "category": "교통",
                "summary": "지하철 접근이 편리하다는 구체적인 현장 기록이 있었습니다.",
                "sourceIndexes": [0],
            },
            {
                "category": "소음",
                "summary": "주간 소음과 출퇴근 시간대 재확인 필요성이 함께 기록됐습니다.",
                "sourceIndexes": [1],
            },
        ]
    return {
        "title": title,
        "summary": summary,
        "opinionCandidates": candidate_list,
        "categorySummaries": (
            default_category_summaries
            if category_summaries is None
            else category_summaries
        ),
        "commonOpinionSummaries": common_summaries or [],
        "conflictingOpinionSummaries": conflict_summaries or [],
    }
