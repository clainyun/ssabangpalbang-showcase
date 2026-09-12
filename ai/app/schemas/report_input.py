"""Strict AI-004 contracts for normalized report-generation input.

The source models mirror a read-only PostgreSQL snapshot.  The normalized
models are the versioned boundary consumed by the later AI-005/AI-006 report
generation steps.  Public member identifiers and storage object keys must not
cross that boundary.
"""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


def _require_timezone(value: datetime, field_name: str) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name} must include a timezone")
    return value


def _strip_required(value: str, field_name: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{field_name} must not be blank")
    return normalized


class _ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ReportSourceType(StrEnum):
    TEXT = "TEXT"
    PHOTO = "PHOTO"
    STT = "STT"


class ParticipantSource(_ContractModel):
    fieldParticipantId: int = Field(ge=1)
    memberId: int = Field(ge=1)
    status: Literal["IN_PROGRESS", "ENDED"]
    startedAt: datetime
    endedAt: datetime | None = None

    @field_validator("startedAt", "endedAt")
    @classmethod
    def timestamps_have_timezone(
        cls, value: datetime | None, info
    ) -> datetime | None:
        if value is None:
            return None
        return _require_timezone(value, info.field_name)

    @model_validator(mode="after")
    def participation_times_are_ordered(self) -> "ParticipantSource":
        if self.endedAt is not None and self.endedAt < self.startedAt:
            raise ValueError("endedAt must not precede startedAt")
        return self


class ChecklistItemSource(_ContractModel):
    checklistId: int = Field(ge=1)
    checklistItemId: int = Field(ge=1)
    memberId: int = Field(ge=1)
    fallback: bool
    category: str = Field(min_length=1, max_length=30)
    title: str = Field(min_length=1)
    subtitle: str | None = None
    displayOrder: int = Field(ge=1)
    completed: bool
    completedAt: datetime | None = None

    @field_validator("category", "title")
    @classmethod
    def required_text_is_not_blank(cls, value: str, info) -> str:
        return _strip_required(value, info.field_name)

    @field_validator("completedAt")
    @classmethod
    def completed_at_has_timezone(
        cls, value: datetime | None
    ) -> datetime | None:
        if value is None:
            return None
        return _require_timezone(value, "completedAt")


class PhotoFileSource(_ContractModel):
    fileId: int = Field(ge=1)
    contentType: str | None = Field(default=None, max_length=100)
    sizeBytes: int | None = Field(default=None, ge=0)
    uploadStatus: Literal["PENDING", "COMPLETED", "FAILED", "DELETED"]
    expiresAt: datetime | None = None
    deletedAt: datetime | None = None

    @field_validator("expiresAt", "deletedAt")
    @classmethod
    def timestamps_have_timezone(
        cls, value: datetime | None, info
    ) -> datetime | None:
        if value is None:
            return None
        return _require_timezone(value, info.field_name)


class FieldRecordSource(_ContractModel):
    sourceId: int = Field(ge=1)
    sessionId: int = Field(ge=1)
    checklistItemId: int = Field(ge=1)
    authorId: int = Field(ge=1)
    sourceType: ReportSourceType
    textContent: str | None = None
    sttStatus: Literal["PENDING", "PROCESSING", "DONE", "FAILED"] | None = None
    photoFile: PhotoFileSource | None = None
    deletedAt: datetime | None = None
    recordedAt: datetime
    updatedAt: datetime

    @field_validator("deletedAt", "recordedAt", "updatedAt")
    @classmethod
    def timestamps_have_timezone(
        cls, value: datetime | None, info
    ) -> datetime | None:
        if value is None:
            return None
        return _require_timezone(value, info.field_name)

    @model_validator(mode="after")
    def payload_matches_source_type(self) -> "FieldRecordSource":
        if self.sourceType == ReportSourceType.TEXT:
            if self.sttStatus is not None or self.photoFile is not None:
                raise ValueError("TEXT must not contain STT or photo metadata")
            if self.textContent is not None and len(self.textContent) > 2000:
                raise ValueError("TEXT textContent must be at most 2000 characters")
        elif self.sourceType == ReportSourceType.STT:
            if self.photoFile is not None:
                raise ValueError("STT must not contain photo metadata")
        elif self.textContent is not None or self.sttStatus is not None:
            raise ValueError("PHOTO must not contain textContent or sttStatus")
        return self


class IncompleteSttJobSource(_ContractModel):
    sttId: str = Field(min_length=1, max_length=50)
    memberId: int = Field(ge=1)
    checklistItemId: int = Field(ge=1)
    status: Literal["PENDING", "PROCESSING", "DONE", "FAILED"]
    retryable: bool
    failCode: str | None = Field(default=None, max_length=100)
    requestedAt: datetime

    @field_validator("sttId")
    @classmethod
    def stt_id_is_not_blank(cls, value: str) -> str:
        return _strip_required(value, "sttId")

    @field_validator("requestedAt")
    @classmethod
    def requested_at_has_timezone(cls, value: datetime) -> datetime:
        return _require_timezone(value, "requestedAt")


class ReportNormalizationSource(_ContractModel):
    """Read-only source snapshot produced from authoritative database rows."""

    schemaVersion: Literal[1] = 1
    reportId: int = Field(ge=1)
    studyId: int = Field(ge=1)
    apartmentId: int = Field(ge=1)
    fieldSessionId: int = Field(ge=1)
    sessionStatus: Literal["IN_PROGRESS", "ENDED"]
    sessionStartedAt: datetime
    sessionEndedAt: datetime | None = None
    snapshotAt: datetime
    participants: list[ParticipantSource]
    checklistItems: list[ChecklistItemSource]
    authoritativeSourceIds: list[Annotated[int, Field(ge=1)]]
    fieldRecords: list[FieldRecordSource]
    incompleteSttJobs: list[IncompleteSttJobSource] = Field(default_factory=list)

    @field_validator("sessionStartedAt", "sessionEndedAt", "snapshotAt")
    @classmethod
    def timestamps_have_timezone(
        cls, value: datetime | None, info
    ) -> datetime | None:
        if value is None:
            return None
        return _require_timezone(value, info.field_name)

    @model_validator(mode="after")
    def snapshot_times_are_ordered(self) -> "ReportNormalizationSource":
        if self.sessionEndedAt is not None:
            if self.sessionEndedAt < self.sessionStartedAt:
                raise ValueError("sessionEndedAt must not precede sessionStartedAt")
            if self.sessionEndedAt > self.snapshotAt:
                raise ValueError("sessionEndedAt must not be after snapshotAt")
        if len(self.authoritativeSourceIds) != len(
            set(self.authoritativeSourceIds)
        ):
            raise ValueError("authoritativeSourceIds must be unique")
        return self


class ExcludedSourceReason(StrEnum):
    DELETED = "DELETED"
    DUPLICATED = "DUPLICATED"
    SESSION_MISMATCH = "SESSION_MISMATCH"
    CHECKLIST_ITEM_MISSING = "CHECKLIST_ITEM_MISSING"
    AUTHOR_NOT_PARTICIPANT = "AUTHOR_NOT_PARTICIPANT"
    AUTHOR_CHECKLIST_MISMATCH = "AUTHOR_CHECKLIST_MISMATCH"
    EMPTY_TEXT = "EMPTY_TEXT"
    STT_NOT_DONE = "STT_NOT_DONE"
    PHOTO_METADATA_MISSING = "PHOTO_METADATA_MISSING"
    PHOTO_UNAVAILABLE = "PHOTO_UNAVAILABLE"


class QualityIssueCode(StrEnum):
    MISSING_CHECKLIST = "MISSING_CHECKLIST"
    CHECKLIST_OWNER_NOT_PARTICIPANT = "CHECKLIST_OWNER_NOT_PARTICIPANT"
    COMPLETION_TIMESTAMP_MISSING = "COMPLETION_TIMESTAMP_MISSING"
    STT_NOT_DONE = "STT_NOT_DONE"
    STT_RESULT_MISSING = "STT_RESULT_MISSING"
    NO_USABLE_SOURCES = "NO_USABLE_SOURCES"


class NormalizedParticipant(_ContractModel):
    participantRef: str = Field(pattern=r"^P[1-9][0-9]*$")
    startedAt: datetime
    endedAt: datetime

    @field_validator("startedAt", "endedAt")
    @classmethod
    def timestamps_have_timezone(cls, value: datetime, info) -> datetime:
        return _require_timezone(value, info.field_name)

    @model_validator(mode="after")
    def participation_times_are_ordered(self) -> "NormalizedParticipant":
        if self.endedAt < self.startedAt:
            raise ValueError("endedAt must not precede startedAt")
        return self


class NormalizedChecklistItem(_ContractModel):
    checklistItemId: int = Field(ge=1)
    participantRef: str = Field(pattern=r"^P[1-9][0-9]*$")
    category: str = Field(min_length=1, max_length=30)
    title: str = Field(min_length=1)
    subtitle: str | None = None
    displayOrder: int = Field(ge=1)
    fallback: bool
    completed: bool
    completedAt: datetime | None = None

    @field_validator("completedAt")
    @classmethod
    def completed_at_has_timezone(
        cls, value: datetime | None
    ) -> datetime | None:
        if value is None:
            return None
        return _require_timezone(value, "completedAt")


class NormalizedPhotoMetadata(_ContractModel):
    fileId: int = Field(ge=1)
    contentType: str = Field(min_length=1, max_length=100)
    sizeBytes: int | None = Field(default=None, ge=0)

    @field_validator("contentType")
    @classmethod
    def content_type_is_not_blank(cls, value: str) -> str:
        return _strip_required(value, "contentType")


class _NormalizedSourceBase(_ContractModel):
    sourceId: int = Field(ge=1)
    participantRef: str = Field(pattern=r"^P[1-9][0-9]*$")
    checklistItemId: int = Field(ge=1)
    recordedAt: datetime

    @field_validator("recordedAt")
    @classmethod
    def recorded_at_has_timezone(cls, value: datetime) -> datetime:
        return _require_timezone(value, "recordedAt")


class NormalizedTextSource(_NormalizedSourceBase):
    sourceType: Literal[ReportSourceType.TEXT] = ReportSourceType.TEXT
    semanticText: str = Field(min_length=1, max_length=2000)

    @field_validator("semanticText")
    @classmethod
    def semantic_text_is_not_blank(cls, value: str) -> str:
        return _strip_required(value, "semanticText")


class NormalizedSttSource(_NormalizedSourceBase):
    sourceType: Literal[ReportSourceType.STT] = ReportSourceType.STT
    semanticText: str = Field(min_length=1)

    @field_validator("semanticText")
    @classmethod
    def semantic_text_is_not_blank(cls, value: str) -> str:
        return _strip_required(value, "semanticText")


class NormalizedPhotoSource(_NormalizedSourceBase):
    sourceType: Literal[ReportSourceType.PHOTO] = ReportSourceType.PHOTO
    photoMetadata: NormalizedPhotoMetadata


NormalizedReportSource = Annotated[
    NormalizedTextSource | NormalizedSttSource | NormalizedPhotoSource,
    Field(discriminator="sourceType"),
]


class ExcludedSource(_ContractModel):
    sourceId: int | None = Field(default=None, ge=1)
    referenceId: str | None = Field(default=None, min_length=1, max_length=100)
    sourceType: ReportSourceType | None = None
    participantRef: str | None = Field(
        default=None, pattern=r"^P[1-9][0-9]*$"
    )
    checklistItemId: int | None = Field(default=None, ge=1)
    recordedAt: datetime | None = None
    reason: ExcludedSourceReason
    detail: str | None = Field(default=None, max_length=200)

    @field_validator("recordedAt")
    @classmethod
    def recorded_at_has_timezone(
        cls, value: datetime | None
    ) -> datetime | None:
        if value is None:
            return None
        return _require_timezone(value, "recordedAt")

    @model_validator(mode="after")
    def has_source_or_reference(self) -> "ExcludedSource":
        if self.sourceId is None and self.referenceId is None:
            raise ValueError("sourceId or referenceId is required")
        return self


class NormalizationQualityIssue(_ContractModel):
    code: QualityIssueCode
    participantRef: str | None = Field(
        default=None, pattern=r"^P[1-9][0-9]*$"
    )
    referenceId: str | None = Field(default=None, min_length=1, max_length=100)
    observedAt: datetime | None = None
    detail: str | None = Field(default=None, max_length=200)

    @field_validator("observedAt")
    @classmethod
    def observed_at_has_timezone(
        cls, value: datetime | None
    ) -> datetime | None:
        if value is None:
            return None
        return _require_timezone(value, "observedAt")


class NormalizationSummary(_ContractModel):
    participantCount: int = Field(ge=0)
    checklistItemCount: int = Field(ge=0)
    completedChecklistItemCount: int = Field(ge=0)
    inputRecordCount: int = Field(ge=0)
    includedSourceCount: int = Field(ge=0)
    excludedRecordCount: int = Field(ge=0)
    duplicateRecordCount: int = Field(ge=0)
    incompleteSttJobCount: int = Field(ge=0)

    @model_validator(mode="after")
    def duplicate_count_is_part_of_excluded_count(self) -> "NormalizationSummary":
        if self.duplicateRecordCount > self.excludedRecordCount:
            raise ValueError(
                "duplicateRecordCount must not exceed excludedRecordCount"
            )
        return self


class NormalizedReportInput(_ContractModel):
    """Versioned, privacy-safe input consumed by report analysis."""

    schemaVersion: Literal[1] = 1
    reportId: int = Field(ge=1)
    studyId: int = Field(ge=1)
    apartmentId: int = Field(ge=1)
    fieldSessionId: int = Field(ge=1)
    sessionEndedAt: datetime
    snapshotAt: datetime
    participants: list[NormalizedParticipant] = Field(min_length=1)
    checklistItems: list[NormalizedChecklistItem]
    sources: list[NormalizedReportSource]
    excludedSources: list[ExcludedSource]
    qualityIssues: list[NormalizationQualityIssue]
    normalizationSummary: NormalizationSummary

    @field_validator("sessionEndedAt", "snapshotAt")
    @classmethod
    def timestamps_have_timezone(cls, value: datetime, info) -> datetime:
        return _require_timezone(value, info.field_name)

    @model_validator(mode="after")
    def references_and_counts_are_consistent(self) -> "NormalizedReportInput":
        if self.sessionEndedAt > self.snapshotAt:
            raise ValueError("sessionEndedAt must not be after snapshotAt")

        participant_refs = [item.participantRef for item in self.participants]
        if len(participant_refs) != len(set(participant_refs)):
            raise ValueError("participantRef must be unique")

        checklist_ids = [item.checklistItemId for item in self.checklistItems]
        if len(checklist_ids) != len(set(checklist_ids)):
            raise ValueError("checklistItemId must be unique")

        source_ids = [item.sourceId for item in self.sources]
        if len(source_ids) != len(set(source_ids)):
            raise ValueError("sourceId must be unique")

        participant_ref_set = set(participant_refs)
        checklist_id_set = set(checklist_ids)
        checklist_participants = {
            item.checklistItemId: item.participantRef
            for item in self.checklistItems
        }
        for item in self.checklistItems:
            if item.participantRef not in participant_ref_set:
                raise ValueError("checklist participantRef must exist")
        for participant in self.participants:
            if participant.endedAt > self.sessionEndedAt:
                raise ValueError(
                    "participant endedAt must not be after sessionEndedAt"
                )
        for source in self.sources:
            if source.participantRef not in participant_ref_set:
                raise ValueError("source participantRef must exist")
            if source.recordedAt > self.snapshotAt:
                raise ValueError("source recordedAt must not be after snapshotAt")
            if source.checklistItemId not in checklist_id_set:
                raise ValueError("source checklistItemId must exist")
            if (
                checklist_participants[source.checklistItemId]
                != source.participantRef
            ):
                raise ValueError(
                    "source participantRef must match checklist participantRef"
                )

        for excluded in self.excludedSources:
            if (
                excluded.participantRef is not None
                and excluded.participantRef not in participant_ref_set
            ):
                raise ValueError("excluded source participantRef must exist")
            if (
                excluded.recordedAt is not None
                and excluded.recordedAt > self.snapshotAt
            ):
                raise ValueError(
                    "excluded source recordedAt must not be after snapshotAt"
                )

        for issue in self.qualityIssues:
            if (
                issue.participantRef is not None
                and issue.participantRef not in participant_ref_set
            ):
                raise ValueError("quality issue participantRef must exist")
            if issue.observedAt is not None and issue.observedAt > self.snapshotAt:
                raise ValueError("quality issue observedAt must not be after snapshotAt")

        summary = self.normalizationSummary
        if summary.participantCount != len(self.participants):
            raise ValueError("participantCount does not match participants")
        if summary.checklistItemCount != len(self.checklistItems):
            raise ValueError("checklistItemCount does not match checklistItems")
        completed_count = sum(item.completed for item in self.checklistItems)
        if summary.completedChecklistItemCount != completed_count:
            raise ValueError(
                "completedChecklistItemCount does not match checklistItems"
            )
        if summary.includedSourceCount != len(self.sources):
            raise ValueError("includedSourceCount does not match sources")
        if summary.excludedRecordCount != len(self.excludedSources):
            raise ValueError("excludedRecordCount does not match excludedSources")
        duplicate_count = sum(
            item.reason == ExcludedSourceReason.DUPLICATED
            for item in self.excludedSources
        )
        if summary.duplicateRecordCount != duplicate_count:
            raise ValueError(
                "duplicateRecordCount does not match excludedSources"
            )
        incomplete_stt_count = sum(
            issue.code
            in {QualityIssueCode.STT_NOT_DONE, QualityIssueCode.STT_RESULT_MISSING}
            for issue in self.qualityIssues
        )
        if summary.incompleteSttJobCount != incomplete_stt_count:
            raise ValueError(
                "incompleteSttJobCount does not match qualityIssues"
            )
        no_usable_sources_count = sum(
            issue.code == QualityIssueCode.NO_USABLE_SOURCES
            for issue in self.qualityIssues
        )
        expected_no_usable_sources_count = 0 if self.sources else 1
        if no_usable_sources_count != expected_no_usable_sources_count:
            raise ValueError(
                "NO_USABLE_SOURCES must occur exactly once when sources is empty"
            )
        if summary.inputRecordCount != (
            summary.includedSourceCount + summary.excludedRecordCount
        ):
            raise ValueError("inputRecordCount must equal included plus excluded")
        return self


def normalized_report_input_json_schema() -> dict:
    return NormalizedReportInput.model_json_schema()
