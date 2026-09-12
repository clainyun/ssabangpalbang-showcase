package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /field-visit/checklist 응답 data다.
 */
public record ChecklistDetailResponse(
        Long studyId,
        Long sessionId,
        String participantStatus,
        boolean readOnly,
        ChecklistBody checklist
) {

    // FieldVisitFinishResponse.ChecklistBody 와 단순 클래스명이 같아 springdoc 스키마 키가
    // 충돌(상세 body 하위 categories/items/example 가 계약에서 누락)하므로 고유 이름을 부여한다.
    @Schema(name = "ChecklistDetailBody")
    public record ChecklistBody(
            Long checklistId,
            boolean isFallback,
            OffsetDateTime generatedAt,
            int completedCount,
            int totalCount,
            List<DetailCategoryResponse> categories
    ) {
    }

    public record DetailCategoryResponse(
            String category,
            int completedCount,
            int totalCount,
            List<DetailItemResponse> items
    ) {
    }

    public record DetailItemResponse(
            Long checklistItemId,
            String title,
            String subtitle,
            String example,
            int displayOrder,
            boolean isCompleted,
            OffsetDateTime completedAt,
            int recordCount,
            RecordSummaryResponse recordSummary
    ) {
    }

    public record RecordSummaryResponse(
            int textCount,
            int photoCount,
            int sttCount
    ) {
        public static RecordSummaryResponse empty() {
            return new RecordSummaryResponse(0, 0, 0);
        }
    }
}
