"""AI-006 semantic claim ↔ TEXT/DONE STT evidence linking service.

Deterministic claimKey, coverage, duplicate removal, and sorting run in Python.
LLM only proposes sourceIndex mappings.
"""

from __future__ import annotations

import hashlib
import json
import logging
import re
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

import jsonschema
from pydantic import ValidationError

from app.exceptions.report_evidence import (
    CLAIM_KEY_COLLISION_MESSAGE,
    INPUT_TOO_LARGE_MESSAGE,
    INVALID_REFERENCE_MESSAGE,
    MISSING_EVIDENCE_MESSAGE,
    PROVIDER_FAILED_MESSAGE,
    PYDANTIC_VALIDATION_FAILED_MESSAGE,
    SCHEMA_VALIDATION_FAILED_MESSAGE,
    ReportEvidenceLinkError,
)
from app.prompts.report_evidence import SYSTEM_PROMPT, build_user_prompt
from app.providers.llm_provider import LlmProvider, LlmProviderError
from app.schemas.report_evidence import (
    ClaimType,
    EvidenceClaim,
    EvidenceLinkRequest,
    EvidenceLinkResult,
    EvidenceRef,
    EvidenceRole,
    LlmEvidenceDraft,
    llm_evidence_draft_json_schema,
)
from app.schemas.report_generation import (
    OpinionType,
    ReportGenerationResult,
)
from app.schemas.report_input import (
    NormalizedReportInput,
    NormalizedReportSource,
    ReportSourceType,
)

logger = logging.getLogger(__name__)

MAX_USABLE_SOURCES = 200
MAX_PROMPT_CHARS = 120_000

_CLAIM_TYPE_ORDER = {
    ClaimType.FEATURE_POSITIVE: 1,
    ClaimType.FEATURE_CAUTION: 2,
    ClaimType.COMMON: 3,
    ClaimType.CONFLICT: 4,
    ClaimType.PARTICIPANT_OPINION: 5,
}

_OPINION_TYPE_ORDER = {
    OpinionType.POSITIVE: 0,
    OpinionType.CAUTION: 1,
}

_PARTICIPANT_REF_PATTERN = re.compile(r"^P([1-9][0-9]*)$")


@dataclass(frozen=True)
class UsableEvidenceSource:
    index: int
    source_id: int
    participant_ref: str
    checklist_item_id: int
    category: str
    source_type: ReportSourceType
    semantic_text: str
    recorded_at: datetime


@dataclass
class ClaimSpec:
    claim_key: str
    claim_type: ClaimType
    category: str | None
    label: str | None
    opinion_type: OpinionType | None
    participant_refs: list[str]
    claim_text: str
    positive_participant_refs: list[str] = field(default_factory=list)
    caution_participant_refs: list[str] = field(default_factory=list)
    group_index: int = 0


def participant_ref_sort_key(ref: str) -> tuple[int, str]:
    match = _PARTICIPANT_REF_PATTERN.fullmatch(ref)
    if match:
        return (int(match.group(1)), ref)
    return (10**9, ref)


def sorted_participant_refs(refs: list[str] | set[str]) -> list[str]:
    return sorted(refs, key=participant_ref_sort_key)


def build_claim_key(claim_type: ClaimType | str, **fields: Any) -> str:
    """Build a stable claimKey from canonical fields (pure / testable)."""
    type_value = (
        claim_type.value if isinstance(claim_type, ClaimType) else str(claim_type)
    )
    canonical: dict[str, Any] = {"claimType": type_value}
    for key, value in fields.items():
        # Provider-only narrative fields must never affect claimKey stability.
        if key in {"claimText", "claim_text", "summary"}:
            continue
        if value is None:
            continue
        if key in {
            "participantRefs",
            "positiveParticipantRefs",
            "cautionParticipantRefs",
        } and isinstance(value, (list, set, tuple)):
            canonical[key] = sorted_participant_refs(list(value))
        else:
            canonical[key] = value
    serialized = json.dumps(
        canonical,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )
    digest = hashlib.sha256(serialized.encode("utf-8")).hexdigest()[:32]
    claim_key = f"{type_value}:{digest}"
    if len(claim_key) > 100:
        raise ReportEvidenceLinkError(
            "INVALID_REFERENCE",
            INVALID_REFERENCE_MESSAGE,
            retryable=False,
        )
    return claim_key


