"""Public allowlist tests for completed report RAG documents."""

from app.rag.report_documents import build_chunk_content, extract_texts


LONG_A = "교통 접근성이 좋아 출퇴근 시간이 짧다는 의견이 많았습니다."
LONG_B = "단지 내 주차 공간이 부족해 저녁 시간대에는 혼잡하다고 합니다."
LONG_C = "인근 초등학교까지 도보 10분 거리라 학부모 만족도가 높았습니다."


def test_chunk_content_is_body_without_apartment_heading() -> None:
    assert build_chunk_content("반포자이", "본문") == "본문"


def test_collects_only_public_report_fields() -> None:
    result_json = {
        "title": "래미안 옥수 리버젠 임장 리포트",
        "summary": LONG_A,
        "analysisTags": ["역세권", "주차 주의"],
        "topPositiveFeatures": [
            {"label": "교통", "summary": LONG_B, "sourceIds": [1, 2]},
        ],
        "categories": [
            {
                "category": "교육",
                "summary": LONG_C,
                "participantOpinions": [
                    {"participantLabel": "참여자 1", "summary": LONG_A},
                ],
            },
        ],
    }

    assert extract_texts(result_json) == [
        "리포트 제목: 래미안 옥수 리버젠 임장 리포트",
        f"종합 요약: {LONG_A}",
        "분석 태그: 역세권, 주차 주의",
        f"긍정 요소 - 교통: {LONG_B}",
        f"카테고리 - 교육: {LONG_C}",
        f"카테고리 - 교육 / 참여자 의견: {LONG_A}",
    ]


def test_private_evidence_and_unknown_keys_are_ignored() -> None:
    private_text = "이 내용은 공개하면 안 되는 참여자의 개인 메모입니다."
    result_json = {
        "summary": LONG_A,
        "personalMemo": private_text,
        "fieldRecords": [
            {
                "textContent": private_text,
                "sttText": private_text,
                "accessUrl": "https://private.example/photo",
                "objectKey": "private/member/7/audio.wav",
            },
        ],
        "evidences": {"rawAudio": private_text},
        "비슷하지도않은키": {"또다른키": private_text},
    }

    texts = extract_texts(result_json)

    assert texts == [f"종합 요약: {LONG_A}"]
    assert private_text not in " ".join(texts)
    assert "private.example" not in " ".join(texts)


def test_private_urls_are_removed_even_from_allowed_text() -> None:
    result_json = {
        "summary": (
            f"{LONG_A} "
            "https://private.example/photo?X-Amz-Signature=secret"
        ),
        "analysisTags": ["s3://private-bucket/object-key", "교통"],
        "topPositiveFeatures": [
            {
                "label": "자료",
                "summary": (
                    f"{LONG_B} private/member/7/audio.wav "
                    "arn:aws:s3:::secret-bucket/raw.wav "
                    "token=secret-value"
                ),
            }
        ],
    }

    texts = extract_texts(result_json)
    combined = " ".join(texts)

    assert "private.example" not in combined
    assert "X-Amz" not in combined
    assert "private-bucket" not in combined
    assert "private/member" not in combined
    assert "secret-bucket" not in combined
    assert "secret-value" not in combined
    assert f"종합 요약: {LONG_A}" in texts


def test_malformed_public_fields_are_ignored() -> None:
    result_json = {
        "title": 123,
        "summary": [LONG_A],
        "analysisTags": "교통",
        "topPositiveFeatures": {"label": "교통", "summary": LONG_B},
        "categories": [None, {"summary": 100, "participantOpinions": {}}],
    }

    assert extract_texts(result_json) == []


def test_short_summary_is_not_indexed_as_prose() -> None:
    assert extract_texts({"summary": "좋음"}) == []


def test_none_and_empty_result_json_return_empty_list() -> None:
    assert extract_texts(None) == []
    assert extract_texts({}) == []


def test_top_level_list_and_string_are_not_public_report_documents() -> None:
    assert extract_texts([LONG_A, {"summary": LONG_B}]) == []
    assert extract_texts(LONG_A) == []


def test_surrounding_and_repeated_whitespace_is_normalized() -> None:
    assert extract_texts({"summary": f"  {LONG_A}  \n  추가 확인이 필요합니다. "}) == [
        f"종합 요약: {LONG_A} 추가 확인이 필요합니다."
    ]
