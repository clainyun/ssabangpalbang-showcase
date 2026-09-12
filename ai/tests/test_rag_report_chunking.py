"""Chunking, heading and reindex-key tests for report documents (C-1~C-7)."""

from datetime import datetime, timedelta, timezone

from app.rag.documents import SourceType
from app.rag.report_documents import (
    CHUNK_CHAR_LIMIT,
    PARAGRAPH_SEPARATOR,
    ReportRow,
    build_report_documents,
    chunk_texts,
    report_reindex_key,
    report_source_at,
)


APARTMENT_NAME = "래미안 옥수 리버젠"
COMPLETED_AT = datetime(2026, 7, 25, 7, 0, tzinfo=timezone.utc)
UPDATED_AT = COMPLETED_AT + timedelta(hours=3)


def _sentence(marker: str) -> str:
    """A sentence of exactly 100 characters ending in a period."""
    return marker * 99 + "."


def _long_body(sentence_count: int) -> str:
    return " ".join(_sentence("가") for _ in range(sentence_count))


def _row(result_json: object, report_id: int = 48) -> ReportRow:
    return ReportRow(
        id=report_id,
        apartment_id=15,
        result_json=result_json,
        completed_at=COMPLETED_AT,
        updated_at=UPDATED_AT,
        apartment_name=APARTMENT_NAME,
    )


def test_c1_short_body_makes_one_chunk() -> None:
    chunks = chunk_texts(["나" * 300])

    assert len(chunks) == 1
    assert chunks[0] == "나" * 300


def test_c2_long_body_splits_into_bounded_chunks() -> None:
    body = _long_body(25)
    assert len(body) == 2_524

    chunks = chunk_texts([body])

    assert len(chunks) == 3
    assert all(len(chunk) <= CHUNK_CHAR_LIMIT for chunk in chunks)


def test_c3_no_chunk_carries_the_apartment_heading() -> None:
    documents = build_report_documents(_row({"summary": _long_body(25)}))

    assert len(documents) > 1
    for document in documents:
        assert not document.content.startswith("[")
        assert f"[{APARTMENT_NAME} 임장 리포트]" not in document.content


def test_c4_reindex_keys_are_numbered_from_zero() -> None:
    documents = build_report_documents(_row({"summary": _long_body(25)}))

    keys = [document.reindex_key for document in documents]

    assert keys == ["REPORT:48:0", "REPORT:48:1", "REPORT:48:2"]


def test_c5_same_input_produces_the_same_keys() -> None:
    result_json = {"summary": _long_body(25)}

    first = build_report_documents(_row(result_json))
    second = build_report_documents(_row(result_json))

    assert [document.reindex_key for document in first] == [
        document.reindex_key for document in second
    ]


def test_c6_bigint_report_id_key_fits_column() -> None:
    key = report_reindex_key(9_223_372_036_854_775_807, 9_999)

    assert len(key) <= 200


def test_c7_paragraph_boundary_is_the_cut_point() -> None:
    first = "다" * 600
    second = "라" * 600

    chunks = chunk_texts([first + PARAGRAPH_SEPARATOR + second])

    assert chunks == [first, second]


def test_sentence_longer_than_limit_is_hard_split() -> None:
    chunks = chunk_texts(["마" * 2_500])

    assert all(len(chunk) <= CHUNK_CHAR_LIMIT for chunk in chunks)
    assert "".join(chunks) == "마" * 2_500


def test_documents_carry_report_source_type_and_id() -> None:
    documents = build_report_documents(_row({"summary": _long_body(3)}))

    assert len(documents) == 1
    assert documents[0].source_type is SourceType.REPORT
    assert documents[0].source_id == 48
    assert documents[0].apartment_id == 15


def test_source_at_prefers_completed_at() -> None:
    assert report_source_at(_row({})) == COMPLETED_AT


def test_source_at_falls_back_to_updated_at() -> None:
    row = ReportRow(
        id=48,
        apartment_id=15,
        result_json={},
        completed_at=None,
        updated_at=UPDATED_AT,
        apartment_name=APARTMENT_NAME,
    )

    assert report_source_at(row) == UPDATED_AT


def test_unreadable_report_makes_no_documents() -> None:
    assert build_report_documents(_row(None)) == []
    assert build_report_documents(_row({"status": "DONE"})) == []
