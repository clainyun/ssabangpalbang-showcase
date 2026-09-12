"""Opt-in PostgreSQL verification for the AI-004 report snapshot loader."""

from __future__ import annotations

import os

import pytest
from sqlalchemy import text

from app.config import ReportDatabaseSettings
from app.repositories.report_input_repository import (
    _CHECKLIST_ITEMS_QUERY,
    _CONTEXT_QUERY,
    _FIELD_RECORDS_AND_STT_QUERY,
    _PARTICIPANTS_QUERY,
    create_report_input_repository,
)
from app.services.report_input_normalizer import normalize_report_input


pytestmark = pytest.mark.skipif(
    os.getenv("RUN_REPORT_INPUT_POSTGRES_E2E") != "1",
    reason="set RUN_REPORT_INPUT_POSTGRES_E2E=1 with local PostgreSQL",
)


def test_real_postgres_queries_match_current_flyway_schema() -> None:
    repository = create_report_input_repository(
        ReportDatabaseSettings.from_env()
    )
    statements = (
        _CONTEXT_QUERY,
        _PARTICIPANTS_QUERY,
        _CHECKLIST_ITEMS_QUERY,
        _FIELD_RECORDS_AND_STT_QUERY,
    )

    with repository._engine.connect().execution_options(
        isolation_level="REPEATABLE READ"
    ) as connection:
        with connection.begin():
            connection.exec_driver_sql("SET TRANSACTION READ ONLY")
            read_only = connection.exec_driver_sql(
                "SHOW transaction_read_only"
            ).scalar_one()
            for statement in statements:
                connection.execute(
                    text("EXPLAIN " + statement.text),
                    {"report_id": 0},
                ).all()

    assert read_only == "on"


def test_real_postgres_snapshot_contains_only_authoritative_source_ids() -> None:
    repository = create_report_input_repository(
        ReportDatabaseSettings.from_env()
    )
    with repository._engine.connect() as connection:
        report_id = connection.execute(
            text(
                """
                SELECT r.id
                FROM report r
                JOIN study s ON s.id = r.study_id
                JOIN field_session fs ON fs.study_id = r.study_id
                WHERE fs.status = 'ENDED'
                  AND fs.ended_at IS NOT NULL
                  AND s.deleted_at IS NULL
                  AND s.canceled_at IS NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM field_participant fp
                      WHERE fp.session_id = fs.id
                        AND (fp.status <> 'ENDED' OR fp.ended_at IS NULL)
                  )
                ORDER BY r.id ASC
                LIMIT 1
                """
            )
        ).scalar_one_or_none()

    if report_id is None:
        pytest.skip("no ended report fixture exists in local PostgreSQL")

    source = repository.load_source(report_id)
    normalized = normalize_report_input(source)

    authoritative_ids = {record.sourceId for record in source.fieldRecords}
    normalized_ids = {record.sourceId for record in normalized.sources}
    excluded_ids = {
        record.sourceId
        for record in normalized.excludedSources
        if record.sourceId is not None
    }
    assert normalized_ids <= authoritative_ids
    assert excluded_ids <= authoritative_ids
