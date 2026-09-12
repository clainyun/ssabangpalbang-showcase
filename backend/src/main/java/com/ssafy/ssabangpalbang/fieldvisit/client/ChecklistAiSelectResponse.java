package com.ssafy.ssabangpalbang.fieldvisit.client;

import java.util.List;

/**
 * FastAPI → Spring 카탈로그 itemCode 선택 응답(AI-002-1).
 */
public record ChecklistAiSelectResponse(
        List<String> itemCodes
) {
}
