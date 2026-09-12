"""Report index runner cleanup behavior tests."""

from datetime import datetime, timezone
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from app.rag.index_report import run
from app.rag.report_documents import ReportRow


class FakeConnection:
    def __init__(self) -> None:
        self.commit_count = 0
        self.closed = False

    def commit(self) -> None:
        self.commit_count += 1

    def close(self) -> None:
        self.closed = True


def _arguments(*, dry_run: bool = False, apartment_ids: list[int] | None = None):
    return SimpleNamespace(
        all=apartment_ids is None,
        apartment_ids=apartment_ids,
        batch_size=None,
        dry_run=dry_run,
    )


def _settings():
    return SimpleNamespace(
        embedding_batch_size=16,
        embedding_model="test-model",
        embedding_device="cpu",
    )


def test_zero_indexable_reports_still_prune_ineligible_documents() -> None:
    connection = FakeConnection()
    with (
        patch("app.rag.index_report.connect", return_value=connection),
        patch("app.rag.index_report.count_reports", return_value=(0, 2, 1)),
        patch(
            "app.rag.index_report.delete_ineligible_report_documents",
            return_value=3,
        ) as cleanup,
    ):
        result = run(_arguments(apartment_ids=[7, 8]), _settings())

    assert result == 0
    cleanup.assert_called_once_with(connection, [7, 8])
    assert connection.commit_count == 2
    assert connection.closed is True


def test_dry_run_never_deletes_documents() -> None:
    connection = FakeConnection()
    with (
        patch("app.rag.index_report.connect", return_value=connection),
        patch("app.rag.index_report.count_reports", return_value=(0, 0, 0)),
        patch("app.rag.index_report.stream_report_batches", return_value=[]),
        patch("app.rag.index_report.delete_ineligible_report_documents") as cleanup,
        patch("app.rag.index_report.delete_stale_report_chunks") as stale_cleanup,
    ):
        result = run(_arguments(dry_run=True), _settings())

    assert result == 0
    cleanup.assert_not_called()
    stale_cleanup.assert_not_called()
    assert connection.closed is True


def test_empty_done_report_deletes_all_of_its_old_chunks() -> None:
    connection = FakeConnection()
    row = ReportRow(
        id=48,
        apartment_id=15,
        result_json=None,
        completed_at=datetime(2026, 7, 25, tzinfo=timezone.utc),
        updated_at=datetime(2026, 7, 25, tzinfo=timezone.utc),
        apartment_name="테스트 아파트",
    )
    embedder = SimpleNamespace(
        model_name="test-model",
        device="cpu",
        dimension=384,
        encode_documents=MagicMock(),
    )
    with (
        patch("app.rag.index_report.connect", return_value=connection),
        patch("app.rag.index_report.count_reports", return_value=(1, 0, 0)),
        patch(
            "app.rag.index_report.delete_ineligible_report_documents",
            return_value=0,
        ),
        patch("app.rag.index_report.RagEmbedder", return_value=embedder),
        patch("app.rag.index_report.stream_report_batches", return_value=[[row]]),
        patch(
            "app.rag.index_report.delete_stale_report_chunks",
            return_value=2,
        ) as stale_cleanup,
        patch("app.rag.index_report.upsert_batch") as upsert,
    ):
        result = run(_arguments(), _settings())

    assert result == 0
    stale_cleanup.assert_called_once_with(connection, 48, [])
    upsert.assert_not_called()
    embedder.encode_documents.assert_not_called()
    assert connection.closed is True
