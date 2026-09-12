"""Deterministic AI-004 report-input normalization.

This module is intentionally free of database and model-provider concerns so
the same snapshot always produces the same normalized contract on retries.
"""

from __future__ import annotations

from collections import Counter
import unicodedata

from app.schemas.report_input import (
    ChecklistItemSource,
    ExcludedSource,
    ExcludedSourceReason,
    FieldRecordSource,
    NormalizationQualityIssue,
    NormalizationSummary,
    NormalizedChecklistItem,
    NormalizedParticipant,
    NormalizedPhotoMetadata,
    NormalizedPhotoSource,
    NormalizedReportInput,
    NormalizedReportSource,
    NormalizedSttSource,
    NormalizedTextSource,
    QualityIssueCode,
    ReportNormalizationSource,
    ReportSourceType,
)


class ReportInputNormalizationError(ValueError):
    """A source snapshot cannot be normalized without guessing."""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


def normalize_report_input(
    source: ReportNormalizationSource,
) -> NormalizedReportInput:
    """Convert authoritative rows into a privacy-safe versioned contract."""

    _require_terminal_snapshot(source)
    _validate_source_manifest(source)
    _validate_source_timestamps(source)
    _validate_checklist_timestamps(source)
    participants, member_to_ref = _normalize_participants(source)
    checklist_items, checklist_members, quality_issues = _normalize_checklists(
        source,
        member_to_ref,
    )
    _validate_incomplete_stt_jobs(
        source,
        member_to_ref,
        checklist_members,
    )
    sources, excluded_sources, duplicate_count = _normalize_sources(
        source,
        member_to_ref,
        checklist_items,
        checklist_members,
    )
    quality_issues.extend(_incomplete_stt_issues(source, member_to_ref))
    if not sources:
        quality_issues.append(
            NormalizationQualityIssue(code=QualityIssueCode.NO_USABLE_SOURCES)
        )

    completed_count = sum(item.completed for item in checklist_items)
    summary = NormalizationSummary(
        participantCount=len(participants),
        checklistItemCount=len(checklist_items),
        completedChecklistItemCount=completed_count,
        inputRecordCount=len(source.fieldRecords),
        includedSourceCount=len(sources),
        excludedRecordCount=len(excluded_sources),
        duplicateRecordCount=duplicate_count,
        incompleteSttJobCount=len(source.incompleteSttJobs),
    )
    return NormalizedReportInput(
        reportId=source.reportId,
        studyId=source.studyId,
        apartmentId=source.apartmentId,
        fieldSessionId=source.fieldSessionId,
        sessionEndedAt=source.sessionEndedAt,
        snapshotAt=source.snapshotAt,
        participants=participants,
        checklistItems=checklist_items,
        sources=sources,
        excludedSources=excluded_sources,
        qualityIssues=quality_issues,
        normalizationSummary=summary,
    )


def _require_terminal_snapshot(source: ReportNormalizationSource) -> None:
    if source.sessionStatus != "ENDED" or source.sessionEndedAt is None:
        raise ReportInputNormalizationError(
            "SESSION_NOT_ENDED",
            "field session must be ended before report normalization",
        )

    in_progress = [
        participant.fieldParticipantId
        for participant in source.participants
        if participant.status != "ENDED" or participant.endedAt is None
    ]
    if in_progress:
        raise ReportInputNormalizationError(
            "PARTICIPANT_NOT_ENDED",
            "all field participants must be ended before report normalization",
        )
    if source.sessionEndedAt > source.snapshotAt:
        raise ReportInputNormalizationError(
            "SNAPSHOT_TIME_INVALID",
            "snapshot time must not precede the field-session end time",
        )

    invalid_participant_time = any(
        participant.startedAt < source.sessionStartedAt
        or participant.startedAt > source.sessionEndedAt
        or participant.endedAt > source.sessionEndedAt
        or participant.endedAt > source.snapshotAt
        for participant in source.participants
    )
    if invalid_participant_time:
        raise ReportInputNormalizationError(
            "PARTICIPANT_TIMESTAMP_INVALID",
            "participant timestamps must stay within the ended field session",
        )


