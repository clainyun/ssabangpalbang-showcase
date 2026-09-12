package com.ssafy.ssabangpalbang.fieldvisit.dto;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

/**
 * AI-002 체크리스트 생성·조회 API의 성공 응답 코드다(docs/API.md 기준).
 *
 * <p>이 enum은 컨트롤러 배선 이전 단계(체크리스트 권한·상태 검증 커밋)에서
 * 미리 정의해 두고, 실제 사용은 POST/GET API 구현 커밋에서 연결한다.</p>
 */
public enum ChecklistResponseCode implements ResponseCode {

    CHECKLIST_GENERATE_SUCCESS(
            "CHECKLIST_GENERATE_SUCCESS",
            "개인 맞춤 체크리스트를 생성했습니다."
    ),
    CHECKLIST_ALREADY_EXISTS(
            "CHECKLIST_ALREADY_EXISTS",
            "이미 생성된 체크리스트입니다."
    ),
    CHECKLIST_FALLBACK_GENERATE_SUCCESS(
            "CHECKLIST_FALLBACK_GENERATE_SUCCESS",
            "기본 체크리스트를 생성했습니다."
    ),
    CHECKLIST_DETAIL_SUCCESS(
            "CHECKLIST_DETAIL_SUCCESS",
            "체크리스트 조회에 성공했습니다."
    ),
    CHECKLIST_GENERATION_STATUS_SUCCESS(
            "CHECKLIST_GENERATION_STATUS_SUCCESS",
            "체크리스트 생성 상태 조회에 성공했습니다."
    );

    private final String code;
    private final String message;

    ChecklistResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
