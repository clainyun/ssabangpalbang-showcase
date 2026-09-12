"""Vector search SQL and truncation tests (Q-1~Q-4)."""

from datetime import datetime, timezone

import pytest

from app.rag.search import (
    REPORT_DOCUMENTS_EXIST,
    SEARCH_REPORT_DOCUMENTS,
    SELECT_APARTMENT_PROFILE,
    ApartmentProfile,
    ReportChunkHit,
    fetch_apartment_profile,
    report_documents_exist,
    search_report_documents,
    truncate_to_char_limit,
)


SOURCE_AT = datetime(2026, 7, 25, 7, 0, tzinfo=timezone.utc)


class FakeCursor:
    def __init__(self, rows: list[tuple]) -> None:
        self.rows = rows
        self.executed: list[tuple[str, dict]] = []

    def __enter__(self) -> "FakeCursor":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def execute(self, sql: str, params: dict) -> None:
        self.executed.append((sql, params))

    def fetchall(self) -> list[tuple]:
        return self.rows

    def fetchone(self) -> tuple | None:
        return self.rows[0] if self.rows else None


class FakeConnection:
    def __init__(self, rows: list[tuple]) -> None:
        self.cursor_obj = FakeCursor(rows)

    def cursor(self) -> FakeCursor:
        return self.cursor_obj


def test_q1_sql_restricts_to_report_documents() -> None:
    assert "d.source_type = 'REPORT'" in SEARCH_REPORT_DOCUMENTS


def test_q2_sql_excludes_null_embeddings() -> None:
    assert "d.embedding IS NOT NULL" in SEARCH_REPORT_DOCUMENTS


def test_q2_sql_rechecks_live_report_visibility() -> None:
    assert "JOIN report r" in SEARCH_REPORT_DOCUMENTS
    assert "r.id = d.source_id" in SEARCH_REPORT_DOCUMENTS
    assert "r.apartment_id = d.apartment_id" in SEARCH_REPORT_DOCUMENTS
    assert "JOIN study s ON s.id = r.study_id" in SEARCH_REPORT_DOCUMENTS
    assert "r.status = 'DONE'" in SEARCH_REPORT_DOCUMENTS
    assert "s.deleted_at IS NULL" in SEARCH_REPORT_DOCUMENTS
    assert "s.status <> 'CANCELED'" in SEARCH_REPORT_DOCUMENTS


def test_report_exists_sql_matches_live_report_filters() -> None:
    for condition in (
        "d.source_type = 'REPORT'",
        "d.embedding IS NOT NULL",
        "r.status = 'DONE'",
        "s.deleted_at IS NULL",
        "s.status <> 'CANCELED'",
    ):
        assert condition in REPORT_DOCUMENTS_EXIST


def test_report_exists_sql_avoids_full_count() -> None:
    normalized = REPORT_DOCUMENTS_EXIST.casefold().replace(" ", "")
    assert "exists" in normalized
    assert "limit1" in normalized
    assert "count(*)" not in normalized


@pytest.mark.parametrize(
    ("rows", "expected"),
    [([(True,)], True), ([(False,)], False), ([], False)],
)
def test_report_documents_exist_returns_boolean(rows, expected) -> None:
    connection = FakeConnection(rows)

    assert report_documents_exist(connection, 15) is expected
    _, params = connection.cursor_obj.executed[0]
    assert params == {"apartment_id": 15}


def test_q2_sql_orders_by_distance_ascending() -> None:
    assert "ORDER BY d.embedding <=> %(query_vec)s" in SEARCH_REPORT_DOCUMENTS


def test_q3_similarity_is_one_minus_distance() -> None:
    assert "1 - (d.embedding <=> %(query_vec)s) AS similarity" in (
        SEARCH_REPORT_DOCUMENTS
    )

    connection = FakeConnection([(48, "본문", SOURCE_AT, 0.72)])

    hits = search_report_documents(connection, 15, [0.1], 5)

    assert hits[0].similarity == 0.72
    assert hits[0].source_id == 48
    assert hits[0].source_at == SOURCE_AT


def test_q4_top_k_is_passed_as_limit_parameter() -> None:
    assert "LIMIT %(top_k)s" in SEARCH_REPORT_DOCUMENTS

    connection = FakeConnection([])
    search_report_documents(connection, 15, [0.1], 3)

    _, params = connection.cursor_obj.executed[0]
    assert params["top_k"] == 3
    assert params["apartment_id"] == 15


def test_apartment_profile_is_fetched() -> None:
    connection = FakeConnection([
        ("래미안 옥수 리버젠", "서울 성동구", "성동구", "옥수동", 1976, "2012-12", 2500)
    ])

    assert fetch_apartment_profile(connection, 15) == ApartmentProfile(
        name="래미안 옥수 리버젠",
        address="서울 성동구",
        district_name="성동구",
        dong_name="옥수동",
        household_count=1976,
        completion_year_month="2012-12",
        parking_space_count=2500,
    )
    assert "WHERE id = %(apartment_id)s" in SELECT_APARTMENT_PROFILE


def test_apartment_profile_is_none_when_missing() -> None:
    assert fetch_apartment_profile(FakeConnection([]), 15) is None


def test_default_settings_expand_retrieval_budget() -> None:
    from app.rag.settings_chatbot import ChatbotSettings

    settings = ChatbotSettings()

    assert settings.top_k == 60
    assert settings.max_context_chars == 30000
    assert not hasattr(settings, "similarity_threshold")


def test_similarity_threshold_environment_variable_is_ignored(monkeypatch) -> None:
    from app.rag.settings_chatbot import ChatbotSettings

    monkeypatch.setenv("CHATBOT_SIMILARITY_THRESHOLD", "not-a-number")
    assert ChatbotSettings.from_env() == ChatbotSettings()


def test_default_top_k_is_passed_to_database() -> None:
    from app.rag.settings_chatbot import ChatbotSettings

    connection = FakeConnection([])
    search_report_documents(connection, 15, [0.1], ChatbotSettings().top_k)

    _, params = connection.cursor_obj.executed[0]
    assert params["top_k"] == 60


def _hit(source_id: int, length: int, similarity: float) -> ReportChunkHit:
    return ReportChunkHit(
        source_id=source_id,
        content="가" * length,
        source_at=SOURCE_AT,
        similarity=similarity,
    )


def test_truncation_stops_at_the_char_budget() -> None:
    hits = [_hit(i, 400, 0.9 - i / 100) for i in range(5)]

    used = truncate_to_char_limit(hits, 1000)

    assert len(used) == 2
    assert [hit.source_id for hit in used] == [0, 1]


def test_truncation_keeps_first_hit_even_when_oversized() -> None:
    used = truncate_to_char_limit([_hit(1, 5000, 0.9)], 1000)

    assert len(used) == 1


def test_truncation_keeps_everything_within_budget() -> None:
    hits = [_hit(i, 100, 0.9) for i in range(3)]

    assert len(truncate_to_char_limit(hits, 1000)) == 3


def test_twelve_thousand_chars_all_fit_in_default_budget() -> None:
    hits = [_hit(i, 800, 0.9) for i in range(15)]
    assert truncate_to_char_limit(hits, 30000) == hits


def test_forty_five_thousand_chars_stop_at_default_budget() -> None:
    hits = [_hit(i, 900, 0.9) for i in range(50)]
    used = truncate_to_char_limit(hits, 30000)
    assert len(used) == 33
    assert sum(len(hit.content) for hit in used) <= 30000