def _validate_source_manifest(source: ReportNormalizationSource) -> None:
    if len(source.authoritativeSourceIds) != len(
        set(source.authoritativeSourceIds)
    ):
        raise ReportInputNormalizationError(
            "DUPLICATE_SOURCE_MANIFEST",
            "authoritativeSourceIds must be unique",
        )

    manifest_ids = set(source.authoritativeSourceIds)
    payload_ids = {record.sourceId for record in source.fieldRecords}

    if payload_ids - manifest_ids:
        raise ReportInputNormalizationError(
            "SOURCE_NOT_IN_MANIFEST",
            "field record payload contains a non-authoritative sourceId",
        )
    if manifest_ids - payload_ids:
        raise ReportInputNormalizationError(
            "SOURCE_PAYLOAD_MISSING",
            "an authoritative sourceId has no field record payload",
        )


def _validate_source_timestamps(source: ReportNormalizationSource) -> None:
    for record in source.fieldRecords:
        if (
            record.recordedAt < source.sessionStartedAt
            or record.recordedAt > source.snapshotAt
            or record.updatedAt < record.recordedAt
            or record.updatedAt > source.snapshotAt
        ):
            raise ReportInputNormalizationError(
                "SOURCE_TIMESTAMP_INVALID",
                "field record timestamps must be ordered within the snapshot",
            )


def _validate_checklist_timestamps(source: ReportNormalizationSource) -> None:
    if any(
        item.completedAt is not None
        and (
            item.completedAt < source.sessionStartedAt
            or item.completedAt > source.snapshotAt
        )
        for item in source.checklistItems
    ):
        raise ReportInputNormalizationError(
            "CHECKLIST_TIMESTAMP_INVALID",
            "checklist completion timestamps must stay within the snapshot",
        )


def _validate_incomplete_stt_jobs(
    source: ReportNormalizationSource,
    member_to_ref: dict[int, str],
    checklist_members: dict[int, int],
) -> None:
    stt_id_counts = Counter(job.sttId for job in source.incompleteSttJobs)
    if any(count > 1 for count in stt_id_counts.values()):
        raise ReportInputNormalizationError(
            "DUPLICATE_STT_JOB",
            "sttId must be unique in a report snapshot",
        )

    if any(
        job.requestedAt < source.sessionStartedAt
        or job.requestedAt > source.snapshotAt
        for job in source.incompleteSttJobs
    ):
        raise ReportInputNormalizationError(
            "STT_TIMESTAMP_INVALID",
            "STT request timestamps must stay within the snapshot",
        )

    for job in source.incompleteSttJobs:
        if job.memberId not in member_to_ref:
            raise ReportInputNormalizationError(
                "STT_OWNER_NOT_PARTICIPANT",
                "an incomplete STT job owner must be a field participant",
            )
        if job.checklistItemId not in checklist_members:
            raise ReportInputNormalizationError(
                "STT_CHECKLIST_ITEM_MISSING",
                "an incomplete STT job must reference a normalized checklist item",
            )
        if checklist_members[job.checklistItemId] != job.memberId:
            raise ReportInputNormalizationError(
                "STT_CHECKLIST_OWNER_MISMATCH",
                "an incomplete STT job owner must own its checklist item",
            )


def _normalize_participants(source: ReportNormalizationSource) -> tuple[
    list[NormalizedParticipant], dict[int, str]
]:
    if not source.participants:
        raise ReportInputNormalizationError(
            "PARTICIPANT_MISSING",
            "at least one ended field participant is required",
        )

    participant_id_counts = Counter(
        item.fieldParticipantId for item in source.participants
    )
    if any(count > 1 for count in participant_id_counts.values()):
        raise ReportInputNormalizationError(
            "DUPLICATE_PARTICIPANT",
            "fieldParticipantId must be unique in a report snapshot",
        )

    member_counts = Counter(item.memberId for item in source.participants)
    duplicate_members = [
        member_id for member_id, count in member_counts.items() if count > 1
    ]
    if duplicate_members:
        raise ReportInputNormalizationError(
            "DUPLICATE_PARTICIPANT",
            "a member must have only one field-participant row",
        )

    sorted_participants = sorted(
        source.participants,
        key=lambda item: (
            item.startedAt,
            item.fieldParticipantId,
        ),
    )
    member_to_ref = {
        item.memberId: f"P{index}"
        for index, item in enumerate(sorted_participants, start=1)
    }
    participants = [
        NormalizedParticipant(
            participantRef=member_to_ref[item.memberId],
            startedAt=item.startedAt,
            endedAt=item.endedAt,
        )
        for item in sorted_participants
    ]
    return participants, member_to_ref


