"""AI-005 story-style report generation service.

Deterministic metrics/aggregation run in Python. LLM only proposes
opinion candidates and narrative summaries.
"""

from __future__ import annotations

import logging
import re
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Any

import jsonschema
from pydantic import ValidationError

from app.exceptions.report_generation import ReportGenerationError
from app.prompts.report_generation import SYSTEM_PROMPT, build_user_prompt
from app.providers.llm_provider import LlmProvider, LlmProviderError
from app.schemas.report_generation import (
    CommonOpinion,
    ConflictingOpinion,
    LlmOpinionCandidate,
    LlmReportDraft,
    OpinionType,
    ParticipantOpinion,
    ReportCategory,
    ReportFeature,
    ReportGenerationResult,
    ReportMetrics,
    llm_report_draft_json_schema,
)
from app.schemas.report_input import (
    NormalizedReportInput,
    NormalizedReportSource,
    ReportSourceType,
)

logger = logging.getLogger(__name__)

INSUFFICIENT_CATEGORY_SUMMARY = (
    "해당 카테고리에서 분석 가능한 의견이 부족하여 단정적인 분석을 제공하기 어렵습니다."
)
INSUFFICIENT_REPORT_TITLE = "스터디 임장 통합 리포트"
INSUFFICIENT_REPORT_SUMMARY = (
    "현장 기록에서 분석 가능한 의견이 부족하여 스터디원 의견을 충분히 분석하지 못했습니다. "
    "체크리스트 통계만 반영했으며, 구체적인 의견이 포함된 추가 TEXT·STT 기록이 필요합니다."
)

# Soft guardrails. AI-004 already validates TEXT <= 2000; STT may be longer.
MAX_USABLE_SOURCES = 200
MAX_PROMPT_CHARS = 120_000
MAX_TOP_FEATURES = 3
MAX_PARTICIPANT_OPINION_SUMMARY_CHARS = 500
PARTICIPANT_SUMMARY_JOIN = " "
CONCLUSION_QUOTE_PUNCTUATION = str.maketrans("", "", ".,?!\"'“”‘’")


@dataclass(frozen=True)
class _UsableSource:
    index: int
    source_id: int
    participant_ref: str
    checklist_item_id: int
    category: str
    source_type: ReportSourceType
    semantic_text: str


@dataclass
class _FeatureAgg:
    label: str
    opinion_type: OpinionType
    participant_refs: set[str]
    first_index: int
    summary: str


@dataclass
class _CommonAgg:
    category: str
    label: str
    opinion_type: OpinionType
    participant_refs: set[str]
    first_index: int


@dataclass
class _ConflictAgg:
    category: str
    label: str
    positive_refs: set[str]
    caution_refs: set[str]
    first_index: int


@dataclass
class _ParticipantOpinionAgg:
    participant_ref: str
    category: str
    opinion_type: OpinionType
    first_index: int
    summary_entries: list[tuple[int, str]] = field(default_factory=list)


