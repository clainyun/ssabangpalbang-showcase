"""Builders for AI-007 Gate 3A worker tests."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

from app.schemas.report_evidence import (
    ClaimType,
    EvidenceClaim,
    EvidenceLinkResult,
    EvidenceRef,
    EvidenceRole,
)
from app.schemas.report_generation import (
    OpinionType,
    ReportCategory,
    ReportFeature,
    ReportGenerationResult,
    ReportMetrics,
)
from app.schemas.report_input import NormalizedReportInput
from app.schemas.report_request import ReportRequestedPayload
from app.services.report_generation import (
    build_insufficient_result,
    compute_metrics,
    ordered_categories,
)
from tests.fixtures.report_generation.builders import build_normalized_input

KST = timezone(timedelta(hours=9))


def sample_payload(
    *,
    study_id: int = 7,
    session_id: int = 3,
    apartment_id: int = 100,
) -> ReportRequestedPayload:
    return ReportRequestedPayload(
        studyId=study_id,
        sessionId=session_id,
        apartmentId=apartment_id,
        occurredAt=datetime(2026, 8, 1, 9, 15, 30, tzinfo=timezone.utc),
    )


def insufficient_normalized() -> NormalizedReportInput:
    return build_normalized_input(sources=[])


def sufficient_normalized() -> NormalizedReportInput:
    return build_normalized_input()


def insufficient_generation(
    normalized: NormalizedReportInput | None = None,
) -> ReportGenerationResult:
    source = normalized or insufficient_normalized()
    return build_insufficient_result(
        metrics=compute_metrics(source),
        categories=ordered_categories(source),
    )


def empty_claims_with_usable_sources(
    normalized: NormalizedReportInput | None = None,
) -> ReportGenerationResult:
    """Abnormal AI-005 shape: usable sources exist but zero semantic claims."""

    source = normalized or sufficient_normalized()
    categories = ordered_categories(source)
    return ReportGenerationResult(
        title="비정상 결과",
        summary="요약만 있고 claim이 없습니다.",
        metrics=compute_metrics(source),
        topPositiveFeatures=[],
        topCautionFeatures=[],
        commonOpinions=[],
        conflictingOpinions=[],
        categories=[
            ReportCategory(
                category=category,
                summary="카테고리 요약",
                positiveOpinionCount=0,
                cautionOpinionCount=0,
                dataSufficient=True,
                participantOpinions=[],
            )
            for category in categories
        ],
    )


def claimed_generation(
    normalized: NormalizedReportInput | None = None,
) -> ReportGenerationResult:
    source = normalized or sufficient_normalized()
    categories = ordered_categories(source) or ["교통"]
    return ReportGenerationResult(
        title="정상 리포트",
        summary="참여자 의견을 요약한 리포트입니다.",
        metrics=compute_metrics(source),
        topPositiveFeatures=[
            ReportFeature(
                rank=1,
                label="교통 편리",
                summary="지하철 접근이 편리합니다.",
                mentionCount=1,
                participantRefs=["P1"],
            )
        ],
        topCautionFeatures=[],
        commonOpinions=[],
        conflictingOpinions=[],
        categories=[
            ReportCategory(
                category=category,
                summary=f"{category} 요약",
                positiveOpinionCount=1 if category == categories[0] else 0,
                cautionOpinionCount=0,
                dataSufficient=True,
                participantOpinions=[],
            )
            for category in categories
        ],
    )


def sample_evidence_result() -> EvidenceLinkResult:
    recorded = datetime(2026, 7, 20, 14, 20, tzinfo=KST)
    return EvidenceLinkResult(
        claims=[
            EvidenceClaim(
                claimKey="FEATURE_POSITIVE|교통 편리|POSITIVE|P1",
                claimType=ClaimType.FEATURE_POSITIVE,
                category=None,
                label="교통 편리",
                opinionType=OpinionType.POSITIVE,
                participantRefs=["P1"],
                evidences=[
                    EvidenceRef(
                        sourceType="TEXT",
                        sourceId=201,
                        participantRef="P1",
                        checklistItemId=501,
                        category="교통",
                        recordedAt=recorded,
                        evidenceRole=EvidenceRole.SUPPORT,
                    )
                ],
                displayOrder=1,
            )
        ]
    )


def metrics_for(normalized: NormalizedReportInput) -> ReportMetrics:
    return compute_metrics(normalized)