def _normalize_checklists(
    source: ReportNormalizationSource,
    member_to_ref: dict[int, str],
) -> tuple[
    list[NormalizedChecklistItem],
    dict[int, int],
    list[NormalizationQualityIssue],
]:
    item_counts = Counter(item.checklistItemId for item in source.checklistItems)
    if any(count > 1 for count in item_counts.values()):
        raise ReportInputNormalizationError(
            "DUPLICATE_CHECKLIST_ITEM",
            "checklistItemId must be unique in a report snapshot",
        )

    quality_issues: list[NormalizationQualityIssue] = []
    checklist_members: dict[int, int] = {}
    normalized_items: list[NormalizedChecklistItem] = []
    participant_order = {
        member_id: int(participant_ref[1:])
        for member_id, participant_ref in member_to_ref.items()
    }
    ordered_source_items = sorted(
        source.checklistItems,
        key=lambda item: (
            participant_order.get(item.memberId, 2**31 - 1),
            item.displayOrder,
            item.checklistItemId,
        ),
    )
    for item in ordered_source_items:
        participant_ref = member_to_ref.get(item.memberId)
        if participant_ref is None:
            quality_issues.append(
                NormalizationQualityIssue(
                    code=QualityIssueCode.CHECKLIST_OWNER_NOT_PARTICIPANT,
                    referenceId=str(item.checklistItemId),
                    detail="checklist owner is not a field participant",
                )
            )
            continue

        checklist_members[item.checklistItemId] = item.memberId
        normalized_items.append(
            _normalize_checklist_item(item, participant_ref)
        )
        if item.completed and item.completedAt is None:
            quality_issues.append(
                NormalizationQualityIssue(
                    code=QualityIssueCode.COMPLETION_TIMESTAMP_MISSING,
                    participantRef=participant_ref,
                    referenceId=str(item.checklistItemId),
                )
            )

    checklist_owner_members = {item.memberId for item in source.checklistItems}
    for member_id, participant_ref in member_to_ref.items():
        if member_id not in checklist_owner_members:
            quality_issues.append(
                NormalizationQualityIssue(
                    code=QualityIssueCode.MISSING_CHECKLIST,
                    participantRef=participant_ref,
                )
            )

    return normalized_items, checklist_members, quality_issues


def _normalize_checklist_item(
    item: ChecklistItemSource,
    participant_ref: str,
) -> NormalizedChecklistItem:
    return NormalizedChecklistItem(
        checklistItemId=item.checklistItemId,
        participantRef=participant_ref,
        category=item.category,
        title=item.title,
        subtitle=item.subtitle,
        displayOrder=item.displayOrder,
        fallback=item.fallback,
        completed=item.completed,
        completedAt=item.completedAt,
    )


def _normalize_sources(
    source: ReportNormalizationSource,
    member_to_ref: dict[int, str],
    checklist_items: list[NormalizedChecklistItem],
    checklist_members: dict[int, int],
) -> tuple[
    list[NormalizedReportSource],
    list[ExcludedSource],
    int,
]:
    checklist_ids = {item.checklistItemId for item in checklist_items}
    checklist_order = {
        item.checklistItemId: (
            int(item.participantRef[1:]),
            item.displayOrder,
            item.checklistItemId,
        )
        for item in checklist_items
    }
    normalized: list[NormalizedReportSource] = []
    excluded: list[ExcludedSource] = []
    duplicate_count = 0

    records_by_id: dict[int, list[FieldRecordSource]] = {}
    for record in source.fieldRecords:
        records_by_id.setdefault(record.sourceId, []).append(record)

    for source_id in sorted(records_by_id):
        records = records_by_id[source_id]
        canonical = records[0]
        canonical_payload = canonical.model_dump(mode="json")
        if any(
            item.model_dump(mode="json") != canonical_payload
            for item in records[1:]
        ):
            raise ReportInputNormalizationError(
                "CONFLICTING_SOURCE_ID",
                "one sourceId must not reference different record payloads",
            )
        exclusion = _validate_record(
            canonical,
            source,
            member_to_ref,
            checklist_ids,
            checklist_members,
        )
        if exclusion is not None:
            excluded.append(exclusion)
        else:
            participant_ref = member_to_ref[canonical.authorId]
            normalized.append(_normalize_record(canonical, participant_ref))

        for duplicate in records[1:]:
            duplicate_count += 1
            excluded.append(
                _excluded(
                    duplicate,
                    ExcludedSourceReason.DUPLICATED,
                    member_to_ref,
                )
            )

    normalized.sort(
        key=lambda item: (
            checklist_order[item.checklistItemId],
            item.recordedAt,
            item.sourceId,
        )
    )
    return normalized, excluded, duplicate_count


