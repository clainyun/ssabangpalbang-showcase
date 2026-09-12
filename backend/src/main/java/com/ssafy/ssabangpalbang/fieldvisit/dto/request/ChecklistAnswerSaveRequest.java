package com.ssafy.ssabangpalbang.fieldvisit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

@Schema(description = "체크리스트 완료 상태 일괄 저장 요청")
public record ChecklistAnswerSaveRequest(
        @NotEmpty(message = "answers는 1개 이상이어야 합니다.")
        @Valid
        @Schema(description = "저장할 항목 목록", minLength = 1)
        List<AnswerItem> answers
) {
    @Schema(description = "항목별 완료 상태")
    public record AnswerItem(
            @NotNull(message = "checklistItemId는 필수입니다.")
            @Positive(message = "checklistItemId는 1 이상이어야 합니다.")
            @Schema(minimum = "1", example = "501")
            Long checklistItemId,

            @NotNull(message = "isCompleted는 필수입니다.")
            @Schema(example = "true")
            Boolean isCompleted
    ) {
    }
}
