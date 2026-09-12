"""Consistent, read-only loading of authoritative report input rows."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from sqlalchemy import URL, create_engine, text
from sqlalchemy.engine import Engine

from app.config import ReportDatabaseSettings
from app.schemas.report_input import (
    ChecklistItemSource,
    FieldRecordSource,
    IncompleteSttJobSource,
    ParticipantSource,
    PhotoFileSource,
    ReportNormalizationSource,
)


_CONTEXT_QUERY = text(
    """
    SELECT
        r.id AS report_id,
        r.study_id,
        r.apartment_id,
        s.apartment_id AS study_apartment_id,
        s.status AS study_status,
        s.deleted_at AS study_deleted_at,
        s.canceled_at AS study_canceled_at,
        fs.id AS field_session_id,
        fs.status AS session_status,
        fs.started_at AS session_started_at,
        fs.ended_at AS session_ended_at,
        CURRENT_TIMESTAMP AS snapshot_at
    FROM report r
    JOIN study s
      ON s.id = r.study_id
    LEFT JOIN field_session fs
      ON fs.study_id = r.study_id
    WHERE r.id = :report_id
    """
)

_PARTICIPANTS_QUERY = text(
    """
    SELECT
        fp.id AS field_participant_id,
        fp.member_id,
        fp.status,
        fp.started_at,
        fp.ended_at
    FROM report r
    JOIN field_session fs
      ON fs.study_id = r.study_id
    JOIN field_participant fp
      ON fp.session_id = fs.id
    WHERE r.id = :report_id
    ORDER BY fp.started_at ASC, fp.id ASC
    """
)

_CHECKLIST_ITEMS_QUERY = text(
    """
    SELECT
        c.id AS checklist_id,
        ci.id AS checklist_item_id,
        c.member_id,
        c.is_fallback,
        ci.category,
        ci.title,
        ci.subtitle,
        ci.display_order,
        COALESCE(ca.is_completed, FALSE) AS is_completed,
        ca.completed_at
    FROM report r
    JOIN field_session fs
      ON fs.study_id = r.study_id
    JOIN checklist c
      ON c.session_id = fs.id
    JOIN checklist_item ci
      ON ci.checklist_id = c.id
    LEFT JOIN checklist_answer ca
      ON ca.checklist_item_id = ci.id
    WHERE r.id = :report_id
    ORDER BY c.member_id ASC, ci.display_order ASC, ci.id ASC
    """
)

_FIELD_RECORDS_AND_STT_QUERY = text(
    """
    WITH target AS (
        SELECT
            r.study_id,
            fs.id AS session_id
        FROM report r
        JOIN field_session fs
          ON fs.study_id = r.study_id
        WHERE r.id = :report_id
    )
    SELECT
        'FIELD_RECORD'::text AS row_kind,
        fr.id AS stable_id,
        fr.id AS source_id,
        fr.session_id,
        fr.checklist_item_id,
        fr.author_id,
        fr.source_type::text AS source_type,
        fr.text_content,
        fr.stt_status::text AS stt_status,
        fr.deleted_at,
        fr.created_at AS recorded_at,
        fr.updated_at,
        fm.id AS photo_file_id,
        fm.owner_id AS photo_owner_id,
        fm.study_id AS photo_study_id,
        fm.file_usage::text AS photo_file_usage,
        fm.content_type::text AS photo_content_type,
        fm.size_bytes AS photo_size_bytes,
        fm.upload_status::text AS photo_upload_status,
        fm.expires_at AS photo_expires_at,
        fm.deleted_at AS photo_deleted_at,
        NULL::text AS stt_id,
        NULL::bigint AS stt_member_id,
        NULL::text AS stt_job_status,
        NULL::boolean AS stt_retryable,
        NULL::text AS stt_fail_code,
        NULL::timestamptz AS stt_requested_at
    FROM target t
    JOIN field_record fr
      ON fr.session_id = t.session_id
    LEFT JOIN file_meta fm
      ON fm.id = fr.photo_file_id

    UNION ALL

    SELECT
        'STT_JOB'::text AS row_kind,
        sj.id AS stable_id,
        NULL::bigint AS source_id,
        sj.session_id,
        sj.checklist_item_id,
        NULL::bigint AS author_id,
        'STT'::text AS source_type,
        NULL::text AS text_content,
        NULL::text AS stt_status,
        NULL::timestamptz AS deleted_at,
        NULL::timestamptz AS recorded_at,
        NULL::timestamptz AS updated_at,
        NULL::bigint AS photo_file_id,
        NULL::bigint AS photo_owner_id,
        NULL::bigint AS photo_study_id,
        NULL::text AS photo_file_usage,
        NULL::text AS photo_content_type,
        NULL::bigint AS photo_size_bytes,
        NULL::text AS photo_upload_status,
        NULL::timestamptz AS photo_expires_at,
        NULL::timestamptz AS photo_deleted_at,
        sj.stt_id::text AS stt_id,
        sj.member_id AS stt_member_id,
        sj.status::text AS stt_job_status,
        sj.retryable AS stt_retryable,
        sj.fail_code::text AS stt_fail_code,
        sj.requested_at AS stt_requested_at
    FROM target t
    JOIN stt_job sj
      ON sj.session_id = t.session_id
     AND sj.field_record_id IS NULL

    ORDER BY row_kind ASC, stable_id ASC
    """
)


class ReportInputRepositoryError(RuntimeError):
    """The requested report cannot be represented as one authoritative snapshot."""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


class ReportInputRepository:
    """Load report normalization input using four reads in one DB snapshot."""

    def __init__(self, engine: Engine) -> None:
        self._engine = engine

    def load_source(self, report_id: int) -> ReportNormalizationSource:
        if report_id < 1:
            raise ValueError("report_id must be a positive integer")

        with self._engine.connect().execution_options(
            isolation_level="REPEATABLE READ"
        ) as connection:
            with connection.begin():
                connection.exec_driver_sql("SET TRANSACTION READ ONLY")

                parameters = {"report_id": report_id}
                context = (
                    connection.execute(_CONTEXT_QUERY, parameters)
                    .mappings()
                    .one_or_none()
                )
                context = self._require_valid_context(context, report_id)

                participant_rows = (
                    connection.execute(_PARTICIPANTS_QUERY, parameters)
                    .mappings()
                    .all()
                )
                checklist_rows = (
                    connection.execute(_CHECKLIST_ITEMS_QUERY, parameters)
                    .mappings()
                    .all()
                )
                source_rows = (
                    connection.execute(_FIELD_RECORDS_AND_STT_QUERY, parameters)
                    .mappings()
                    .all()
                )
                field_record_rows = [
                    row
                    for row in source_rows
                    if row["row_kind"] == "FIELD_RECORD"
                ]

                return ReportNormalizationSource(
                    reportId=context["report_id"],
                    studyId=context["study_id"],
                    apartmentId=context["apartment_id"],
                    fieldSessionId=context["field_session_id"],
                    sessionStatus=context["session_status"],
                    sessionStartedAt=context["session_started_at"],
                    sessionEndedAt=context["session_ended_at"],
                    snapshotAt=context["snapshot_at"],
                    participants=[
                        self._map_participant(row) for row in participant_rows
                    ],
                    checklistItems=[
                        self._map_checklist_item(row) for row in checklist_rows
                    ],
                    authoritativeSourceIds=sorted(
                        {row["source_id"] for row in field_record_rows}
                    ),
                    fieldRecords=[
                        self._map_field_record(row, context["study_id"])
                        for row in field_record_rows
                    ],
                    incompleteSttJobs=[
                        self._map_incomplete_stt_job(row)
                        for row in source_rows
                        if row["row_kind"] == "STT_JOB"
                    ],
                )

    @staticmethod
    def _require_valid_context(
        context: Mapping[str, Any] | None,
        report_id: int,
    ) -> Mapping[str, Any]:
        if context is None:
            raise ReportInputRepositoryError(
                "REPORT_NOT_FOUND",
                f"report {report_id} was not found",
            )
        if context["field_session_id"] is None:
            raise ReportInputRepositoryError(
                "FIELD_SESSION_NOT_FOUND",
                "the report study has no field session",
            )
        if context["apartment_id"] != context["study_apartment_id"]:
            raise ReportInputRepositoryError(
                "REPORT_APARTMENT_MISMATCH",
                "report and study must reference the same apartment",
            )
        if (
            context["study_deleted_at"] is not None
            or context["study_canceled_at"] is not None
            or context["study_status"] == "CANCELED"
        ):
            raise ReportInputRepositoryError(
                "STUDY_UNAVAILABLE",
                "a deleted or canceled study cannot produce a report input",
            )
        return context

    @staticmethod
    def _map_participant(row: Mapping[str, Any]) -> ParticipantSource:
        return ParticipantSource(
            fieldParticipantId=row["field_participant_id"],
            memberId=row["member_id"],
            status=row["status"],
            startedAt=row["started_at"],
            endedAt=row["ended_at"],
        )

    @staticmethod
    def _map_checklist_item(row: Mapping[str, Any]) -> ChecklistItemSource:
        return ChecklistItemSource(
            checklistId=row["checklist_id"],
            checklistItemId=row["checklist_item_id"],
            memberId=row["member_id"],
            fallback=row["is_fallback"],
            category=row["category"],
            title=row["title"],
            subtitle=row["subtitle"],
            displayOrder=row["display_order"],
            completed=row["is_completed"],
            completedAt=row["completed_at"],
        )

    @staticmethod
    def _map_field_record(
        row: Mapping[str, Any],
        study_id: int,
    ) -> FieldRecordSource:
        photo_file = None
        if (
            row["source_type"] == "PHOTO"
            and row["photo_file_id"] is not None
            and row["photo_owner_id"] == row["author_id"]
            and row["photo_study_id"] == study_id
            and row["photo_file_usage"] == "FIELD_PHOTO"
        ):
            photo_file = PhotoFileSource(
                fileId=row["photo_file_id"],
                contentType=row["photo_content_type"],
                sizeBytes=row["photo_size_bytes"],
                uploadStatus=row["photo_upload_status"],
                expiresAt=row["photo_expires_at"],
                deletedAt=row["photo_deleted_at"],
            )

        return FieldRecordSource(
            sourceId=row["source_id"],
            sessionId=row["session_id"],
            checklistItemId=row["checklist_item_id"],
            authorId=row["author_id"],
            sourceType=row["source_type"],
            textContent=row["text_content"],
            sttStatus=row["stt_status"],
            photoFile=photo_file,
            deletedAt=row["deleted_at"],
            recordedAt=row["recorded_at"],
            updatedAt=row["updated_at"],
        )

    @staticmethod
    def _map_incomplete_stt_job(
        row: Mapping[str, Any],
    ) -> IncompleteSttJobSource:
        return IncompleteSttJobSource(
            sttId=row["stt_id"],
            memberId=row["stt_member_id"],
            checklistItemId=row["checklist_item_id"],
            status=row["stt_job_status"],
            retryable=row["stt_retryable"],
            failCode=row["stt_fail_code"],
            requestedAt=row["stt_requested_at"],
        )


def create_report_input_repository(
    settings: ReportDatabaseSettings,
) -> ReportInputRepository:
    """Create the psycopg-backed repository without exposing credentials."""

    url = URL.create(
        "postgresql+psycopg",
        username=settings.user,
        password=settings.password,
        host=settings.host,
        port=settings.port,
        database=settings.database,
    )
    engine = create_engine(
        url,
        pool_pre_ping=True,
        connect_args={
            "connect_timeout": settings.connect_timeout_seconds,
            "options": (
                "-c statement_timeout="
                f"{settings.statement_timeout_ms}"
            ),
        },
    )
    return ReportInputRepository(engine)