class ReportGenerationService:
    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def generate_report(
        self, normalized_input: NormalizedReportInput
    ) -> ReportGenerationResult:
        metrics = compute_metrics(normalized_input)
        categories = ordered_categories(normalized_input)
        participant_labels = build_participant_labels(normalized_input)
        usable = collect_usable_sources(normalized_input)

        if not usable:
            logger.info(
                "report generation insufficient data reportId=%s usableSources=0",
                normalized_input.reportId,
            )
            return build_insufficient_result(
                metrics=metrics,
                categories=categories,
            )

        self._guard_input_size(usable)
        draft = await self._call_llm(
            normalized_input=normalized_input,
            usable=usable,
            categories=categories,
            participant_labels=participant_labels,
        )
        self._validate_draft_references(
            draft=draft,
            categories=categories,
            usable=usable,
            report_id=normalized_input.reportId,
        )
        return assemble_result(
            draft=draft,
            metrics=metrics,
            categories=categories,
            participant_labels=participant_labels,
            participant_order=[
                item.participantRef for item in normalized_input.participants
            ],
        )

    def _guard_input_size(self, usable: list[_UsableSource]) -> None:
        if len(usable) > MAX_USABLE_SOURCES:
            raise ReportGenerationError(
                "INPUT_TOO_LARGE",
                "usable source count exceeds configured limit",
            )
        total_chars = sum(len(item.semantic_text) for item in usable)
        if total_chars > MAX_PROMPT_CHARS:
            raise ReportGenerationError(
                "INPUT_TOO_LARGE",
                "usable source text exceeds prompt size limit",
            )

    async def _call_llm(
        self,
        *,
        normalized_input: NormalizedReportInput,
        usable: list[_UsableSource],
        categories: list[str],
        participant_labels: dict[str, str],
    ) -> LlmReportDraft:
        usable_payload = [
            {
                "sourceIndex": item.index,
                "sourceType": item.source_type.value,
                "participantRef": item.participant_ref,
                "checklistItemId": item.checklist_item_id,
                "category": item.category,
                "semanticText": item.semantic_text,
            }
            for item in usable
        ]
        user_prompt = build_user_prompt(
            normalized_input,
            usable_sources=usable_payload,
            categories=categories,
            participant_labels=participant_labels,
        )
        if len(user_prompt) > MAX_PROMPT_CHARS:
            raise ReportGenerationError(
                "INPUT_TOO_LARGE",
                "built user prompt exceeds size limit",
            )
        try:
            raw = await self.provider.complete_json(SYSTEM_PROMPT, user_prompt)
        except LlmProviderError:
            # Do not chain the original exception: provider/model text must not
            # appear in AI-007 logger.exception tracebacks.
            raise ReportGenerationError(
                "PROVIDER_FAILED",
                "LLM provider call failed",
                retryable=True,
            ) from None

        return self._validate_draft_payload(raw)

    def _validate_draft_payload(self, raw: dict[str, Any]) -> LlmReportDraft:
        schema = llm_report_draft_json_schema()
        try:
            jsonschema.validate(instance=raw, schema=schema)
        except jsonschema.ValidationError:
            raise ReportGenerationError(
                "SCHEMA_VALIDATION_FAILED",
                "LLM draft failed JSON Schema validation",
                retryable=True,
            ) from None
        try:
            return LlmReportDraft.model_validate(raw)
        except ValidationError:
            raise ReportGenerationError(
                "PYDANTIC_VALIDATION_FAILED",
                "Pydantic validation failed for LLM draft",
                retryable=True,
            ) from None

    def _validate_draft_references(
        self,
        *,
        draft: LlmReportDraft,
        categories: list[str],
        usable: list[_UsableSource],
        report_id: int,
    ) -> None:
        category_set = set(categories)

        for candidate in draft.opinionCandidates:
            if candidate.sourceIndex < 0 or candidate.sourceIndex >= len(usable):
                raise ReportGenerationError(
                    "INVALID_REFERENCE",
                    "LLM draft references out-of-range sourceIndex",
                    retryable=True,
                )
            source = usable[candidate.sourceIndex]
            if source.source_type not in {ReportSourceType.TEXT, ReportSourceType.STT}:
                raise ReportGenerationError(
                    "INVALID_REFERENCE",
                    "LLM draft references non-opinion usable source",
                    retryable=True,
                )
            if candidate.participantRef != source.participant_ref:
                raise ReportGenerationError(
                    "INVALID_REFERENCE",
                    "LLM draft participantRef does not match sourceIndex",
                    retryable=True,
                )
            if candidate.category != source.category:
                raise ReportGenerationError(
                    "INVALID_REFERENCE",
                    "LLM draft category does not match sourceIndex",
                    retryable=True,
                )
            if candidate.category not in category_set:
                raise ReportGenerationError(
                    "INVALID_REFERENCE",
                    "LLM draft references unknown category",
                    retryable=True,
                )
            if candidate.conclusionQuote is not None and not _quote_matches_source(
                quote=candidate.conclusionQuote,
                semantic_text=source.semantic_text,
            ):
                logger.info(
                    "report conclusion quote unmatched reportId=%s sourceIndex=%s",
                    report_id,
                    candidate.sourceIndex,
                )
                candidate.conclusionQuote = None
        seen_categories: set[str] = set()
        summarized_source_indexes: set[int] = set()
        for item in draft.categorySummaries:
            if item.category not in category_set:
                raise ReportGenerationError(
                    "INVALID_REFERENCE",
                    "LLM category summary references unknown category",
                    retryable=True,
                )
            if item.category in seen_categories:
                raise ReportGenerationError(
                    "INVALID_REFERENCE",
                    "LLM category summary contains duplicate category",
                    retryable=True,
                )
            seen_categories.add(item.category)
            for source_index in item.sourceIndexes:
                if source_index >= len(usable):
                    raise ReportGenerationError(
                        "INVALID_REFERENCE",
                        "LLM category summary references out-of-range sourceIndex",
                        retryable=True,
                    )
                source = usable[source_index]
                if source.category != item.category:
                    raise ReportGenerationError(
                        "INVALID_REFERENCE",
                        "LLM category summary category does not match sourceIndex",
                        retryable=True,
                    )
                if source_index in summarized_source_indexes:
                    raise ReportGenerationError(
                        "INVALID_REFERENCE",
                        "LLM category summaries reference duplicate sourceIndex",
                        retryable=True,
                    )
                summarized_source_indexes.add(source_index)

        expected_source_indexes = set(range(len(usable)))
        if summarized_source_indexes != expected_source_indexes:
            raise ReportGenerationError(
                "INVALID_REFERENCE",
                "LLM category summaries do not cover every usable source",
                retryable=True,
            )

        # 요약은 자연어 키가 아니라 후보 index 로 참조한다. 범위만 맞으면 어떤 키로
        # 붙을지는 서버가 후보에서 결정적으로 유도하므로, 예전의 "키 불일치·중복 키"
        # 실패 모드는 존재하지 않는다.
        candidate_count = len(draft.opinionCandidates)
        for common_item in draft.commonOpinionSummaries:
            if any(
                index >= candidate_count for index in common_item.candidateIndexes
            ):
                raise ReportGenerationError(
                    "INVALID_REFERENCE",
                    "LLM common summary references out-of-range candidate index",
                    retryable=True,
                )
        for conflict_item in draft.conflictingOpinionSummaries:
            if any(
                index >= candidate_count for index in conflict_item.candidateIndexes
            ):
                raise ReportGenerationError(
                    "INVALID_REFERENCE",
                    "LLM conflict summary references out-of-range candidate index",
                    retryable=True,
                )


