"""Behavior tests for deterministic AI-004 source normalization."""

from __future__ import annotations

import copy
import json
from datetime import timedelta
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.schemas.report_input import (
    NormalizedReportInput,
    ReportNormalizationSource,
)
from app.services.report_input_normalizer import (
    ReportInputNormalizationError,
    normalize_report_input,
)


CONTRACT_ROOT = Path(__file__).resolve().parents[2] / "contracts" / "ai-004"
SOURCE_FIXTURE = CONTRACT_ROOT / "report_normalization_source.json"
EXPECTED_FIXTURE = CONTRACT_ROOT / "report_normalization_expected.json"


def _read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _normalize(payload: dict) -> NormalizedReportInput:
    return normalize_report_input(ReportNormalizationSource.model_validate(payload))


def test_normalization_matches_canonical_expected_fixture() -> None:
    source = ReportNormalizationSource.model_validate_json(
        SOURCE_FIXTURE.read_text(encoding="utf-8")
    )
    expected = NormalizedReportInput.model_validate_json(
        EXPECTED_FIXTURE.read_text(encoding="utf-8")
    )

    actual = normalize_report_input(source)

    assert actual.model_dump(mode="json") == expected.model_dump(mode="json")
    assert [item.sourceType.value for item in actual.sources] == [
        "TEXT",
        "PHOTO",
        "STT",
    ]
    assert actual.normalizationSummary.duplicateRecordCount == 1
    assert actual.excludedSources[0].participantRef == "P1"
    assert actual.excludedSources[0].recordedAt is not None
    stt_issues = [
        item
        for item in actual.qualityIssues
        if item.code.value in {"STT_NOT_DONE", "STT_RESULT_MISSING"}
    ]
    assert [item.participantRef for item in stt_issues] == ["P1", "P2"]
    assert all(item.observedAt is not None for item in stt_issues)
    assert [item.reason.value for item in actual.excludedSources] == [
        "DUPLICATED",
        "EMPTY_TEXT",
        "STT_NOT_DONE",
        "PHOTO_METADATA_MISSING",
    ]


def test_normalization_is_deterministic_when_snapshot_lists_are_reordered() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    expected = _normalize(copy.deepcopy(payload))
    payload["participants"].reverse()
    payload["checklistItems"].reverse()
    payload["fieldRecords"].reverse()
    payload["authoritativeSourceIds"].reverse()
    payload["incompleteSttJobs"].reverse()

    actual = _normalize(payload)

    assert actual.model_dump(mode="json") == expected.model_dump(mode="json")


def test_semantic_text_is_trimmed_line_normalized_and_unicode_nfc() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    text_record = next(
        record
        for record in payload["fieldRecords"]
        if record["sourceId"] == 201
    )
    text_record["textContent"] = "  Cafe\u0301\r\n둘째 줄\r셋째 줄  "
    payload["fieldRecords"] = [text_record]
    payload["authoritativeSourceIds"] = [text_record["sourceId"]]
    payload["incompleteSttJobs"] = []

    normalized = _normalize(payload)

    assert normalized.sources[0].semanticText == "Café\n둘째 줄\n셋째 줄"


def test_same_source_id_with_conflicting_payload_is_a_hard_error() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    duplicate_indexes = [
        index
        for index, record in enumerate(payload["fieldRecords"])
        if record["sourceId"] == 201
    ]
    payload["fieldRecords"][duplicate_indexes[1]]["textContent"] = "서로 다른 기록"

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        _normalize(payload)

    assert exc_info.value.code == "CONFLICTING_SOURCE_ID"


@pytest.mark.parametrize(
    ("mutation", "expected_code"),
    [
        ("payload_not_in_manifest", "SOURCE_NOT_IN_MANIFEST"),
        ("manifest_without_payload", "SOURCE_PAYLOAD_MISSING"),
    ],
)
def test_source_manifest_must_match_field_record_payloads(
    mutation: str,
    expected_code: str,
) -> None:
    payload = _read_json(SOURCE_FIXTURE)
    if mutation == "payload_not_in_manifest":
        unexpected = copy.deepcopy(payload["fieldRecords"][0])
        unexpected["sourceId"] = 999
        payload["fieldRecords"].append(unexpected)
    else:
        payload["authoritativeSourceIds"].append(999)

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        _normalize(payload)

    assert exc_info.value.code == expected_code


def test_normalizer_defensively_rejects_mutated_duplicate_manifest() -> None:
    source = ReportNormalizationSource.model_validate(
        _read_json(SOURCE_FIXTURE)
    )
    source.authoritativeSourceIds.append(source.authoritativeSourceIds[0])

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        normalize_report_input(source)

    assert exc_info.value.code == "DUPLICATE_SOURCE_MANIFEST"


