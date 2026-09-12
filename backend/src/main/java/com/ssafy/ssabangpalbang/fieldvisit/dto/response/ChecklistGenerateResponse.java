package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * POST /field-visit/checklist/generate 응답 data다.
 */
public record ChecklistGenerateResponse(
        Long studyId,
        Long sessionId,
        Long checklistId,
        boolean isFallback,
        OffsetDateTime generatedAt,
        int completedCount,
        int totalCount,
        List<GenerateCategoryResponse> categories
) {

    public record GenerateCategoryResponse(
            String category,
            int itemCount,
            List<GenerateItemResponse> items
    ) {
    }

    public record GenerateItemResponse(
            Long checklistItemId,
            String title,
            String subtitle,
            int displayOrder,
            boolean isCompleted,
            OffsetDateTime completedAt,
            int recordCount
    ) {
    }
}