def _normalize_conclusion_quote(value: str) -> str:
    without_whitespace = re.sub(r"\s+", "", value)
    return without_whitespace.translate(CONCLUSION_QUOTE_PUNCTUATION)


def _quote_matches_source(*, quote: str, semantic_text: str) -> bool:
    normalized_quote = _normalize_conclusion_quote(quote)
    normalized_source = _normalize_conclusion_quote(semantic_text)
    return bool(normalized_quote) and normalized_quote in normalized_source


async def generate_report(
    normalized_input: NormalizedReportInput,
    *,
    provider: LlmProvider,
) -> ReportGenerationResult:
    """Module-level entrypoint for AI-007 orchestration."""
    return await ReportGenerationService(provider).generate_report(normalized_input)


def compute_metrics(normalized_input: NormalizedReportInput) -> ReportMetrics:
    total = len(normalized_input.checklistItems)
    completed = sum(1 for item in normalized_input.checklistItems if item.completed)
    if total == 0:
        rate = 0.0
    else:
        rate = round((completed / total) * 100.0, 1)
    source_ids = {source.sourceId for source in normalized_input.sources}
    return ReportMetrics(
        totalChecklistItemCount=total,
        completedChecklistItemCount=completed,
        averageCompletionRate=rate,
        fieldRecordCount=len(source_ids),
    )


def ordered_categories(normalized_input: NormalizedReportInput) -> list[str]:
    seen: list[str] = []
    for item in normalized_input.checklistItems:
        if item.category not in seen:
            seen.append(item.category)
    return seen


def build_participant_labels(
    normalized_input: NormalizedReportInput,
) -> dict[str, str]:
    return {
        participant.participantRef: f"참여자 {index}"
        for index, participant in enumerate(normalized_input.participants, start=1)
    }


def collect_usable_sources(
    normalized_input: NormalizedReportInput,
) -> list[_UsableSource]:
    checklist_category = {
        item.checklistItemId: item.category for item in normalized_input.checklistItems
    }
    usable: list[_UsableSource] = []
    index = 0
    for source in normalized_input.sources:
        if not _is_opinion_source(source):
            continue
        semantic = getattr(source, "semanticText", None)
        if not isinstance(semantic, str) or not semantic.strip():
            continue
        usable.append(
            _UsableSource(
                index=index,
                source_id=source.sourceId,
                participant_ref=source.participantRef,
                checklist_item_id=source.checklistItemId,
                category=checklist_category[source.checklistItemId],
                source_type=source.sourceType,
                semantic_text=semantic.strip(),
            )
        )
        index += 1
    return usable


def _is_opinion_source(source: NormalizedReportSource) -> bool:
    return source.sourceType in {ReportSourceType.TEXT, ReportSourceType.STT}


