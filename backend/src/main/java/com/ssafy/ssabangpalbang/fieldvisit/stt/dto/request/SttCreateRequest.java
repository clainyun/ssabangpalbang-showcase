package com.ssafy.ssabangpalbang.fieldvisit.stt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SttCreateRequest(
        @NotNull(message = "음성 파일 ID는 필수입니다.")
        @Positive(message = "음성 파일 ID는 1 이상이어야 합니다.")
        @Schema(example = "90")
        Long audioFileId,

        @NotNull(message = "체크리스트 항목 ID는 필수입니다.")
        @Positive(message = "체크리스트 항목 ID는 1 이상이어야 합니다.")
        @Schema(example = "501")
        Long checklistItemId,

        @NotBlank(message = "clientRequestId는 필수입니다.")
        @Schema(
                description = "중복 요청 방지용 UUID",
                example = "81197c8f-780b-40c2-abf6-b83473de9c82"
        )
        String clientRequestId
) {
}
