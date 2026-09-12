"""Document normalization tests for public apartment RAG indexing."""

from datetime import datetime, timezone

from app.rag.documents import ApartmentRow, normalize_apartment


def _row(**overrides: object) -> ApartmentRow:
    values = {
        "id": 15,
        "name": "역삼 테스트 아파트",
        "address": "서울특별시 강남구 테헤란로 1",
        "district_name": "강남구",
        "dong_name": "역삼동",
        "household_count": 120,
        "completion_year_month": "2015-03",
        "parking_space_count": 140,
        "updated_at": datetime(2026, 7, 31, tzinfo=timezone.utc),
    }
    values.update(overrides)
    return ApartmentRow(**values)


def test_all_fields_make_six_line_document() -> None:
    content = normalize_apartment(_row())

    assert content.splitlines() == [
        "역삼 테스트 아파트",
        "주소: 서울특별시 강남구 테헤란로 1",
        "지역: 강남구 역삼동",
        "세대수: 120세대",
        "준공: 2015-03",
        "주차: 140대",
    ]


def test_none_address_omits_address_line_and_none_text() -> None:
    content = normalize_apartment(_row(address=None))

    assert "주소:" not in content
    assert "None" not in content


def test_none_household_count_omits_household_line() -> None:
    assert "세대수:" not in normalize_apartment(_row(household_count=None))


def test_none_district_and_dong_omit_region_line() -> None:
    content = normalize_apartment(
        _row(district_name=None, dong_name=None)
    )

    assert "지역:" not in content


def test_dong_only_has_no_leading_space() -> None:
    content = normalize_apartment(_row(district_name=None))

    assert "지역: 역삼동" in content
    assert "지역:  역삼동" not in content


def test_name_only_still_makes_one_line_document() -> None:
    content = normalize_apartment(
        _row(
            address=None,
            district_name=None,
            dong_name=None,
            household_count=None,
            completion_year_month=None,
            parking_space_count=None,
        )
    )

    assert content == "역삼 테스트 아파트"


def test_zero_household_count_is_not_omitted() -> None:
    content = normalize_apartment(_row(household_count=0))

    assert "세대수: 0세대" in content
