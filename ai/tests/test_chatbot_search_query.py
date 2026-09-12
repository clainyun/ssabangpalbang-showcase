"""Search query normalization and regional context tests."""

import pytest

from app.prompts.chatbot_prompt import build_search_query


@pytest.mark.parametrize(
    ("apartment_name", "district_name", "dong_name", "question", "expected"),
    [
        ("도곡렉슬", "강남구", "도곡동", "도곡렉슬 재개발", "강남구 도곡동 도곡렉슬 재개발"),
        ("도곡렉슬", "강남구", "도곡동", "재개발 뭐있나", "도곡렉슬 강남구 도곡동 재개발 뭐있나"),
        ("도곡렉슬", None, None, "도곡 렉슬 재개발", "도곡 렉슬 재개발"),
        ("도곡렉슬", "강남구", "도곡동", "학교", "도곡렉슬 강남구 도곡동 학교"),
        ("도곡렉슬", None, "도곡동", "학교", "도곡렉슬 도곡동 학교"),
        ("도곡렉슬", None, None, "학교", "도곡렉슬 학교"),
        ("도곡렉슬", "강남구", "도곡동", "강남구 도곡동 학교", "도곡렉슬 강남구 도곡동 학교"),
        (None, "", "   ", "  학교  ", "학교"),
        ("  도곡  렉슬 ", " 강남구 ", "도곡동", "  학교   어디야 ", "도곡 렉슬 강남구 도곡동 학교 어디야"),
    ],
)
def test_build_search_query(
    apartment_name: str | None,
    district_name: str | None,
    dong_name: str | None,
    question: str,
    expected: str,
) -> None:
    assert (
        build_search_query(
            apartment_name, district_name, dong_name, question
        )
        == expected
    )
