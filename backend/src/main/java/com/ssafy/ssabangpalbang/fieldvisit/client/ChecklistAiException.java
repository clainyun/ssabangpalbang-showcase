package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;

/**
 * {@link ChecklistAiClient} 호출 중 발생하는 기술적 실패를 나타내는 공통
 * 상위 예외다. 모든 하위 타입은 {@link ChecklistFallbackReason}의 기술적
 * 실패 사유 중 하나와 1:1로 대응한다.
 */
public abstract class ChecklistAiException extends RuntimeException {

    private final ChecklistFallbackReason reason;

    protected ChecklistAiException(
            ChecklistFallbackReason reason,
            String message
    ) {
        super(message);
        this.reason = reason;
    }

    protected ChecklistAiException(
            ChecklistFallbackReason reason,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.reason = reason;
    }

    public ChecklistFallbackReason getReason() {
        return reason;
    }
}
