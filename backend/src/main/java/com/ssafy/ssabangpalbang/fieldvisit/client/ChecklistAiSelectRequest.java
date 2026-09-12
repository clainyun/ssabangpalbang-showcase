package com.ssafy.ssabangpalbang.fieldvisit.client;

import java.util.List;

/**
 * Spring → FastAPI 카탈로그 itemCode 선택 요청(AI-002-1).
 */
public record ChecklistAiSelectRequest(
        String selectionVersion,
        int targetItemCount,
        String mappedPurpose,
        List<String> selectedPriorities,
        List<ShortlistItem> shortlist
) {

    public record ShortlistItem(
            String itemCode,
            String categoryCode,
            String title,
            List<String> priorityTags,
            List<String> conditionTags,
            int serverScore,
            boolean isCommonCore
    ) {
    }
}
