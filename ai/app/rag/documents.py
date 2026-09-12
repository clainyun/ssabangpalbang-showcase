"""Pure document normalization rules shared by RAG indexers."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum


class SourceType(StrEnum):
    APARTMENT = "APARTMENT"
    REPORT = "REPORT"


@dataclass(frozen=True)
class ApartmentRow:
    id: int
    name: str
    address: str | None
    district_name: str | None
    dong_name: str | None
    household_count: int | None
    completion_year_month: str | None
    parking_space_count: int | None
    updated_at: datetime


@dataclass(frozen=True)
class RagDocument:
    apartment_id: int
    source_type: SourceType
    source_id: int
    content: str
    source_at: datetime
    reindex_key: str


def apartment_reindex_key(apartment_id: int) -> str:
    return f"{SourceType.APARTMENT.value}:{apartment_id}"


def normalize_apartment(row: ApartmentRow) -> str:
    lines = [row.name]
    if row.address:
        lines.append(f"주소: {row.address}")
    if row.district_name or row.dong_name:
        region = " ".join(
            value
            for value in (row.district_name, row.dong_name)
            if value
        )
        lines.append(f"지역: {region}")
    if row.household_count is not None:
        lines.append(f"세대수: {row.household_count}세대")
    if row.completion_year_month is not None:
        lines.append(f"준공: {row.completion_year_month}")
    if row.parking_space_count is not None:
        lines.append(f"주차: {row.parking_space_count}대")
    return "\n".join(lines)


def build_apartment_document(row: ApartmentRow) -> RagDocument:
    return RagDocument(
        apartment_id=row.id,
        source_type=SourceType.APARTMENT,
        source_id=row.id,
        content=normalize_apartment(row),
        source_at=row.updated_at,
        reindex_key=apartment_reindex_key(row.id),
    )
