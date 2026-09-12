package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;

/**
 * 응답이 비어 있거나 유효한 JSON이 아니어서 {@link ChecklistAiGenerateResponse}로
 * 역직렬화 자체가 불가능한 경우다. 역직렬화는 됐지만 내용이 불완전한
 * 경우(빈 items, 빈 title 등)는 {@link ChecklistAiResponseValidator}가 담당한다.
 */
public class ChecklistAiInvalidResponseException extends ChecklistAiException {

    public ChecklistAiInvalidResponseException(
            ChecklistFallbackReason reason,
            String message
    ) {
        super(requireInvalidResponseReason(reason), message);
    }

    public ChecklistAiInvalidResponseException(
            ChecklistFallbackReason reason,
            String message,
            Throwable cause
    ) {
        super(requireInvalidResponseReason(reason), message, cause);
    }

    private static ChecklistFallbackReason requireInvalidResponseReason(
            ChecklistFallbackReason reason
    ) {
        if (reason != ChecklistFallbackReason.AI_EMPTY_RESPONSE
                && reason != ChecklistFallbackReason.AI_INVALID_JSON) {
            throw new IllegalArgumentException(
                    "ChecklistAiInvalidResponseException은 AI_EMPTY_RESPONSE 또는 "
                            + "AI_INVALID_JSON 사유에만 사용할 수 있습니다: " + reason
            );
        }
        return reason;
    }
}
