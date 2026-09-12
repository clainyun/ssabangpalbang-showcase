package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistPersonalizationInput;

/**
 * {@link ChecklistPersonalizationReader}의 조회 결과다.
 *
 * <p>{@code insufficient = true}면 {@link ChecklistFallbackReason#PERSONALIZATION_INSUFFICIENT}
 * 사유로 fallback을 사용해야 한다는 뜻이다(FastAPI 호출 자체를 생략한다). AI-002
 * 정상 흐름에서는 온보딩이 전부 필수라 이 값이 거의 항상 false여야 하며,
 * true인 경우는 기존 데이터·정합성 문제로 인한 예외적 상황으로 취급한다.</p>
 */
public record ChecklistPersonalizationResult(
        ChecklistPersonalizationInput input,
        boolean insufficient
) {

    public boolean isSufficient() {
        return !insufficient;
    }
}