def build_insufficient_result(
    *,
    metrics: ReportMetrics,
    categories: list[str],
) -> ReportGenerationResult:
    return ReportGenerationResult(
        title=INSUFFICIENT_REPORT_TITLE,
        summary=INSUFFICIENT_REPORT_SUMMARY,
        metrics=metrics,
        topPositiveFeatures=[],
        topCautionFeatures=[],
        commonOpinions=[],
        conflictingOpinions=[],
        categories=[
            ReportCategory(
                category=category,
                summary=INSUFFICIENT_CATEGORY_SUMMARY,
                positiveOpinionCount=0,
                cautionOpinionCount=0,
                dataSufficient=False,
                participantOpinions=[],
            )
            for category in categories
        ],
    )


def assemble_result(
    *,
    draft: LlmReportDraft,
    metrics: ReportMetrics,
    categories: list[str],
    participant_labels: dict[str, str],
    participant_order: list[str],
) -> ReportGenerationResult:
    feature_map: dict[tuple[OpinionType, str], _FeatureAgg] = {}
    common_map: dict[tuple[str, str, OpinionType], _CommonAgg] = {}
    conflict_map: dict[tuple[str, str], _ConflictAgg] = {}
    participant_opinion_map: dict[
        tuple[str, str, OpinionType], _ParticipantOpinionAgg
    ] = {}
    category_opinion_participants: dict[str, set[str]] = defaultdict(set)

    for candidate in draft.opinionCandidates:
        _accumulate_feature(feature_map, candidate)
        _accumulate_common(common_map, candidate)
        _accumulate_conflict(conflict_map, candidate)
        _accumulate_participant_opinion(participant_opinion_map, candidate)
        category_opinion_participants[candidate.category].add(candidate.participantRef)

    top_positive = _select_top_features(feature_map, OpinionType.POSITIVE, participant_order)
    top_caution = _select_top_features(feature_map, OpinionType.CAUTION, participant_order)

    # 요약이 가리키는 후보들에서 집계 키를 유도한다. 같은 키에 요약이 두 번 오면
    # 먼저 온 것을 유지한다(결정적 first-wins). 키가 어떤 집계 그룹과도 안 맞으면
    # 그 요약만 안 쓰이고 중립 폴백 문구가 대신 나간다 — 리포트는 실패하지 않는다.
    common_summaries: dict[tuple[str, str, OpinionType], str] = {}
    for item in draft.commonOpinionSummaries:
        for index in item.candidateIndexes:
            referenced = draft.opinionCandidates[index]
            common_summaries.setdefault(
                (referenced.category, referenced.label, referenced.opinionType),
                item.summary,
            )
    conflict_summaries: dict[tuple[str, str], str] = {}
    for item in draft.conflictingOpinionSummaries:
        for index in item.candidateIndexes:
            referenced = draft.opinionCandidates[index]
            conflict_summaries.setdefault(
                (referenced.category, referenced.label),
                item.summary,
            )
    category_summaries = {
        item.category: item.summary for item in draft.categorySummaries
    }

    common_opinions = _build_common_opinions(
        common_map=common_map,
        summaries=common_summaries,
        participant_order=participant_order,
    )
    conflicting_opinions = _build_conflicting_opinions(
        conflict_map=conflict_map,
        summaries=conflict_summaries,
        participant_order=participant_order,
    )

    participant_count = len(participant_order)
    report_categories: list[ReportCategory] = []
    for category in categories:
        opinions = [
            ParticipantOpinion(
                participantRef=agg.participant_ref,
                participantLabel=participant_labels[agg.participant_ref],
                opinionType=agg.opinion_type,
                summary=_combine_participant_summaries(agg),
            )
            for key, agg in sorted(
                participant_opinion_map.items(),
                key=lambda item: (
                    participant_order.index(item[1].participant_ref),
                    0 if item[1].opinion_type == OpinionType.POSITIVE else 1,
                    item[1].first_index,
                ),
            )
            if key[1] == category
        ]
        positive_count = sum(
            1 for opinion in opinions if opinion.opinionType == OpinionType.POSITIVE
        )
        caution_count = sum(
            1 for opinion in opinions if opinion.opinionType == OpinionType.CAUTION
        )
        distinct_participants = category_opinion_participants.get(category, set())
        sufficient = _is_data_sufficient(
            participant_count=participant_count,
            distinct_opinion_participants=len(distinct_participants),
        )
        summary = _category_summary(
            sufficient=sufficient,
            llm_summary=category_summaries.get(category),
            positive_count=positive_count,
            caution_count=caution_count,
        )
        report_categories.append(
            ReportCategory(
                category=category,
                summary=summary,
                positiveOpinionCount=positive_count,
                cautionOpinionCount=caution_count,
                dataSufficient=sufficient,
                participantOpinions=opinions,
            )
        )

    try:
        return ReportGenerationResult(
            title=draft.title,
            summary=draft.summary,
            metrics=metrics,
            topPositiveFeatures=top_positive,
            topCautionFeatures=top_caution,
            commonOpinions=common_opinions,
            conflictingOpinions=conflicting_opinions,
            categories=report_categories,
        )
    except ValidationError:
        raise ReportGenerationError(
            "PYDANTIC_VALIDATION_FAILED",
            "Final report result failed validation",
            retryable=True,
        ) from None


