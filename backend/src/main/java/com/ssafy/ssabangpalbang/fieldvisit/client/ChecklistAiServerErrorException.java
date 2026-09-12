package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;

/** FastAPI가 5xx를 반환한 경우(외부 LLM 실패로 인한 서버 내부 오류 포함). */
public class ChecklistAiServerErrorException extends ChecklistAiException {

    private final int statusCode;

    public ChecklistAiServerErrorException(int statusCode, String message) {
        super(ChecklistFallbackReason.AI_SERVER_ERROR, message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
