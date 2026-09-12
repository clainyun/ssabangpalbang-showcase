"""CLI batch indexer for completed report documents."""

from __future__ import annotations

import argparse
import logging
import sys
import time
from collections.abc import Sequence

from app.rag.config import RagSettings
from app.rag.db import (
    connect,
    count_reports,
    delete_ineligible_report_documents,
    delete_stale_report_chunks,
    stream_report_batches,
    upsert_batch,
)
from app.rag.report_documents import ReportRow, build_report_documents
from app.rag.embedder import RagEmbedder


LOGGER = logging.getLogger("index_report")


def _positive_int(value: str) -> int:
    parsed = int(value)
    if parsed < 1:
        raise argparse.ArgumentTypeError("1 이상의 정수여야 합니다")
    return parsed


def _apartment_ids(value: str) -> list[int]:
    try:
        values = [_positive_int(item.strip()) for item in value.split(",")]
    except (ValueError, argparse.ArgumentTypeError) as exc:
        raise argparse.ArgumentTypeError(
            "쉼표로 구분한 양의 정수 목록이어야 합니다"
        ) from exc
    if not values or any(not item.strip() for item in value.split(",")):
        raise argparse.ArgumentTypeError(
            "쉼표로 구분한 양의 정수 목록이어야 합니다"
        )
    return list(dict.fromkeys(values))


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="완료된 임장 리포트를 RAG 문서로 색인합니다."
    )
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--all", action="store_true")
    target.add_argument("--apartment-ids", type=_apartment_ids)
    parser.add_argument("--batch-size", type=_positive_int)
    parser.add_argument("--dry-run", action="store_true")
    return parser


def _elapsed(started_at: float) -> str:
    seconds = int(time.monotonic() - started_at)
    hours, remainder = divmod(seconds, 3600)
    minutes, seconds = divmod(remainder, 60)
    return f"{hours:02d}:{minutes:02d}:{seconds:02d}"


def run(arguments: argparse.Namespace, settings: RagSettings) -> int:
    apartment_ids = None if arguments.all else arguments.apartment_ids
    batch_size = arguments.batch_size or settings.embedding_batch_size
    started_at = time.monotonic()
    connection = connect(settings)
    try:
        total, not_done, study_excluded = count_reports(
            connection, apartment_ids
        )
        connection.commit()
        print(
            f"[index_report] 대상 리포트: {total:,}건 "
            "(status=DONE·스터디 공개 필터 후)"
        )

        if arguments.dry_run:
            chunks = skipped = 0
            for rows in stream_report_batches(
                connection, apartment_ids, batch_size
            ):
                for row in rows:
                    documents = build_report_documents(row)
                    if not documents:
                        skipped += 1
                        _warn_empty(row)
                        continue
                    chunks += len(documents)
            _print_excluded(not_done, study_excluded, skipped)
            print(f"[index_report] dry-run 청크: {chunks:,}건 (DB 미변경)")
            return 0

        deleted = delete_ineligible_report_documents(connection, apartment_ids)
        connection.commit()

        if total == 0:
            _print_excluded(not_done, study_excluded, 0)
            print(
                "[index_report] 완료: 청크 0건 "
                f"(신규 0, 갱신 0, 실패 0), 옛 청크 삭제 {deleted:,}건, "
                f"소요 {_elapsed(started_at)}"
            )
            return 0

        embedder = RagEmbedder(
            settings.embedding_model,
            settings.embedding_device,
        )
        print(
            "[index_report] 모델 로드 완료: "
            f"{embedder.model_name} ({embedder.device}, "
            f"dim={embedder.dimension})"
        )

        processed = inserted = updated = failed = 0
        skipped = 0
        for rows in stream_report_batches(
            connection, apartment_ids, batch_size
        ):
            documents = []
            for row in rows:
                row_documents = build_report_documents(row)
                if not row_documents:
                    skipped += 1
                    _warn_empty(row)
                    deleted += delete_stale_report_chunks(
                        connection,
                        row.id,
                        [],
                    )
                    continue
                deleted += delete_stale_report_chunks(
                    connection,
                    row.id,
                    [document.reindex_key for document in row_documents],
                )
                documents.extend(row_documents)
            processed += len(rows)
            if not documents:
                connection.commit()
                continue

            vectors = embedder.encode_documents(
                [document.content for document in documents],
                batch_size,
            )
            batch_inserted, batch_updated, batch_failed = upsert_batch(
                connection,
                documents,
                vectors,
            )
            inserted += batch_inserted
            updated += batch_updated
            failed += batch_failed
            print(
                f"[index_report] {processed}/{total} 리포트 처리 "
                f"(신규 {inserted}, 갱신 {updated}, 실패 {failed})"
            )

        _print_excluded(not_done, study_excluded, skipped)
        print(
            f"[index_report] 청크 {inserted + updated:,}건 저장 "
            f"(신규 {inserted:,}, 갱신 {updated:,}, 실패 {failed:,}), "
            f"옛 청크 삭제 {deleted:,}건"
        )
        print(f"[index_report] 완료: 소요 {_elapsed(started_at)}")
        return 1 if failed else 0
    finally:
        connection.close()


def _print_excluded(not_done: int, study_excluded: int, skipped: int) -> None:
    print(
        "[index_report]   제외: status≠DONE "
        f"{not_done:,}건, 스터디 삭제·취소 {study_excluded:,}건, "
        f"본문 추출 실패 {skipped:,}건"
    )


def _warn_empty(row: ReportRow) -> None:
    LOGGER.warning(
        "본문을 추출하지 못해 report_id=%s를 건너뜁니다", row.id
    )


def main(argv: Sequence[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    arguments = _parser().parse_args(argv)
    try:
        settings = RagSettings.from_env()
        return run(arguments, settings)
    except Exception as exc:
        LOGGER.error("색인 실패: %s", exc)
        return 1


if __name__ == "__main__":
    sys.exit(main())
