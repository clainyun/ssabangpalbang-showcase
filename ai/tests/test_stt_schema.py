"""Contract tests for AI-003 Kafka JSON fixtures."""

from pathlib import Path

import pytest
from pydantic import ValidationError

from app.schemas.stt import (
    STT_RESULT_ADAPTER,
    SttRequest,
    SttRequestDlq,
)


CONTRACT_ROOT = Path(__file__).resolve().parents[2] / "contracts" / "ai-003"


def test_shared_contract_fixtures_are_strictly_valid() -> None:
    request = SttRequest.model_validate_json(
        (CONTRACT_ROOT / "stt_request.json").read_text(encoding="utf-8")
    )
    processing = STT_RESULT_ADAPTER.validate_json(
        (CONTRACT_ROOT / "stt_processing.json").read_text(encoding="utf-8")
    )
    done = STT_RESULT_ADAPTER.validate_json(
        (CONTRACT_ROOT / "stt_done.json").read_text(encoding="utf-8")
    )
    failed = STT_RESULT_ADAPTER.validate_json(
        (CONTRACT_ROOT / "stt_failed.json").read_text(encoding="utf-8")
    )
    dlq = SttRequestDlq.model_validate_json(
        (CONTRACT_ROOT / "stt_request_dlq.json").read_text(encoding="utf-8")
    )

    assert request.language == "ko-KR"
    assert processing.status == "PROCESSING"
    assert done.status == "DONE"
    assert failed.status == "FAILED"
    assert dlq.failCode == "INVALID_MESSAGE"


def test_request_rejects_wrong_version_extra_fields_and_invalid_attempt() -> None:
    payload = {
        "schemaVersion": 2,
        "sttId": "stt-1",
        "attemptNo": 0,
        "audioFileId": 90,
        "objectKey": "private/audio.m4a",
        "contentType": "audio/m4a",
        "language": "ko-KR",
        "unexpected": True,
    }
    with pytest.raises(ValidationError):
        SttRequest.model_validate(payload)


def test_request_rejects_stt_id_longer_than_database_contract() -> None:
    payload = {
        "schemaVersion": 1,
        "sttId": "s" * 51,
        "attemptNo": 1,
        "audioFileId": 90,
        "objectKey": "private/audio.m4a",
        "contentType": "audio/m4a",
        "language": "ko-KR",
    }

    with pytest.raises(ValidationError):
        SttRequest.model_validate(payload)


def test_result_status_requires_exact_status_specific_fields() -> None:
    with pytest.raises(ValidationError):
        STT_RESULT_ADAPTER.validate_python(
            {
                "schemaVersion": 1,
                "sttId": "stt-1",
                "attemptNo": 1,
                "status": "DONE",
                "completedAt": "2026-07-30T12:30:00Z",
            }
        )

    with pytest.raises(ValidationError):
        STT_RESULT_ADAPTER.validate_python(
            {
                "schemaVersion": 1,
                "sttId": "stt-1",
                "attemptNo": 1,
                "status": "PROCESSING",
                "startedAt": "2026-07-30T12:30:00",
            }
        )
