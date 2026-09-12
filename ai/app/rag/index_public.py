"""CLI batch indexer for public apartment documents."""

from __future__ import annotations

import argparse
import logging
import sys
import time
from collections.abc import Sequence

from app.rag.config import RagSettings
from app.rag.db import (
    connect,
    count_apartments,
    stream_apartment_batches,
    upsert_batch,
)
from app.rag.documents import build_apartment_document
from app.rag.embedder import RagEmbedder


LOGGER = logging.getLogger("index_public")


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
        description="아파트 공공데이터를 RAG 문서로 색인합니다."
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
        total = count_apartments(connection, apartment_ids)
        connection.commit()
        print(f"[index_public] 대상 아파트: {total:,}건")
        seen_ids: set[int] = set()

        if arguments.dry_run:
            samples: list[str] = []
            for rows in stream_apartment_batches(
                connection, apartment_ids, batch_size
            ):
                for row in rows:
                    seen_ids.add(row.id)
                    document = build_apartment_document(row)
                    if len(samples) < 3:
                        samples.append(document.content)
            print(f"[index_public] dry-run 문서: {len(seen_ids):,}건")
            for index, sample in enumerate(samples, start=1):
                print(f"[index_public] 샘플 {index}:\n{sample}")
            _warn_missing_ids(apartment_ids, seen_ids)
            return 0

        if total == 0:
            _warn_missing_ids(apartment_ids, seen_ids)
            print(
                "[index_public] 완료: 총 0건 "
                f"(신규 0, 갱신 0, 실패 0), 소요 {_elapsed(started_at)}"
            )
            return 0

        embedder = RagEmbedder(
            settings.embedding_model,
            settings.embedding_device,
        )
        print(
            "[index_public] 모델 로드 완료: "
            f"{embedder.model_name} ({embedder.device}, "
            f"dim={embedder.dimension})"
        )

        processed = inserted = updated = failed = 0
        next_progress = 100
        for rows in stream_apartment_batches(
            connection, apartment_ids, batch_size
        ):
            seen_ids.update(row.id for row in rows)
            documents = [build_apartment_document(row) for row in rows]
            vectors = embedder.encode_documents(
                [document.content for document in documents],
                batch_size,
            )
            batch_inserted, batch_updated, batch_failed = upsert_batch(
                connection,
                documents,
                vectors,
            )
            processed += len(rows)
            inserted += batch_inserted
            updated += batch_updated
            failed += batch_failed
            while processed >= next_progress:
                print(
                    f"[index_public] {processed}/{total} 처리 "
                    f"(신규 {inserted}, 갱신 {updated}, 실패 {failed})"
                )
                next_progress += 100

        _warn_missing_ids(apartment_ids, seen_ids)
        print(
            f"[index_public] 완료: 총 {processed:,}건 "
            f"(신규 {inserted:,}, 갱신 {updated:,}, 실패 {failed:,}), "
            f"소요 {_elapsed(started_at)}"
        )
        return 1 if failed else 0
    finally:
        connection.close()


def _warn_missing_ids(
    requested_ids: list[int] | None,
    seen_ids: set[int],
) -> None:
    if requested_ids is None:
        return
    for apartment_id in sorted(set(requested_ids) - seen_ids):
        LOGGER.warning("존재하지 않는 apartment_id=%s를 건너뜁니다", apartment_id)


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
