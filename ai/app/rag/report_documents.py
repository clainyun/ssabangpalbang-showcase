"""Public-field normalization rules for completed report RAG documents."""

from __future__ import annotations

import re
from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from datetime import datetime
from typing import Any

from app.rag.documents import RagDocument, SourceType


CHUNK_CHAR_LIMIT = 1000
"""Chunk size in characters, not tokens. Applies to the chunk body."""

MIN_TEXT_LENGTH = 20
"""Minimum length for a standalone public summary."""

PARAGRAPH_SEPARATOR = "\n\n"

_SENTENCE_BOUNDARY = re.compile(r"(?<=[.!?。？！])\s+")
_PRIVATE_URL = re.compile(
    r"(?i)(?:https?|s3|gs|file)://\S+|data:(?:image|audio)/\S+"
)
_PRIVATE_LOCATOR = re.compile(
    r"(?i)\barn:aws:s3:::\S+"
    r"|\b(?:private|uploads?|media|audio|photos?|members?|field-records?)"
    r"/[^\s,;]+"
)
_SIGNED_QUERY_VALUE = re.compile(
    r"(?i)\b(?:X-Amz-[A-Za-z-]+|AWSAccessKeyId|Signature|access_token|token)"
    r"=[^\s&]+"
)


@dataclass(frozen=True)
class ReportRow:
    id: int
    apartment_id: int
    result_json: Any
    completed_at: datetime | None
    updated_at: datetime
    apartment_name: str


def report_reindex_key(report_id: int, chunk_index: int) -> str:
    return f"{SourceType.REPORT.value}:{report_id}:{chunk_index}"


def report_source_at(row: ReportRow) -> datetime:
    """``completed_at`` wins; ``updated_at`` covers DONE rows that lack it."""
    return row.completed_at or row.updated_at


def extract_texts(result_json: Any) -> list[str]:
    """Collect only fields exposed by the public report contracts.

    Unknown keys are deliberately ignored. Report generation output can also
    contain participant evidence, personal notes and private media metadata;
    recursively walking arbitrary values would turn those into public RAG
    documents.
    """
    if not isinstance(result_json, dict):
        return []

    collected: dict[str, None] = {}
    _add_labeled(collected, "리포트 제목", result_json.get("title"))
    _add_labeled(
        collected,
        "종합 요약",
        result_json.get("summary"),
        require_summary=True,
    )

    tags = _string_items(result_json.get("analysisTags"))
    if tags:
        _add(collected, f"분석 태그: {', '.join(tags)}")

    _add_features(
        collected,
        result_json.get("topPositiveFeatures"),
        "긍정 요소",
    )
    _add_features(
        collected,
        result_json.get("topCautionFeatures"),
        "주의 요소",
    )
    _add_categories(collected, result_json.get("categories"))
    return list(collected)


def _add_features(
    collected: dict[str, None],
    node: Any,
    prefix: str,
) -> None:
    if not isinstance(node, list):
        return
    for feature in node:
        if not isinstance(feature, dict):
            continue
        label = _safe_text(feature.get("label"))
        summary = _safe_text(feature.get("summary"))
        if summary is None or len(summary) < MIN_TEXT_LENGTH:
            continue
        heading = prefix if label is None else f"{prefix} - {label}"
        _add(collected, f"{heading}: {summary}")


def _add_categories(collected: dict[str, None], node: Any) -> None:
    if not isinstance(node, list):
        return
    for category in node:
        if not isinstance(category, dict):
            continue
        label = _safe_text(category.get("category"))
        summary = _safe_text(category.get("summary"))
        if summary is not None and len(summary) >= MIN_TEXT_LENGTH:
            heading = "카테고리" if label is None else f"카테고리 - {label}"
            _add(collected, f"{heading}: {summary}")

        opinions = category.get("participantOpinions")
        if not isinstance(opinions, list):
            continue
        for opinion in opinions:
            if not isinstance(opinion, dict):
                continue
            opinion_summary = _safe_text(opinion.get("summary"))
            if opinion_summary is None or len(opinion_summary) < MIN_TEXT_LENGTH:
                continue
            heading = "참여자 의견"
            if label is not None:
                heading = f"카테고리 - {label} / 참여자 의견"
            _add(collected, f"{heading}: {opinion_summary}")


def _add_labeled(
    collected: dict[str, None],
    label: str,
    value: Any,
    require_summary: bool = False,
) -> None:
    text = _safe_text(value)
    if text is None:
        return
    if require_summary and len(text) < MIN_TEXT_LENGTH:
        return
    _add(collected, f"{label}: {text}")


def _string_items(node: Any) -> list[str]:
    if not isinstance(node, list):
        return []
    return [
        text
        for value in node
        if (text := _safe_text(value)) is not None
    ]


def _safe_text(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    text = _PRIVATE_URL.sub("", value)
    text = _PRIVATE_LOCATOR.sub("", text)
    text = _SIGNED_QUERY_VALUE.sub("", text).strip()
    return text or None


def _add(collected: dict[str, None], text: str) -> None:
    normalized = " ".join(text.split())
    if normalized:
        collected.setdefault(normalized, None)


def chunk_texts(
    texts: Sequence[str],
    limit: int = CHUNK_CHAR_LIMIT,
) -> list[str]:
    """Pack texts into chunks of at most ``limit`` characters.

    Splits on paragraph boundaries first, then sentence boundaries, and only
    cuts mid-word when a single sentence is longer than ``limit``. Adjacent
    pieces are merged so the result is a few full chunks instead of many tiny
    ones.
    """
    pieces: list[str] = []
    for text in texts:
        pieces.extend(_split_to_limit(text, limit))

    chunks: list[str] = []
    current = ""
    for piece in pieces:
        candidate = (
            piece if not current else current + PARAGRAPH_SEPARATOR + piece
        )
        if len(candidate) <= limit:
            current = candidate
            continue
        if current:
            chunks.append(current)
        current = piece
    if current:
        chunks.append(current)
    return chunks


def _split_to_limit(text: str, limit: int) -> list[str]:
    pieces: list[str] = []
    for paragraph in _paragraphs(text):
        if len(paragraph) <= limit:
            pieces.append(paragraph)
            continue
        for sentence in _sentences(paragraph):
            if len(sentence) <= limit:
                pieces.append(sentence)
            else:
                pieces.extend(_hard_split(sentence, limit))
    return pieces


def _paragraphs(text: str) -> Iterable[str]:
    for paragraph in text.split(PARAGRAPH_SEPARATOR):
        stripped = paragraph.strip()
        if stripped:
            yield stripped


def _sentences(paragraph: str) -> Iterable[str]:
    for sentence in _SENTENCE_BOUNDARY.split(paragraph):
        stripped = sentence.strip()
        if stripped:
            yield stripped


def _hard_split(sentence: str, limit: int) -> list[str]:
    return [sentence[start:start + limit] for start in range(0, len(sentence), limit)]


def build_chunk_content(apartment_name: str, chunk: str) -> str:
    """Keep the signature stable while excluding the similarity-skewing header."""
    return chunk


def build_report_documents(row: ReportRow) -> list[RagDocument]:
    """Return one document per chunk. Empty when nothing readable was found."""
    chunks = chunk_texts(extract_texts(row.result_json))
    source_at = report_source_at(row)
    return [
        RagDocument(
            apartment_id=row.apartment_id,
            source_type=SourceType.REPORT,
            source_id=row.id,
            content=build_chunk_content(row.apartment_name, chunk),
            source_at=source_at,
            reindex_key=report_reindex_key(row.id, chunk_index),
        )
        for chunk_index, chunk in enumerate(chunks)
    ]
