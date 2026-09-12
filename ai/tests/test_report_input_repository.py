"""Unit tests for the AI-004 read-only PostgreSQL adapter."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

import pytest

from app.config import ReportDatabaseSettings
from app.repositories import report_input_repository as repository_module
from app.repositories.report_input_repository import (
    ReportInputRepository,
    ReportInputRepositoryError,
    create_report_input_repository,
)


NOW = datetime(2026, 7, 31, 1, 0, tzinfo=timezone.utc)


class _FakeResult:
    def __init__(self, *, one: dict[str, Any] | None = None, many=None) -> None:
        self._one = one
        self._many = [] if many is None else many

    def mappings(self) -> "_FakeResult":
        return self

    def one_or_none(self) -> dict[str, Any] | None:
        return self._one

    def all(self) -> list[dict[str, Any]]:
        return self._many


class _ContextManager:
    def __init__(self, value) -> None:
        self._value = value

    def __enter__(self):
        return self._value

    def __exit__(self, exc_type, exc, traceback) -> None:
        return None


class _FakeConnection:
    def __init__(self, results: list[_FakeResult]) -> None:
        self._results = iter(results)
        self.isolation_level = None
        self.driver_sql: list[str] = []
        self.parameters: list[dict[str, int]] = []

    def execution_options(self, *, isolation_level: str):
        self.isolation_level = isolation_level
        return self

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        return None

    def begin(self) -> _ContextManager:
        return _ContextManager(self)

    def exec_driver_sql(self, statement: str) -> None:
        self.driver_sql.append(statement)

    def execute(self, statement, parameters: dict[str, int]) -> _FakeResult:
        self.parameters.append(parameters)
        return next(self._results)


class _FakeEngine:
    def __init__(self, connection: _FakeConnection) -> None:
        self.connection = connection

    def connect(self) -> _FakeConnection:
        return self.connection


def _context() -> dict[str, Any]:
    return {
        "report_id": 48,
        "study_id": 7,
        "apartment_id": 15,
        "study_apartment_id": 15,
        "study_status": "IN_PROGRESS",
        "study_deleted_at": None,
        "study_canceled_at": None,
        "field_session_id": 31,
        "session_status": "ENDED",
        "session_started_at": NOW,
        "session_ended_at": NOW,
        "snapshot_at": NOW,
    }


def _field_record_row() -> dict[str, Any]:
    return {
        "row_kind": "FIELD_RECORD",
        "stable_id": 201,
        "source_id": 201,
        "session_id": 31,
        "checklist_item_id": 501,
        "author_id": 9,
        "source_type": "TEXT",
        "text_content": "현장 메모",
        "stt_status": None,
        "deleted_at": None,
        "recorded_at": NOW,
        "updated_at": NOW,
        "photo_file_id": None,
        "photo_owner_id": None,
        "photo_study_id": None,
        "photo_file_usage": None,
        "photo_content_type": None,
        "photo_size_bytes": None,
        "photo_upload_status": None,
        "photo_expires_at": None,
        "photo_deleted_at": None,
        "stt_id": None,
        "stt_member_id": None,
        "stt_job_status": None,
        "stt_retryable": None,
        "stt_fail_code": None,
        "stt_requested_at": None,
    }


def _stt_job_row() -> dict[str, Any]:
    row = _field_record_row()
    row.update(
        {
            "row_kind": "STT_JOB",
            "stable_id": 301,
            "source_id": None,
            "author_id": None,
            "source_type": "STT",
            "text_content": None,
            "recorded_at": None,
            "updated_at": None,
            "stt_id": "stt-301",
            "stt_member_id": 9,
            "stt_job_status": "PROCESSING",
            "stt_retryable": False,
            "stt_fail_code": None,
            "stt_requested_at": NOW,
        }
    )
    return row


def test_load_source_uses_one_read_only_repeatable_read_snapshot() -> None:
    connection = _FakeConnection(
        [
            _FakeResult(one=_context()),
            _FakeResult(
                many=[
                    {
                        "field_participant_id": 81,
                        "member_id": 9,
                        "status": "ENDED",
                        "started_at": NOW,
                        "ended_at": NOW,
                    }
                ]
            ),
            _FakeResult(
                many=[
                    {
                        "checklist_id": 91,
                        "checklist_item_id": 501,
                        "member_id": 9,
                        "is_fallback": False,
                        "category": "교통",
                        "title": "역 접근성",
                        "subtitle": None,
                        "display_order": 1,
                        "is_completed": True,
                        "completed_at": NOW,
                    }
                ]
            ),
            _FakeResult(many=[_field_record_row(), _stt_job_row()]),
        ]
    )

    source = ReportInputRepository(_FakeEngine(connection)).load_source(48)

    assert source.reportId == 48
    assert source.authoritativeSourceIds == [201]
    assert source.fieldRecords[0].sourceId == 201
    assert source.incompleteSttJobs[0].sttId == "stt-301"
    assert connection.isolation_level == "REPEATABLE READ"
    assert connection.driver_sql == ["SET TRANSACTION READ ONLY"]
    assert connection.parameters == [{"report_id": 48}] * 4


@pytest.mark.parametrize(
    ("mutation", "expected_code"),
    [
        ("missing_report", "REPORT_NOT_FOUND"),
        ("missing_session", "FIELD_SESSION_NOT_FOUND"),
        ("apartment_mismatch", "REPORT_APARTMENT_MISMATCH"),
        ("deleted", "STUDY_UNAVAILABLE"),
        ("canceled_at", "STUDY_UNAVAILABLE"),
        ("canceled_status", "STUDY_UNAVAILABLE"),
    ],
)
def test_invalid_context_is_rejected_without_guessing(
    mutation: str,
    expected_code: str,
) -> None:
    context = None if mutation == "missing_report" else _context()
    if mutation == "missing_session":
        context["field_session_id"] = None
    elif mutation == "apartment_mismatch":
        context["study_apartment_id"] = 999
    elif mutation == "deleted":
        context["study_deleted_at"] = NOW
    elif mutation == "canceled_at":
        context["study_canceled_at"] = NOW
    elif mutation == "canceled_status":
        context["study_status"] = "CANCELED"

    with pytest.raises(ReportInputRepositoryError) as error:
        ReportInputRepository._require_valid_context(context, 48)

    assert error.value.code == expected_code


def test_photo_metadata_is_mapped_only_for_matching_field_photo_owner() -> None:
    row = _field_record_row()
    row.update(
        {
            "source_type": "PHOTO",
            "text_content": None,
            "photo_file_id": 301,
            "photo_owner_id": 9,
            "photo_study_id": 7,
            "photo_file_usage": "FIELD_PHOTO",
            "photo_content_type": "image/jpeg",
            "photo_size_bytes": 1024,
            "photo_upload_status": "COMPLETED",
        }
    )

    mapped = ReportInputRepository._map_field_record(row, study_id=7)
    row["photo_owner_id"] = 999
    owner_mismatch = ReportInputRepository._map_field_record(row, study_id=7)

    assert mapped.photoFile.fileId == 301
    assert mapped.photoFile.contentType == "image/jpeg"
    assert owner_mismatch.photoFile is None


def test_source_query_does_not_read_storage_paths_or_original_names() -> None:
    query = str(repository_module._FIELD_RECORDS_AND_STT_QUERY).lower()

    assert "s3_key" not in query
    assert "original_name" not in query
    assert "fail_reason" not in query
    assert "audio_file_id" not in query


def test_factory_builds_psycopg_url_without_plaintext_password(
    monkeypatch,
) -> None:
    captured: dict[str, Any] = {}
    fake_engine = object()

    def fake_create_engine(url, **kwargs):
        captured["url"] = url
        captured["kwargs"] = kwargs
        return fake_engine

    monkeypatch.setattr(repository_module, "create_engine", fake_create_engine)
    settings = ReportDatabaseSettings(
        host="postgres",
        port=5432,
        database="ssabangpalbang",
        user="report_reader",
        password="local-test-password",
    )

    repository = create_report_input_repository(settings)

    assert repository._engine is fake_engine
    assert "local-test-password" not in str(captured["url"])
    assert captured["url"].drivername == "postgresql+psycopg"
    assert captured["kwargs"]["connect_args"]["connect_timeout"] == 5
    assert "statement_timeout=5000" in (
        captured["kwargs"]["connect_args"]["options"]
    )