@pytest.mark.parametrize(
    ("mutation", "expected_code"),
    [
        ("session_not_ended", "SESSION_NOT_ENDED"),
        ("participant_not_ended", "PARTICIPANT_NOT_ENDED"),
        ("participants_missing", "PARTICIPANT_MISSING"),
    ],
)
def test_non_terminal_or_incomplete_snapshot_is_rejected(
    mutation: str,
    expected_code: str,
) -> None:
    payload = _read_json(SOURCE_FIXTURE)
    if mutation == "session_not_ended":
        payload["sessionStatus"] = "IN_PROGRESS"
        payload["sessionEndedAt"] = None
    elif mutation == "participant_not_ended":
        payload["participants"][0]["status"] = "IN_PROGRESS"
        payload["participants"][0]["endedAt"] = None
    else:
        payload["participants"] = []

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        _normalize(payload)

    assert exc_info.value.code == expected_code


def test_normalizer_defensively_rejects_snapshot_before_session_end() -> None:
    source = ReportNormalizationSource.model_validate(
        _read_json(SOURCE_FIXTURE)
    )
    source.snapshotAt = source.sessionEndedAt - timedelta(seconds=1)

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        normalize_report_input(source)

    assert exc_info.value.code == "SNAPSHOT_TIME_INVALID"


@pytest.mark.parametrize(
    ("mutation", "expected_code"),
    [
        ("participant_id", "DUPLICATE_PARTICIPANT"),
        ("participant_member", "DUPLICATE_PARTICIPANT"),
        ("checklist_item", "DUPLICATE_CHECKLIST_ITEM"),
    ],
)
def test_ambiguous_participant_and_checklist_identity_is_rejected(
    mutation: str,
    expected_code: str,
) -> None:
    payload = _read_json(SOURCE_FIXTURE)
    if mutation == "participant_id":
        duplicate = copy.deepcopy(payload["participants"][0])
        duplicate["memberId"] = 99
        payload["participants"].append(duplicate)
    elif mutation == "participant_member":
        duplicate = copy.deepcopy(payload["participants"][0])
        duplicate["fieldParticipantId"] = 99
        payload["participants"].append(duplicate)
    else:
        payload["checklistItems"].append(
            copy.deepcopy(payload["checklistItems"][0])
        )

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        _normalize(payload)

    assert exc_info.value.code == expected_code


def test_duplicate_incomplete_stt_id_is_rejected() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload["incompleteSttJobs"].append(
        copy.deepcopy(payload["incompleteSttJobs"][0])
    )

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        _normalize(payload)

    assert exc_info.value.code == "DUPLICATE_STT_JOB"


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("recordedAt", "2026-07-20T13:59:59+09:00"),
        ("recordedAt", "2026-07-20T16:05:01+09:00"),
        ("updatedAt", "2026-07-20T13:59:59+09:00"),
        ("updatedAt", "2026-07-20T16:05:01+09:00"),
    ],
)
def test_field_record_timestamp_must_stay_inside_snapshot(
    field: str,
    value: str,
) -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload["fieldRecords"][0][field] = value

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        _normalize(payload)

    assert exc_info.value.code == "SOURCE_TIMESTAMP_INVALID"


def test_participant_timestamp_must_stay_inside_ended_session() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload["participants"][0]["endedAt"] = "2026-07-20T16:00:01+09:00"

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        _normalize(payload)

    assert exc_info.value.code == "PARTICIPANT_TIMESTAMP_INVALID"


def test_incomplete_stt_timestamp_must_stay_inside_snapshot() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload["incompleteSttJobs"][0]["requestedAt"] = (
        "2026-07-20T16:05:01+09:00"
    )

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        _normalize(payload)

    assert exc_info.value.code == "STT_TIMESTAMP_INVALID"


@pytest.mark.parametrize(
    ("field", "value", "expected_code"),
    [
        ("memberId", 999, "STT_OWNER_NOT_PARTICIPANT"),
        ("checklistItemId", 999, "STT_CHECKLIST_ITEM_MISSING"),
        ("checklistItemId", 503, "STT_CHECKLIST_OWNER_MISMATCH"),
    ],
)
def test_incomplete_stt_job_must_belong_to_participant_checklist(
    field: str,
    value: int,
    expected_code: str,
) -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload["incompleteSttJobs"][0][field] = value

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        _normalize(payload)

    assert exc_info.value.code == expected_code


