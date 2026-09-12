"""Cleanup SQL tests for report RAG documents."""

from app.rag.db import (
    delete_ineligible_report_documents,
    delete_stale_report_chunks,
)


class FakeCursor:
    def __init__(self, rowcount: int = 0) -> None:
        self.rowcount = rowcount
        self.executed: list[tuple[str, dict]] = []

    def __enter__(self) -> "FakeCursor":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def execute(self, sql: str, params: dict) -> None:
        self.executed.append((sql, params))


class FakeConnection:
    def __init__(self, rowcount: int = 0) -> None:
        self.cursor_obj = FakeCursor(rowcount)

    def cursor(self) -> FakeCursor:
        return self.cursor_obj


def test_ineligible_cleanup_uses_live_report_anti_join() -> None:
    connection = FakeConnection(rowcount=3)

    deleted = delete_ineligible_report_documents(connection, [7, 8])

    sql, params = connection.cursor_obj.executed[0]
    assert deleted == 3
    assert "NOT EXISTS" in sql
    assert "r.status = 'DONE'" in sql
    assert "s.deleted_at IS NULL" in sql
    assert "s.status <> 'CANCELED'" in sql
    assert "d.apartment_id = ANY(%(ids)s::bigint[])" in sql
    assert params == {"ids": [7, 8]}


def test_empty_apartment_scope_is_a_noop() -> None:
    connection = FakeConnection(rowcount=9)

    assert delete_ineligible_report_documents(connection, []) == 0
    assert connection.cursor_obj.executed == []


def test_empty_current_keys_delete_all_chunks_for_the_report() -> None:
    connection = FakeConnection(rowcount=4)

    deleted = delete_stale_report_chunks(connection, 48, [])

    sql, params = connection.cursor_obj.executed[0]
    assert deleted == 4
    assert "source_id = %(report_id)s" in sql
    assert "ANY" not in sql
    assert params == {"report_id": 48}


def test_current_keys_are_explicitly_cast_for_postgresql() -> None:
    connection = FakeConnection(rowcount=2)

    delete_stale_report_chunks(connection, 48, ["REPORT:48:0"])

    sql, params = connection.cursor_obj.executed[0]
    assert "ANY(%(current_keys)s::varchar[])" in sql
    assert params == {
        "report_id": 48,
        "current_keys": ["REPORT:48:0"],
    }
