"""Strict contract tests for canonical AI-004 JSON fixtures."""

from __future__ import annotations

import copy
import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.schemas.report_input import (
    NormalizedReportInput,
    ReportNormalizationSource,
    normalized_report_input_json_schema,
)


CONTRACT_ROOT = Path(__file__).resolve().parents[2] / "contracts" / "ai-004"
SOURCE_FIXTURE = CONTRACT_ROOT / "report_normalization_source.json"
EXPECTED_FIXTURE = CONTRACT_ROOT / "report_normalization_expected.json"


def _read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def test_canonical_fixtures_are_strictly_valid() -> None:
    source = ReportNormalizationSource.model_validate_json(
        SOURCE_FIXTURE.read_text(encoding="utf-8")
    )
    expected = NormalizedReportInput.model_validate_json(
        EXPECTED_FIXTURE.read_text(encoding="utf-8")
    )

    assert source.schemaVersion == 1
    assert source.sessionStatus == "ENDED"
    assert {record.sourceType.value for record in source.fieldRecords} == {
        "TEXT",
        "PHOTO",
        "STT",
    }
    assert [item.sourceId for item in expected.sources] == [201, 214, 228]
    assert [item.participantRef for item in expected.participants] == ["P1", "P2"]


@pytest.mark.parametrize(
    ("field", "invalid_value"),
    [
        ("schemaVersion", 2),
        ("reportId", 0),
        ("sessionStartedAt", "2026-07-20T14:00:00"),
    ],
)
def test_source_contract_rejects_invalid_version_id_and_naive_time(
    field: str,
    invalid_value: object,
) -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload[field] = invalid_value

    with pytest.raises(ValidationError):
        ReportNormalizationSource.model_validate(payload)


def test_source_contract_rejects_unknown_fields() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload["unexpected"] = True
    payload["fieldRecords"][0]["storageObjectKey"] = "private/audio.m4a"

    with pytest.raises(ValidationError):
        ReportNormalizationSource.model_validate(payload)


def test_source_contract_rejects_duplicate_authoritative_source_ids() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload["authoritativeSourceIds"].append(
        payload["authoritativeSourceIds"][0]
    )

    with pytest.raises(ValidationError):
        ReportNormalizationSource.model_validate(payload)


@pytest.mark.parametrize("field", ["originalName", "s3Key"])
def test_photo_source_rejects_private_or_uncontracted_metadata(field: str) -> None:
    payload = _read_json(SOURCE_FIXTURE)
    photo_record = next(
        record
        for record in payload["fieldRecords"]
        if record["sourceType"] == "PHOTO" and record["photoFile"] is not None
    )
    photo_record["photoFile"][field] = "must-not-cross-the-contract-boundary"

    with pytest.raises(ValidationError):
        ReportNormalizationSource.model_validate(payload)


@pytest.mark.parametrize("field", ["originalName", "s3Key"])
def test_normalized_photo_metadata_rejects_extra_fields(field: str) -> None:
    payload = _read_json(EXPECTED_FIXTURE)
    photo_source = next(
        source for source in payload["sources"] if source["sourceType"] == "PHOTO"
    )
    photo_source["photoMetadata"][field] = (
        "must-not-cross-the-normalized-contract-boundary"
    )

    with pytest.raises(ValidationError):
        NormalizedReportInput.model_validate(payload)


def test_photo_source_rejects_unknown_upload_status() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    photo_record = next(
        record
        for record in payload["fieldRecords"]
        if record["sourceType"] == "PHOTO" and record["photoFile"] is not None
    )
    photo_record["photoFile"]["uploadStatus"] = "READY"

    with pytest.raises(ValidationError):
        ReportNormalizationSource.model_validate(payload)


@pytest.mark.parametrize(
    ("source_type", "field", "value"),
    [
        ("TEXT", "sttStatus", "DONE"),
        (
            "TEXT",
            "photoFile",
            {
                "fileId": 999,
                "contentType": "image/jpeg",
                "sizeBytes": 1,
                "uploadStatus": "COMPLETED",
                "expiresAt": None,
                "deletedAt": None,
            },
        ),
        (
            "STT",
            "photoFile",
            {
                "fileId": 999,
                "contentType": "image/jpeg",
                "sizeBytes": 1,
                "uploadStatus": "COMPLETED",
                "expiresAt": None,
                "deletedAt": None,
            },
        ),
        ("PHOTO", "textContent", "photo must not carry semantic text"),
        ("PHOTO", "sttStatus", "DONE"),
    ],
)
def test_field_record_payload_must_match_source_type(
    source_type: str,
    field: str,
    value: object,
) -> None:
    payload = _read_json(SOURCE_FIXTURE)
    record = next(
        item for item in payload["fieldRecords"] if item["sourceType"] == source_type
    )
    record[field] = value

    with pytest.raises(ValidationError):
        ReportNormalizationSource.model_validate(payload)


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("sessionEndedAt", "2026-07-20T13:59:59+09:00"),
        ("sessionEndedAt", "2026-07-20T16:05:01+09:00"),
    ],
)
def test_source_contract_rejects_invalid_session_time_order(
    field: str,
    value: str,
) -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload[field] = value

    with pytest.raises(ValidationError):
        ReportNormalizationSource.model_validate(payload)


