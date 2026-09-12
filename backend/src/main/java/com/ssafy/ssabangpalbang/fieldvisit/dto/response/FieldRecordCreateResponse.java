package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * POST /field-visit/records 응답 data다.
 * 목록 항목과 동일한 record 필드를 중첩하고, itemRecordCount를 함께 반환한다.
 */
@Schema(description = "현장 기록 생성 응답")
public record FieldRecordCreateResponse(
        Long studyId,
        Long sessionId,
        RecordBody record,
        Integer itemRecordCount
) {
    @Schema(description = "생성된 현장 기록")
    public record RecordBody(
            Long sourceId,
            Long checklistItemId,
            String sourceType,
            String textContent,
            PhotoInfo photo,
            String sttStatus,
            FieldRecordListResponse.Author author,
            boolean isMine,
            boolean canEdit,
            boolean canDelete,
            String clientRequestId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    @Schema(description = "사진 파일 접근 정보")
    public record PhotoInfo(
            Long fileId,
            String originalName,
            String contentType,
            String fileUrl,
            boolean available,
            OffsetDateTime expiresAt
    ) {
    }
}