def _category_summary(
    *,
    sufficient: bool,
    llm_summary: str | None,
    positive_count: int,
    caution_count: int,
) -> str:
    if llm_summary and llm_summary.strip():
        return llm_summary.strip()
    if not sufficient:
        return INSUFFICIENT_CATEGORY_SUMMARY
    return (
        f"해당 카테고리에서 긍정 의견 {positive_count}건과 "
        f"주의 의견 {caution_count}건이 확인되었습니다."
    )


def _combine_participant_summaries(agg: _ParticipantOpinionAgg) -> str:
    ordered = sorted(agg.summary_entries, key=lambda item: (item[0], item[1]))
    unique: list[str] = []
    seen: set[str] = set()
    for _index, summary in ordered:
        if summary in seen:
            continue
        seen.add(summary)
        unique.append(summary)
    combined = PARTICIPANT_SUMMARY_JOIN.join(unique)
    if len(combined) > MAX_PARTICIPANT_OPINION_SUMMARY_CHARS:
        raise ReportGenerationError(
            "OUTPUT_TOO_LARGE",
            "combined participant opinion summary exceeds configured limit",
        )
    return combined


def _accumulate_feature(
    feature_map: dict[tuple[OpinionType, str], _FeatureAgg],
    candidate: LlmOpinionCandidate,
) -> None:
    key = (candidate.opinionType, candidate.label)
    existing = feature_map.get(key)
    if existing is None:
        feature_map[key] = _FeatureAgg(
            label=candidate.label,
            opinion_type=candidate.opinionType,
            participant_refs={candidate.participantRef},
            first_index=candidate.sourceIndex,
            summary=candidate.summary,
        )
        return
    existing.participant_refs.add(candidate.participantRef)
    if candidate.sourceIndex < existing.first_index:
        existing.first_index = candidate.sourceIndex
        existing.summary = candidate.summary


def _accumulate_common(
    common_map: dict[tuple[str, str, OpinionType], _CommonAgg],
    candidate: LlmOpinionCandidate,
) -> None:
    key = (candidate.category, candidate.label, candidate.opinionType)
    existing = common_map.get(key)
    if existing is None:
        common_map[key] = _CommonAgg(
            category=candidate.category,
            label=candidate.label,
            opinion_type=candidate.opinionType,
            participant_refs={candidate.participantRef},
            first_index=candidate.sourceIndex,
        )
        return
    existing.participant_refs.add(candidate.participantRef)
    if candidate.sourceIndex < existing.first_index:
        existing.first_index = candidate.sourceIndex


def _accumulate_conflict(
    conflict_map: dict[tuple[str, str], _ConflictAgg],
    candidate: LlmOpinionCandidate,
) -> None:
    key = (candidate.category, candidate.label)
    existing = conflict_map.get(key)
    if existing is None:
        existing = _ConflictAgg(
            category=candidate.category,
            label=candidate.label,
            positive_refs=set(),
            caution_refs=set(),
            first_index=candidate.sourceIndex,
        )
        conflict_map[key] = existing
    if candidate.opinionType == OpinionType.POSITIVE:
        existing.positive_refs.add(candidate.participantRef)
    else:
        existing.caution_refs.add(candidate.participantRef)
    if candidate.sourceIndex < existing.first_index:
        existing.first_index = candidate.sourceIndex