def test_participant_contract_rejects_ended_at_before_started_at() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload["participants"][0]["endedAt"] = "2026-07-20T13:00:00+09:00"

    with pytest.raises(ValidationError):
        ReportNormalizationSource.model_validate(payload)


def test_text_record_accepts_2000_and_rejects_2001_chars() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    record = next(
        item for item in payload["fieldRecords"] if item["sourceType"] == "TEXT"
    )
    record["textContent"] = "가" * 2000
    ReportNormalizationSource.model_validate(payload)

    record["textContent"] = "가" * 2001
    with pytest.raises(ValidationError):
        ReportNormalizationSource.model_validate(payload)


def test_stt_record_accepts_text_longer_than_2000_chars() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    record = next(
        item for item in payload["fieldRecords"] if item["sourceType"] == "STT"
    )
    record["textContent"] = "가" * 2001

    source = ReportNormalizationSource.model_validate(payload)

    validated = next(
        item for item in source.fieldRecords if item.sourceType.value == "STT"
    )
    assert len(validated.textContent) == 2001


def test_normalized_source_discriminator_enforces_type_specific_fields() -> None:
    payload = _read_json(EXPECTED_FIXTURE)
    text_source = payload["sources"][0]
    text_source.pop("semanticText")
    text_source["photoMetadata"] = {
        "fileId": 301,
        "contentType": "image/jpeg",
        "sizeBytes": 10,
    }

    with pytest.raises(ValidationError):
        NormalizedReportInput.model_validate(payload)


@pytest.mark.parametrize(
    "mutation",
    [
        "unknown_participant",
        "checklist_owner_mismatch",
        "excluded_unknown_participant",
        "participant_after_session",
        "source_after_snapshot",
        "duplicate_source",
        "wrong_summary_count",
    ],
)
def test_normalized_contract_rejects_broken_references_and_counts(
    mutation: str,
) -> None:
    payload = _read_json(EXPECTED_FIXTURE)
    if mutation == "unknown_participant":
        payload["sources"][0]["participantRef"] = "P99"
    elif mutation == "checklist_owner_mismatch":
        payload["sources"][0]["participantRef"] = "P2"
    elif mutation == "excluded_unknown_participant":
        payload["excludedSources"][0]["participantRef"] = "P99"
    elif mutation == "participant_after_session":
        payload["participants"][0]["endedAt"] = (
            "2026-07-20T16:00:01+09:00"
        )
    elif mutation == "source_after_snapshot":
        payload["sources"][0]["recordedAt"] = (
            "2026-07-20T16:05:01+09:00"
        )
    elif mutation == "duplicate_source":
        payload["sources"].append(copy.deepcopy(payload["sources"][0]))
        payload["normalizationSummary"]["includedSourceCount"] += 1
        payload["normalizationSummary"]["inputRecordCount"] += 1
    else:
        payload["normalizationSummary"]["participantCount"] = 99

    with pytest.raises(ValidationError):
        NormalizedReportInput.model_validate(payload)


@pytest.mark.parametrize(
    "mutation",
    [
        "duplicate_count",
        "incomplete_stt_count",
        "no_usable_sources_mismatch",
    ],
)
def test_normalized_contract_rejects_inconsistent_diagnostic_counts(
    mutation: str,
) -> None:
    payload = _read_json(EXPECTED_FIXTURE)
    if mutation == "duplicate_count":
        payload["normalizationSummary"]["duplicateRecordCount"] = 0
    elif mutation == "incomplete_stt_count":
        payload["normalizationSummary"]["incompleteSttJobCount"] = 0
    else:
        payload["qualityIssues"].append({"code": "NO_USABLE_SOURCES"})

    with pytest.raises(ValidationError):
        NormalizedReportInput.model_validate(payload)


def test_normalized_contract_requires_at_least_one_participant() -> None:
    payload = _read_json(EXPECTED_FIXTURE)
    payload["participants"] = []
    payload["normalizationSummary"]["participantCount"] = 0

    with pytest.raises(ValidationError):
        NormalizedReportInput.model_validate(payload)


def test_normalized_contract_is_privacy_safe() -> None:
    normalized = NormalizedReportInput.model_validate_json(
        EXPECTED_FIXTURE.read_text(encoding="utf-8")
    )
    serialized = json.dumps(normalized.model_dump(mode="json"), ensure_ascii=False)
    schema_serialized = json.dumps(
        normalized_report_input_json_schema(),
        ensure_ascii=False,
    )

    for forbidden in (
        "memberId",
        "authorId",
        "fieldParticipantId",
        "objectKey",
        "s3Key",
        "audioFileId",
        "originalName",
    ):
        assert forbidden not in serialized
        assert forbidden not in schema_serialized


def test_generated_json_schema_is_versioned_and_discriminated() -> None:
    schema = normalized_report_input_json_schema()

    assert schema["properties"]["schemaVersion"]["const"] == 1
    sources = schema["properties"]["sources"]["items"]
    assert sources["discriminator"]["propertyName"] == "sourceType"
    assert set(sources["discriminator"]["mapping"]) == {"TEXT", "PHOTO", "STT"}
