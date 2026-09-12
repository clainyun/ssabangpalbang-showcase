package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "현장 기록 수정·삭제 응답")
public record FieldRecordMutationResponse(
        Long studyId,
        Long sessionId,
        Long sourceId,
        Long checklistItemId,
        FieldRecordListResponse.Author author,
        String sourceType,
        String textContent,
        FieldRecordCreateResponse.PhotoInfo photo,
        String sttStatus,
        String clientRequestId,
        boolean deleted,
        OffsetDateTime deletedAt,
        Integer itemRecordCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