class ReportEvidenceLinkService:
    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def link_evidence(
        self, request: EvidenceLinkRequest
    ) -> EvidenceLinkResult:
        claims = build_claim_specs(
            request.generationResult,
            request.normalizedInput,
        )
        if not claims:
            logger.info(
                "report evidence link skipped reportId=%s claimCount=0",
                request.normalizedInput.reportId,
            )
            try:
                return EvidenceLinkResult(claims=[])
            except ValidationError:
                raise ReportEvidenceLinkError(
                    "PYDANTIC_VALIDATION_FAILED",
                    PYDANTIC_VALIDATION_FAILED_MESSAGE,
                    retryable=False,
                ) from None

        usable = collect_usable_evidence_sources(request.normalizedInput)
        if not usable:
            raise ReportEvidenceLinkError(
                "MISSING_EVIDENCE",
                MISSING_EVIDENCE_MESSAGE,
                retryable=True,
            )

        self._guard_input_size(usable)
        draft = await self._call_llm(claims=claims, usable=usable)
        assembled = assemble_evidence_claims(
            claims=claims,
            draft=draft,
            usable=usable,
        )
        try:
            return EvidenceLinkResult(claims=assembled)
        except ValidationError:
            raise ReportEvidenceLinkError(
                "PYDANTIC_VALIDATION_FAILED",
                PYDANTIC_VALIDATION_FAILED_MESSAGE,
                retryable=False,
            ) from None

    def _guard_input_size(self, usable: list[UsableEvidenceSource]) -> None:
        if len(usable) > MAX_USABLE_SOURCES:
            raise ReportEvidenceLinkError(
                "INPUT_TOO_LARGE",
                INPUT_TOO_LARGE_MESSAGE,
                retryable=False,
            )
        total_chars = sum(len(item.semantic_text) for item in usable)
        if total_chars > MAX_PROMPT_CHARS:
            raise ReportEvidenceLinkError(
                "INPUT_TOO_LARGE",
                INPUT_TOO_LARGE_MESSAGE,
                retryable=False,
            )

    async def _call_llm(
        self,
        *,
        claims: list[ClaimSpec],
        usable: list[UsableEvidenceSource],
    ) -> LlmEvidenceDraft:
        claim_payload = [_claim_payload(item) for item in claims]
        usable_payload = [
            {
                "sourceIndex": item.index,
                "sourceType": item.source_type.value,
                "sourceId": item.source_id,
                "participantRef": item.participant_ref,
                "checklistItemId": item.checklist_item_id,
                "category": item.category,
                "semanticText": item.semantic_text,
            }
            for item in usable
        ]
        user_prompt = build_user_prompt(
            claims=claim_payload,
            usable_sources=usable_payload,
        )
        if len(user_prompt) > MAX_PROMPT_CHARS:
            raise ReportEvidenceLinkError(
                "INPUT_TOO_LARGE",
                INPUT_TOO_LARGE_MESSAGE,
                retryable=False,
            )
        try:
            raw = await self.provider.complete_json(SYSTEM_PROMPT, user_prompt)
        except LlmProviderError:
            raise ReportEvidenceLinkError(
                "PROVIDER_FAILED",
                PROVIDER_FAILED_MESSAGE,
                retryable=True,
            ) from None

        return self._validate_draft_payload(raw)

    def _validate_draft_payload(self, raw: dict[str, Any]) -> LlmEvidenceDraft:
        schema = llm_evidence_draft_json_schema()
        try:
            jsonschema.validate(instance=raw, schema=schema)
        except jsonschema.ValidationError:
            raise ReportEvidenceLinkError(
                "SCHEMA_VALIDATION_FAILED",
                SCHEMA_VALIDATION_FAILED_MESSAGE,
                retryable=False,
            ) from None
        try:
            return LlmEvidenceDraft.model_validate(raw)
        except ValidationError:
            raise ReportEvidenceLinkError(
                "PYDANTIC_VALIDATION_FAILED",
                PYDANTIC_VALIDATION_FAILED_MESSAGE,
                retryable=False,
            ) from None