def test_checklist_completion_timestamp_must_stay_inside_snapshot() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload["checklistItems"][0]["completedAt"] = (
        "2026-07-20T16:05:01+09:00"
    )

    with pytest.raises(ReportInputNormalizationError) as exc_info:
        _normalize(payload)

    assert exc_info.value.code == "CHECKLIST_TIMESTAMP_INVALID"


def test_completed_stt_preserves_text_longer_than_2000_chars() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    record = copy.deepcopy(
        next(item for item in payload["fieldRecords"] if item["sourceId"] == 228)
    )
    record["textContent"] = "가" * 2001
    payload["fieldRecords"] = [record]
    payload["authoritativeSourceIds"] = [record["sourceId"]]
    payload["incompleteSttJobs"] = []

    normalized = _normalize(payload)

    assert len(normalized.sources[0].semanticText) == 2001


@pytest.mark.parametrize(
    ("source_id", "mutation", "expected_reason"),
    [
        (201, "session_mismatch", "SESSION_MISMATCH"),
        (201, "deleted", "DELETED"),
        (201, "checklist_missing", "CHECKLIST_ITEM_MISSING"),
        (201, "author_missing", "AUTHOR_NOT_PARTICIPANT"),
        (201, "author_mismatch", "AUTHOR_CHECKLIST_MISMATCH"),
        (214, "photo_unavailable", "PHOTO_UNAVAILABLE"),
    ],
)
def test_invalid_records_are_excluded_with_specific_reason(
    source_id: int,
    mutation: str,
    expected_reason: str,
) -> None:
    payload = _read_json(SOURCE_FIXTURE)
    record = copy.deepcopy(
        next(item for item in payload["fieldRecords"] if item["sourceId"] == source_id)
    )
    payload["fieldRecords"] = [record]
    payload["authoritativeSourceIds"] = [record["sourceId"]]
    payload["incompleteSttJobs"] = []
    if mutation == "session_mismatch":
        record["sessionId"] = 901
    elif mutation == "deleted":
        record["deletedAt"] = "2026-07-20T15:00:00+09:00"
    elif mutation == "checklist_missing":
        record["checklistItemId"] = 999
    elif mutation == "author_missing":
        record["authorId"] = 999
    elif mutation == "author_mismatch":
        record["authorId"] = 8
    else:
        record["photoFile"]["uploadStatus"] = "FAILED"

    normalized = _normalize(payload)

    assert normalized.sources == []
    assert normalized.excludedSources[0].reason.value == expected_reason
    assert any(issue.code.value == "NO_USABLE_SOURCES" for issue in normalized.qualityIssues)


def test_completed_undeleted_photo_is_available_even_after_expires_at() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    record = copy.deepcopy(
        next(
            item
            for item in payload["fieldRecords"]
            if item["sourceType"] == "PHOTO" and item["photoFile"] is not None
        )
    )
    record["photoFile"]["expiresAt"] = "2026-07-20T15:00:00+09:00"
    payload["fieldRecords"] = [record]
    payload["authoritativeSourceIds"] = [record["sourceId"]]
    payload["incompleteSttJobs"] = []

    normalized = _normalize(payload)

    assert len(normalized.sources) == 1
    assert normalized.sources[0].sourceType.value == "PHOTO"


def test_empty_snapshot_marks_no_usable_sources_without_fabricating_data() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload["fieldRecords"] = []
    payload["authoritativeSourceIds"] = []
    payload["incompleteSttJobs"] = []

    normalized = _normalize(payload)

    assert normalized.sources == []
    assert normalized.excludedSources == []
    assert normalized.normalizationSummary.inputRecordCount == 0
    assert normalized.normalizationSummary.includedSourceCount == 0
    assert normalized.normalizationSummary.excludedRecordCount == 0
    assert any(issue.code.value == "NO_USABLE_SOURCES" for issue in normalized.qualityIssues)


def test_no_usable_sources_quality_issue_must_not_be_duplicated() -> None:
    payload = _read_json(SOURCE_FIXTURE)
    payload["fieldRecords"] = []
    payload["authoritativeSourceIds"] = []
    payload["incompleteSttJobs"] = []
    normalized = _normalize(payload).model_dump(mode="json")
    no_source_issue = next(
        item
        for item in normalized["qualityIssues"]
        if item["code"] == "NO_USABLE_SOURCES"
    )
    normalized["qualityIssues"].append(copy.deepcopy(no_source_issue))

    with pytest.raises(ValidationError):
        NormalizedReportInput.model_validate(normalized)