def _accumulate_participant_opinion(
    opinion_map: dict[tuple[str, str, OpinionType], _ParticipantOpinionAgg],
    candidate: LlmOpinionCandidate,
) -> None:
    key = (candidate.participantRef, candidate.category, candidate.opinionType)
    existing = opinion_map.get(key)
    if existing is None:
        opinion_map[key] = _ParticipantOpinionAgg(
            participant_ref=candidate.participantRef,
            category=candidate.category,
            opinion_type=candidate.opinionType,
            first_index=candidate.sourceIndex,
            summary_entries=[(candidate.sourceIndex, candidate.summary)],
        )
        return
    existing.summary_entries.append((candidate.sourceIndex, candidate.summary))
    if candidate.sourceIndex < existing.first_index:
        existing.first_index = candidate.sourceIndex


def _ordered_refs(refs: set[str], participant_order: list[str]) -> list[str]:
    return [ref for ref in participant_order if ref in refs]


def _select_top_features(
    feature_map: dict[tuple[OpinionType, str], _FeatureAgg],
    opinion_type: OpinionType,
    participant_order: list[str],
) -> list[ReportFeature]:
    selected = [
        agg
        for (otype, _label), agg in feature_map.items()
        if otype == opinion_type
    ]
    selected.sort(
        key=lambda item: (
            -len(item.participant_refs),
            item.first_index,
            item.label,
        )
    )
    features: list[ReportFeature] = []
    for rank, agg in enumerate(selected[:MAX_TOP_FEATURES], start=1):
        refs = _ordered_refs(agg.participant_refs, participant_order)
        features.append(
            ReportFeature(
                rank=rank,
                label=agg.label,
                summary=agg.summary,
                mentionCount=len(refs),
                participantRefs=refs,
            )
        )
    return features


def _neutral_common_summary(*, participant_count: int, label: str) -> str:
    return (
        f"{participant_count}명의 참여자가 '{label}'에 대해 "
        "같은 유형의 의견을 남겼습니다."
    )


def _neutral_conflict_summary(*, label: str) -> str:
    return f"'{label}'에 대해 긍정 의견과 주의 의견이 함께 확인되었습니다."


def _build_common_opinions(
    *,
    common_map: dict[tuple[str, str, OpinionType], _CommonAgg],
    summaries: dict[tuple[str, str, OpinionType], str],
    participant_order: list[str],
) -> list[CommonOpinion]:
    items = [agg for agg in common_map.values() if len(agg.participant_refs) >= 2]
    items.sort(
        key=lambda item: (
            -len(item.participant_refs),
            item.first_index,
            item.label,
        )
    )
    result: list[CommonOpinion] = []
    for agg in items:
        refs = _ordered_refs(agg.participant_refs, participant_order)
        summary = summaries.get((agg.category, agg.label, agg.opinion_type))
        if not summary:
            summary = _neutral_common_summary(
                participant_count=len(refs),
                label=agg.label,
            )
        result.append(
            CommonOpinion(
                category=agg.category,
                label=agg.label,
                opinionType=agg.opinion_type,
                summary=summary,
                participantCount=len(refs),
                participantRefs=refs,
            )
        )
    return result


def _build_conflicting_opinions(
    *,
    conflict_map: dict[tuple[str, str], _ConflictAgg],
    summaries: dict[tuple[str, str], str],
    participant_order: list[str],
) -> list[ConflictingOpinion]:
    items: list[_ConflictAgg] = []
    for agg in conflict_map.values():
        if not agg.positive_refs or not agg.caution_refs:
            continue
        combined = agg.positive_refs | agg.caution_refs
        if len(combined) < 2:
            continue
        items.append(agg)
    items.sort(
        key=lambda item: (
            -(len(item.positive_refs | item.caution_refs)),
            item.first_index,
            item.label,
        )
    )
    result: list[ConflictingOpinion] = []
    for agg in items:
        positive_refs = _ordered_refs(agg.positive_refs, participant_order)
        caution_refs = _ordered_refs(agg.caution_refs, participant_order)
        summary = summaries.get((agg.category, agg.label))
        if not summary:
            summary = _neutral_conflict_summary(label=agg.label)
        result.append(
            ConflictingOpinion(
                category=agg.category,
                label=agg.label,
                summary=summary,
                positiveParticipantCount=len(positive_refs),
                cautionParticipantCount=len(caution_refs),
                positiveParticipantRefs=positive_refs,
                cautionParticipantRefs=caution_refs,
            )
        )
    return result


def _is_data_sufficient(
    *,
    participant_count: int,
    distinct_opinion_participants: int,
) -> bool:
    if distinct_opinion_participants <= 0:
        return False
    if participant_count <= 1:
        return distinct_opinion_participants >= 1
    return distinct_opinion_participants >= 2