def collect_usable_evidence_sources(
    normalized_input: NormalizedReportInput,
) -> list[UsableEvidenceSource]:
    checklist_category = {
        item.checklistItemId: item.category
        for item in normalized_input.checklistItems
    }
    usable: list[UsableEvidenceSource] = []
    index = 0
    for source in normalized_input.sources:
        if not _is_text_or_stt(source):
            continue
        semantic = getattr(source, "semanticText", None)
        if not isinstance(semantic, str) or not semantic.strip():
            continue
        try:
            category = checklist_category[source.checklistItemId]
        except KeyError:
            raise ReportEvidenceLinkError(
                "INVALID_REFERENCE",
                INVALID_REFERENCE_MESSAGE,
                retryable=False,
            ) from None
        usable.append(
            UsableEvidenceSource(
                index=index,
                source_id=source.sourceId,
                participant_ref=source.participantRef,
                checklist_item_id=source.checklistItemId,
                category=category,
                source_type=source.sourceType,
                semantic_text=semantic.strip(),
                recorded_at=source.recordedAt,
            )
        )
        index += 1
    return usable


def build_claim_specs(
    generation_result: ReportGenerationResult,
    normalized_input: NormalizedReportInput,
) -> list[ClaimSpec]:
    specs: list[ClaimSpec] = []
    seen_keys: set[str] = set()

    def _register(spec: ClaimSpec) -> None:
        if spec.claim_key in seen_keys:
            raise ReportEvidenceLinkError(
                "INVALID_REFERENCE",
                CLAIM_KEY_COLLISION_MESSAGE,
                retryable=False,
            )
        seen_keys.add(spec.claim_key)
        specs.append(spec)

    for index, feature in enumerate(generation_result.topPositiveFeatures):
        claim_key = build_claim_key(
            ClaimType.FEATURE_POSITIVE,
            label=feature.label,
            opinionType=OpinionType.POSITIVE.value,
            participantRefs=feature.participantRefs,
        )
        _register(
            ClaimSpec(
                claim_key=claim_key,
                claim_type=ClaimType.FEATURE_POSITIVE,
                category=None,
                label=feature.label,
                opinion_type=OpinionType.POSITIVE,
                participant_refs=list(feature.participantRefs),
                claim_text=feature.summary,
                group_index=index,
            )
        )

    for index, feature in enumerate(generation_result.topCautionFeatures):
        claim_key = build_claim_key(
            ClaimType.FEATURE_CAUTION,
            label=feature.label,
            opinionType=OpinionType.CAUTION.value,
            participantRefs=feature.participantRefs,
        )
        _register(
            ClaimSpec(
                claim_key=claim_key,
                claim_type=ClaimType.FEATURE_CAUTION,
                category=None,
                label=feature.label,
                opinion_type=OpinionType.CAUTION,
                participant_refs=list(feature.participantRefs),
                claim_text=feature.summary,
                group_index=index,
            )
        )

    for index, opinion in enumerate(generation_result.commonOpinions):
        claim_key = build_claim_key(
            ClaimType.COMMON,
            category=opinion.category,
            label=opinion.label,
            opinionType=opinion.opinionType.value,
            participantRefs=opinion.participantRefs,
        )
        _register(
            ClaimSpec(
                claim_key=claim_key,
                claim_type=ClaimType.COMMON,
                category=opinion.category,
                label=opinion.label,
                opinion_type=opinion.opinionType,
                participant_refs=list(opinion.participantRefs),
                claim_text=opinion.summary,
                group_index=index,
            )
        )

    for index, conflict in enumerate(generation_result.conflictingOpinions):
        claim_key = build_claim_key(
            ClaimType.CONFLICT,
            category=conflict.category,
            label=conflict.label,
            positiveParticipantRefs=conflict.positiveParticipantRefs,
            cautionParticipantRefs=conflict.cautionParticipantRefs,
        )
        combined = list(
            dict.fromkeys(
                list(conflict.positiveParticipantRefs)
                + list(conflict.cautionParticipantRefs)
            )
        )
        _register(
            ClaimSpec(
                claim_key=claim_key,
                claim_type=ClaimType.CONFLICT,
                category=conflict.category,
                label=conflict.label,
                opinion_type=None,
                participant_refs=combined,
                claim_text=conflict.summary,
                positive_participant_refs=list(conflict.positiveParticipantRefs),
                caution_participant_refs=list(conflict.cautionParticipantRefs),
                group_index=index,
            )
        )

    category_order = {
        category.category: index
        for index, category in enumerate(generation_result.categories)
    }
    participant_order = {
        participant.participantRef: index
        for index, participant in enumerate(normalized_input.participants)
    }
    participant_specs: list[ClaimSpec] = []
    for category in generation_result.categories:
        if not category.dataSufficient:
            continue
        for opinion in category.participantOpinions:
            claim_key = build_claim_key(
                ClaimType.PARTICIPANT_OPINION,
                category=category.category,
                opinionType=opinion.opinionType.value,
                participantRef=opinion.participantRef,
            )
            if claim_key in seen_keys:
                raise ReportEvidenceLinkError(
                    "INVALID_REFERENCE",
                    CLAIM_KEY_COLLISION_MESSAGE,
                    retryable=False,
                )
            seen_keys.add(claim_key)
            participant_specs.append(
                ClaimSpec(
                    claim_key=claim_key,
                    claim_type=ClaimType.PARTICIPANT_OPINION,
                    category=category.category,
                    label=None,
                    opinion_type=opinion.opinionType,
                    participant_refs=[opinion.participantRef],
                    claim_text=opinion.summary,
                    group_index=0,
                )
            )

    participant_specs.sort(
        key=lambda item: (
            category_order.get(item.category or "", 10**9),
            participant_order.get(item.participant_refs[0], 10**9),
            _OPINION_TYPE_ORDER.get(item.opinion_type or OpinionType.POSITIVE, 9),
            item.claim_key,
        )
    )
    for index, item in enumerate(participant_specs):
        item.group_index = index
        specs.append(item)

    return specs


