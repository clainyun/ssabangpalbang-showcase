package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;

/**
 * {@link ChecklistAiResponseValidator}의 검증 결과다.
 *
 * <p>{@code valid = false}면 {@code reason}에 항상
 * {@link ChecklistFallbackReason#isTechnicalAiFailure()}가 true인 값이 담긴다.</p>
 */
public record ChecklistAiValidationResult(
        boolean valid,
        ChecklistFallbackReason reason
) {

    public static ChecklistAiValidationResult success() {
        return new ChecklistAiValidationResult(true, null);
    }

    public static ChecklistAiValidationResult failure(ChecklistFallbackReason reason) {
        if (!reason.isTechnicalAiFailure()) {
            throw new IllegalArgumentException(
                    "fallback reason은 기술적 AI 실패 사유여야 합니다: " + reason
            );
        }
        return new ChecklistAiValidationResult(false, reason);
    }
}