def _validate_record(
    record: FieldRecordSource,
    source: ReportNormalizationSource,
    member_to_ref: dict[int, str],
    checklist_ids: set[int],
    checklist_members: dict[int, int],
) -> ExcludedSource | None:
    if record.sessionId != source.fieldSessionId:
        return _excluded(
            record,
            ExcludedSourceReason.SESSION_MISMATCH,
            member_to_ref,
        )
    if record.deletedAt is not None:
        return _excluded(record, ExcludedSourceReason.DELETED, member_to_ref)
    if record.checklistItemId not in checklist_ids:
        return _excluded(
            record,
            ExcludedSourceReason.CHECKLIST_ITEM_MISSING,
            member_to_ref,
        )
    if record.authorId not in member_to_ref:
        return _excluded(
            record,
            ExcludedSourceReason.AUTHOR_NOT_PARTICIPANT,
            member_to_ref,
        )
    if checklist_members[record.checklistItemId] != record.authorId:
        return _excluded(
            record,
            ExcludedSourceReason.AUTHOR_CHECKLIST_MISMATCH,
            member_to_ref,
        )

    if record.sourceType in {ReportSourceType.TEXT, ReportSourceType.STT}:
        if record.sourceType == ReportSourceType.STT and record.sttStatus != "DONE":
            return _excluded(
                record,
                ExcludedSourceReason.STT_NOT_DONE,
                member_to_ref,
                detail=f"status={record.sttStatus or 'MISSING'}",
            )
        if record.textContent is None or not record.textContent.strip():
            return _excluded(
                record,
                ExcludedSourceReason.EMPTY_TEXT,
                member_to_ref,
            )
        return None

    photo = record.photoFile
    if photo is None:
        return _excluded(
            record,
            ExcludedSourceReason.PHOTO_METADATA_MISSING,
            member_to_ref,
        )
    available = (
        photo.uploadStatus == "COMPLETED"
        and photo.deletedAt is None
        and photo.contentType is not None
        and photo.contentType.lower().startswith("image/")
    )
    if not available:
        return _excluded(
            record,
            ExcludedSourceReason.PHOTO_UNAVAILABLE,
            member_to_ref,
        )
    return None


def _normalize_record(
    record: FieldRecordSource,
    participant_ref: str,
) -> NormalizedReportSource:
    common = {
        "sourceId": record.sourceId,
        "participantRef": participant_ref,
        "checklistItemId": record.checklistItemId,
        "recordedAt": record.recordedAt,
    }
    if record.sourceType == ReportSourceType.TEXT:
        return NormalizedTextSource(
            **common,
            semanticText=_normalize_semantic_text(record.textContent),
        )
    if record.sourceType == ReportSourceType.STT:
        return NormalizedSttSource(
            **common,
            semanticText=_normalize_semantic_text(record.textContent),
        )

    photo = record.photoFile
    return NormalizedPhotoSource(
        **common,
        photoMetadata=NormalizedPhotoMetadata(
            fileId=photo.fileId,
            contentType=photo.contentType,
            sizeBytes=photo.sizeBytes,
        ),
    )


def _normalize_semantic_text(value: str) -> str:
    line_normalized = value.replace("\r\n", "\n").replace("\r", "\n")
    return unicodedata.normalize("NFC", line_normalized).strip()


def _excluded(
    record: FieldRecordSource,
    reason: ExcludedSourceReason,
    member_to_ref: dict[int, str],
    detail: str | None = None,
) -> ExcludedSource:
    return ExcludedSource(
        sourceId=record.sourceId,
        sourceType=record.sourceType,
        participantRef=member_to_ref.get(record.authorId),
        checklistItemId=record.checklistItemId,
        recordedAt=record.recordedAt,
        reason=reason,
        detail=detail,
    )


def _incomplete_stt_issues(
    source: ReportNormalizationSource,
    member_to_ref: dict[int, str],
) -> list[NormalizationQualityIssue]:
    issues: list[NormalizationQualityIssue] = []
    for job in sorted(source.incompleteSttJobs, key=lambda item: item.sttId):
        code = (
            QualityIssueCode.STT_RESULT_MISSING
            if job.status == "DONE"
            else QualityIssueCode.STT_NOT_DONE
        )
        issues.append(
            NormalizationQualityIssue(
                code=code,
                participantRef=member_to_ref[job.memberId],
                referenceId=job.sttId,
                observedAt=job.requestedAt,
                detail=f"status={job.status}",
            )
        )
    return issues