def assemble_evidence_claims(
    *,
    claims: list[ClaimSpec],
    draft: LlmEvidenceDraft,
    usable: list[UsableEvidenceSource],
) -> list[EvidenceClaim]:
    unique_keys = {item.claim_key for item in claims}
    if len(claims) != len(unique_keys):
        raise ReportEvidenceLinkError(
            "INVALID_REFERENCE",
            CLAIM_KEY_COLLISION_MESSAGE,
            retryable=False,
        )

    claim_by_key = {item.claim_key: item for item in claims}
    expected_keys = set(claim_by_key)
    normal_keys = [item.claimKey for item in draft.normalMappings]
    conflict_keys = [item.claimKey for item in draft.conflictMappings]

    if len(normal_keys) != len(set(normal_keys)):
        raise ReportEvidenceLinkError(
            "INVALID_REFERENCE",
            INVALID_REFERENCE_MESSAGE,
            retryable=False,
        )
    if len(conflict_keys) != len(set(conflict_keys)):
        raise ReportEvidenceLinkError(
            "INVALID_REFERENCE",
            INVALID_REFERENCE_MESSAGE,
            retryable=False,
        )
    if set(normal_keys) & set(conflict_keys):
        raise ReportEvidenceLinkError(
            "INVALID_REFERENCE",
            INVALID_REFERENCE_MESSAGE,
            retryable=False,
        )

    actual_keys = set(normal_keys) | set(conflict_keys)
    if actual_keys != expected_keys:
        raise ReportEvidenceLinkError(
            "INVALID_REFERENCE",
            INVALID_REFERENCE_MESSAGE,
            retryable=False,
        )

    expected_normal = {
        item.claim_key
        for item in claims
        if item.claim_type != ClaimType.CONFLICT
    }
    expected_conflict = {
        item.claim_key
        for item in claims
        if item.claim_type == ClaimType.CONFLICT
    }
    if set(normal_keys) != expected_normal or set(conflict_keys) != expected_conflict:
        raise ReportEvidenceLinkError(
            "INVALID_REFERENCE",
            INVALID_REFERENCE_MESSAGE,
            retryable=False,
        )

    try:
        assembled: list[EvidenceClaim] = []
        for mapping in draft.normalMappings:
            claim = claim_by_key[mapping.claimKey]
            evidences = _resolve_normal_evidences(
                claim=claim,
                source_indexes=mapping.sourceIndexes,
                usable=usable,
            )
            assembled.append(
                EvidenceClaim(
                    claimKey=claim.claim_key,
                    claimType=claim.claim_type,
                    category=claim.category,
                    label=claim.label,
                    opinionType=claim.opinion_type,
                    participantRefs=list(claim.participant_refs),
                    evidences=evidences,
                    displayOrder=1,
                )
            )

        for mapping in draft.conflictMappings:
            claim = claim_by_key[mapping.claimKey]
            evidences = _resolve_conflict_evidences(
                claim=claim,
                positive_indexes=mapping.positiveSourceIndexes,
                caution_indexes=mapping.cautionSourceIndexes,
                usable=usable,
            )
            assembled.append(
                EvidenceClaim(
                    claimKey=claim.claim_key,
                    claimType=claim.claim_type,
                    category=claim.category,
                    label=claim.label,
                    opinionType=None,
                    participantRefs=list(claim.participant_refs),
                    evidences=evidences,
                    displayOrder=1,
                )
            )

        assembled.sort(
            key=lambda item: (
                _CLAIM_TYPE_ORDER[item.claimType],
                claim_by_key[item.claimKey].group_index,
                item.claimKey,
            )
        )
        return [
            item.model_copy(update={"displayOrder": index})
            for index, item in enumerate(assembled, start=1)
        ]
    except ValidationError:
        raise ReportEvidenceLinkError(
            "PYDANTIC_VALIDATION_FAILED",
            PYDANTIC_VALIDATION_FAILED_MESSAGE,
            retryable=False,
        ) from None


