package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;

/** FastAPI 서버에 연결할 수 없는 경우(DNS 실패, 커넥션 거부 등). */
public class ChecklistAiConnectionException extends ChecklistAiException {

    public ChecklistAiConnectionException(String message) {
        super(ChecklistFallbackReason.AI_CONNECTION_FAILED, message);
    }

    public ChecklistAiConnectionException(String message, Throwable cause) {
        super(ChecklistFallbackReason.AI_CONNECTION_FAILED, message, cause);
    }
}
