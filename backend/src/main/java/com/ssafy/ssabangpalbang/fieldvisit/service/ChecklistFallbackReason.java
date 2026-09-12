package com.ssafy.ssabangpalbang.fieldvisit.service;

/**
 * 기본(fallback) 체크리스트를 사용하게 된 원인이다.
 *
 * <p>사용자 확정 사항에 따라 "개인화 입력 부족"과 "기술적 AI 실패"를 로그·테스트에서
 * 구분할 수 있도록 별도 그룹으로 나눈다. 두 그룹 모두 최종적으로는
 * {@code isFallback = true} 응답으로 이어지지만, 원인은 다르게 기록해야 한다.</p>
 */
public enum ChecklistFallbackReason {

    /**
     * 정상 흐름이라면 온보딩이 전부 필수라 발생하지 않아야 하지만, 기존 데이터·
     * 정합성 문제로 {@code member_preference} 행이 없거나 필수 필드가 비어 있거나
     * 허용되지 않은 값이 저장된 경우다.
     */
    PERSONALIZATION_INSUFFICIENT(FallbackCategory.PERSONALIZATION_INPUT),

    /** FastAPI 서버 자체에 연결할 수 없는 경우(DNS·커넥션 거부 등). */
    AI_CONNECTION_FAILED(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** connect 또는 read timeout이 발생한 경우. */
    AI_TIMEOUT(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** FastAPI가 5xx를 반환한 경우(모델 호출 실패 등 서버 내부 오류 포함). */
    AI_SERVER_ERROR(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** 응답 바디가 비어 있는 경우. */
    AI_EMPTY_RESPONSE(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** 응답이 유효한 JSON으로 파싱되지 않는 경우. */
    AI_INVALID_JSON(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** {@code items} 배열이 비어 있는 경우. */
    AI_EMPTY_ITEMS(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** 항목에 필수 필드(category 등)가 누락된 경우. */
    AI_MISSING_FIELD(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** 항목의 title이 비어 있는 경우. */
    AI_BLANK_TITLE(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** 같은 체크리스트 안에서 displayOrder가 중복되는 경우. */
    AI_DUPLICATE_DISPLAY_ORDER(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** FastAPI 쪽 Pydantic/JSON Schema 검증에 실패했다고 알려온 경우. */
    AI_SCHEMA_VALIDATION_FAILED(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** classpath 카탈로그 로딩·검증에 실패한 경우(AI-002-1). */
    CATALOG_UNAVAILABLE(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** 하드 필터 이후 후보 수가 부족한 경우(AI-002-1). */
    CATALOG_INSUFFICIENT_CANDIDATES(FallbackCategory.TECHNICAL_AI_FAILURE),

    /** 카탈로그 fallback 조립까지 실패한 경우(AI-002-1). */
    CATALOG_FALLBACK_FAILED(FallbackCategory.TECHNICAL_AI_FAILURE);

    private final FallbackCategory category;

    ChecklistFallbackReason(FallbackCategory category) {
        this.category = category;
    }

    public boolean isPersonalizationInputCause() {
        return category == FallbackCategory.PERSONALIZATION_INPUT;
    }

    public boolean isTechnicalAiFailure() {
        return category == FallbackCategory.TECHNICAL_AI_FAILURE;
    }

    private enum FallbackCategory {
        PERSONALIZATION_INPUT,
        TECHNICAL_AI_FAILURE
    }
}
