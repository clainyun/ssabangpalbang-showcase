package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record ChecklistAnswerSaveResponse(
        Long studyId,
        Long checklistId,
        int savedCount,
        int completedCount,
        int totalCount,
        List<AnswerStatus> answers,
        List<CategoryProgress> categoryProgress
) {
    public record AnswerStatus(
            Long checklistItemId,
            boolean isCompleted,
            OffsetDateTime completedAt
    ) {
    }

    public record CategoryProgress(
            String category,
            int completedCount,
            int totalCount
    ) {
    }
}
