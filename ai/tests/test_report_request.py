"""Kafka REPORT_REQUESTED payload validation tests (AI-007 Gate 3A)."""

from __future__ import annotations

import logging

import pytest
from pydantic import ValidationError

from app.schemas.report_request import ReportRequestedPayload


def test_payload_accepts_valid_event() -> None:
    payload = ReportRequestedPayload.model_validate(
        {
            "studyId": 42,
            "sessionId": 7,
            "apartmentId": 100,
            "occurredAt": "2026-08-01T09:15:30.123Z",
        }
    )
    assert payload.studyId == 42
    assert payload.sessionId == 7
    assert payload.apartmentId == 100
    assert payload.occurredAt.tzinfo is not None


@pytest.mark.parametrize(
    "body",
    [
        {
            "sessionId": 7,
            "apartmentId": 100,
            "occurredAt": "2026-08-01T09:15:30Z",
        },
        {
            "studyId": 42,
            "apartmentId": 100,
            "occurredAt": "2026-08-01T09:15:30Z",
        },
        {
            "studyId": 42,
            "sessionId": 7,
            "occurredAt": "2026-08-01T09:15:30Z",
        },
        {
            "studyId": 42,
            "sessionId": 7,
            "apartmentId": 100,
        },
    ],
)
def test_payload_rejects_missing_fields(body: dict) -> None:
    with pytest.raises(ValidationError):
        ReportRequestedPayload.model_validate(body)


def test_payload_rejects_non_positive_ids() -> None:
    with pytest.raises(ValidationError):
        ReportRequestedPayload.model_validate(
            {
                "studyId": 0,
                "sessionId": 7,
                "apartmentId": 100,
                "occurredAt": "2026-08-01T09:15:30Z",
            }
        )


def test_payload_rejects_wrong_types() -> None:
    with pytest.raises(ValidationError):
        ReportRequestedPayload.model_validate(
            {
                "studyId": "42",
                "sessionId": 7,
                "apartmentId": 100,
                "occurredAt": "2026-08-01T09:15:30Z",
            }
        )


def test_payload_rejects_extra_fields() -> None:
    with pytest.raises(ValidationError):
        ReportRequestedPayload.model_validate(
            {
                "studyId": 42,
                "sessionId": 7,
                "apartmentId": 100,
                "occurredAt": "2026-08-01T09:15:30Z",
                "eventId": "evt-1",
            }
        )


def test_payload_rejects_naive_occurred_at() -> None:
    with pytest.raises(ValidationError):
        ReportRequestedPayload.model_validate(
            {
                "studyId": 42,
                "sessionId": 7,
                "apartmentId": 100,
                "occurredAt": "2026-08-01T09:15:30",
            }
        )


def test_payload_schema_rejects_without_custom_raw_dump(
    caplog: pytest.LogCaptureFixture,
) -> None:
    """Schema-level ValidationError may name fields; Handler must not log raw JSON.

    Raw-payload non-exposure is asserted in test_report_message_handler.
    """

    with caplog.at_level(logging.DEBUG):
        with pytest.raises(ValidationError):
            ReportRequestedPayload.model_validate(
                {
                    "studyId": 42,
                    "sessionId": 7,
                    "apartmentId": 100,
                    "occurredAt": "2026-08-01T09:15:30",
                }
            )
    assert not any("2026-08-01T09:15:30" in message for message in caplog.messages)
