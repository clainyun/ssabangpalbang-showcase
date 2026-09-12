package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateResponse;

/**
 * AI 또는 fallback으로 확정된 체크리스트 항목 묶음이다.
 */
public record GeneratedChecklistContent(
        ChecklistAiGenerateResponse response,
        boolean fallback,
        ChecklistFallbackReason fallbackReason
) {

    public static GeneratedChecklistContent ai(ChecklistAiGenerateResponse response) {
        return new GeneratedChecklistContent(response, false, null);
    }

    public static GeneratedChecklistContent fallback(
            ChecklistAiGenerateResponse response,
            ChecklistFallbackReason reason
    ) {
        return new GeneratedChecklistContent(response, true, reason);
    }
}
