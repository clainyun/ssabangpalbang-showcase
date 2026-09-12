package com.ssafy.ssabangpalbang.fieldvisit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "현장 기록 수정 요청. null 필드는 미수정.")
public record FieldRecordUpdateRequest(
        @Positive(message = "checklistItemId는 1 이상이어야 합니다.")
        @Schema(description = "이동할 체크리스트 항목 ID", minimum = "1", nullable = true)
        Long checklistItemId,

        @Size(max = 2000, message = "textContent는 2000자 이하여야 합니다.")
        @Schema(description = "TEXT/STT 본문. 공백만이면 400", maxLength = 2000, nullable = true)
        String textContent,

        @Positive(message = "photoFileId는 1 이상이어야 합니다.")
        @Schema(description = "교체할 PHOTO FileMeta ID", minimum = "1", nullable = true)
        Long photoFileId
) {
}