def _resolve_normal_evidences(
    *,
    claim: ClaimSpec,
    source_indexes: list[int],
    usable: list[UsableEvidenceSource],
) -> list[EvidenceRef]:
    allowed_refs = set(claim.participant_refs)
    refs: list[EvidenceRef] = []
    seen: set[tuple[str, int]] = set()

    for source_index in source_indexes:
        source = _get_usable_source(source_index, usable)
        if source.participant_ref not in allowed_refs:
            raise ReportEvidenceLinkError(
                "INVALID_REFERENCE",
                INVALID_REFERENCE_MESSAGE,
                retryable=False,
            )
        if claim.category is not None and source.category != claim.category:
            raise ReportEvidenceLinkError(
                "INVALID_REFERENCE",
                INVALID_REFERENCE_MESSAGE,
                retryable=False,
            )
        key = (source.source_type.value, source.source_id)
        if key in seen:
            continue
        seen.add(key)
        refs.append(_to_evidence_ref(source, EvidenceRole.SUPPORT))

    if not refs:
        raise ReportEvidenceLinkError(
            "MISSING_EVIDENCE",
            MISSING_EVIDENCE_MESSAGE,
            retryable=True,
        )

    covered = {item.participantRef for item in refs}
    if not allowed_refs.issubset(covered):
        logger.info(
            "evidence coverage incomplete claimType=%s missingParticipantCount=%s",
            claim.claim_type.value,
            len(allowed_refs - covered),
        )
        raise ReportEvidenceLinkError(
            "MISSING_EVIDENCE",
            MISSING_EVIDENCE_MESSAGE,
            retryable=True,
        )

    return _sort_evidence_refs(refs)


