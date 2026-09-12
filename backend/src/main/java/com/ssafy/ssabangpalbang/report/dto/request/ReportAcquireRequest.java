package com.ssafy.ssabangpalbang.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

@Schema(description = "AI Report Worker 처리권 획득 요청")
public record ReportAcquireRequest(
        @NotNull
        @Positive
        @Schema(example = "7", minimum = "1")
        Long studyId,

        @NotNull
        @Positive
        @Schema(example = "3", minimum = "1")
        Long sessionId,

        @NotNull
        @Positive
        @Schema(example = "100", minimum = "1")
        Long apartmentId,

        @NotNull
        @Schema(
                description = "Offset이 포함된 리포트 요청 이벤트 발생 시각",
                example = "2026-08-02T12:30:00+09:00",
                type = "string",
                format = "date-time"
        )
        OffsetDateTime occurredAt
) {
}
