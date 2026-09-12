package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record FieldRecordListResponse(
        Long studyId,
        Long sessionId,
        boolean readOnly,
        ChecklistItemSummary checklistItem,
        List<RecordItem> content,
        String nextCursor,
        boolean hasNext
) {
    public record ChecklistItemSummary(
            Long checklistItemId,
            String category,
            String title,
            String subtitle
    ) {
    }

    public record RecordItem(
            Long sourceId,
            Long checklistItemId,
            String sourceType,
            String textContent,
            FieldRecordCreateResponse.PhotoInfo photo,
            String sttStatus,
            Author author,
            boolean isMine,
            boolean canEdit,
            boolean canDelete,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record Author(
            Long memberId,
            String nickname,
            String selectedCharacterId
    ) {
    }
}