def _resolve_conflict_evidences(
    *,
    claim: ClaimSpec,
    positive_indexes: list[int],
    caution_indexes: list[int],
    usable: list[UsableEvidenceSource],
) -> list[EvidenceRef]:
    positive_allowed = set(claim.positive_participant_refs)
    caution_allowed = set(claim.caution_participant_refs)
    if len(positive_allowed | caution_allowed) < 2:
        raise ReportEvidenceLinkError(
            "MISSING_EVIDENCE",
            MISSING_EVIDENCE_MESSAGE,
            retryable=True,
        )

    positive_refs: list[EvidenceRef] = []
    caution_refs: list[EvidenceRef] = []
    positive_seen: set[tuple[str, int]] = set()
    caution_seen: set[tuple[str, int]] = set()

    for source_index in positive_indexes:
        source = _get_usable_source(source_index, usable)
        if source.participant_ref not in positive_allowed:
            raise ReportEvidenceLinkError(
                "INVALID_REFERENCE",
                INVALID_REFERENCE_MESSAGE,
                retryable=False,
            )
        if claim.category is not None and source.category != claim.category:
            raise ReportEvidenceLinkError(
                "INVALID_REFERENCE",
                INVALID_REFERENCE_MESSAGE,
                retryable=False,
            )
        key = (source.source_type.value, source.source_id)
        if key in positive_seen:
            continue
        positive_seen.add(key)
        positive_refs.append(
            _to_evidence_ref(source, EvidenceRole.SUPPORT_POSITIVE)
        )

    for source_index in caution_indexes:
        source = _get_usable_source(source_index, usable)
        if source.participant_ref not in caution_allowed:
            raise ReportEvidenceLinkError(
                "INVALID_REFERENCE",
                INVALID_REFERENCE_MESSAGE,
                retryable=False,
            )
        if claim.category is not None and source.category != claim.category:
            raise ReportEvidenceLinkError(
                "INVALID_REFERENCE",
                INVALID_REFERENCE_MESSAGE,
                retryable=False,
            )
        key = (source.source_type.value, source.source_id)
        if key in caution_seen:
            continue
        caution_seen.add(key)
        caution_refs.append(
            _to_evidence_ref(source, EvidenceRole.SUPPORT_CAUTION)
        )

    if not positive_refs or not caution_refs:
        raise ReportEvidenceLinkError(
            "MISSING_EVIDENCE",
            MISSING_EVIDENCE_MESSAGE,
            retryable=True,
        )

    overlap = positive_seen & caution_seen
    if overlap:
        raise ReportEvidenceLinkError(
            "INVALID_REFERENCE",
            INVALID_REFERENCE_MESSAGE,
            retryable=False,
        )

    positive_covered = {item.participantRef for item in positive_refs}
    caution_covered = {item.participantRef for item in caution_refs}
    if not positive_allowed.issubset(positive_covered):
        logger.info(
            "conflict positive coverage incomplete missingParticipantCount=%s",
            len(positive_allowed - positive_covered),
        )
        raise ReportEvidenceLinkError(
            "MISSING_EVIDENCE",
            MISSING_EVIDENCE_MESSAGE,
            retryable=True,
        )
    if not caution_allowed.issubset(caution_covered):
        logger.info(
            "conflict caution coverage incomplete missingParticipantCount=%s",
            len(caution_allowed - caution_covered),
        )
        raise ReportEvidenceLinkError(
            "MISSING_EVIDENCE",
            MISSING_EVIDENCE_MESSAGE,
            retryable=True,
        )

    return _sort_evidence_refs(positive_refs + caution_refs)


def _get_usable_source(
    source_index: int,
    usable: list[UsableEvidenceSource],
) -> UsableEvidenceSource:
    if source_index < 0 or source_index >= len(usable):
        raise ReportEvidenceLinkError(
            "INVALID_REFERENCE",
            INVALID_REFERENCE_MESSAGE,
            retryable=False,
        )
    source = usable[source_index]
    if source.source_type not in {ReportSourceType.TEXT, ReportSourceType.STT}:
        raise ReportEvidenceLinkError(
            "INVALID_REFERENCE",
            INVALID_REFERENCE_MESSAGE,
            retryable=False,
        )
    return source


def _to_evidence_ref(
    source: UsableEvidenceSource,
    role: EvidenceRole,
) -> EvidenceRef:
    return EvidenceRef(
        sourceType=source.source_type,  # type: ignore[arg-type]
        sourceId=source.source_id,
        participantRef=source.participant_ref,
        checklistItemId=source.checklist_item_id,
        category=source.category,
        recordedAt=source.recorded_at,
        evidenceRole=role,
    )


def _sort_evidence_refs(refs: list[EvidenceRef]) -> list[EvidenceRef]:
    return sorted(
        refs,
        key=lambda item: (
            item.recordedAt,
            item.sourceId,
            item.sourceType.value,
        ),
    )


def _claim_payload(claim: ClaimSpec) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "claimKey": claim.claim_key,
        "claimType": claim.claim_type.value,
        "category": claim.category,
        "label": claim.label,
        "opinionType": (
            claim.opinion_type.value if claim.opinion_type is not None else None
        ),
        "participantRefs": list(claim.participant_refs),
        "claimText": claim.claim_text,
    }
    if claim.claim_type == ClaimType.CONFLICT:
        payload["positiveParticipantRefs"] = list(claim.positive_participant_refs)
        payload["cautionParticipantRefs"] = list(claim.caution_participant_refs)
    return payload


def _is_text_or_stt(source: NormalizedReportSource) -> bool:
    return source.sourceType in {ReportSourceType.TEXT, ReportSourceType.STT}
