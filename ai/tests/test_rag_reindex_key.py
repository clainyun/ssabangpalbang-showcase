"""Stable key and source type tests for public apartment documents."""

from app.rag.documents import SourceType, apartment_reindex_key


def test_apartment_reindex_key() -> None:
    assert apartment_reindex_key(15) == "APARTMENT:15"


def test_same_id_makes_same_key() -> None:
    assert apartment_reindex_key(15) == apartment_reindex_key(15)


def test_different_ids_make_different_keys() -> None:
    assert apartment_reindex_key(15) != apartment_reindex_key(16)


def test_bigint_max_key_fits_column() -> None:
    assert len(apartment_reindex_key(9_223_372_036_854_775_807)) <= 200


def test_apartment_source_type_fits_column() -> None:
    assert SourceType.APARTMENT.value == "APARTMENT"
    assert len(SourceType.APARTMENT.value) <= 20
