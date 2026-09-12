package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;

/** connect 또는 read timeout이 발생한 경우. */
public class ChecklistAiTimeoutException extends ChecklistAiException {

    public ChecklistAiTimeoutException(String message) {
        super(ChecklistFallbackReason.AI_TIMEOUT, message);
    }

    public ChecklistAiTimeoutException(String message, Throwable cause) {
        super(ChecklistFallbackReason.AI_TIMEOUT, message, cause);
    }
}
