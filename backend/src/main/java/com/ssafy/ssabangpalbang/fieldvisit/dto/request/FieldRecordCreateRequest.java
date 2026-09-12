package com.ssafy.ssabangpalbang.fieldvisit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "현장 기록 생성 요청")
public record FieldRecordCreateRequest(
        @NotNull(message = "checklistItemId는 필수입니다.")
        @Positive(message = "checklistItemId는 1 이상이어야 합니다.")
        @Schema(description = "본인 체크리스트 항목 ID", minimum = "1", example = "501")
        Long checklistItemId,

        @NotBlank(message = "sourceType은 필수입니다.")
        @Schema(description = "기록 유형", allowableValues = {"TEXT", "PHOTO"}, example = "TEXT")
        String sourceType,

        @Size(max = 2000, message = "textContent는 2000자 이하여야 합니다.")
        @Schema(description = "TEXT일 때 본문", maxLength = 2000, nullable = true)
        String textContent,

        @Positive(message = "photoFileId는 1 이상이어야 합니다.")
        @Schema(description = "PHOTO일 때 FileMeta ID", minimum = "1", nullable = true)
        Long photoFileId,

        @NotBlank(message = "clientRequestId는 필수입니다.")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "clientRequestId는 UUID 형식이어야 합니다."
        )
        @Size(max = 100, message = "clientRequestId는 100자 이하여야 합니다.")
        @Schema(description = "멱등 키", format = "uuid", maxLength = 100)
        String clientRequestId
) {
}
