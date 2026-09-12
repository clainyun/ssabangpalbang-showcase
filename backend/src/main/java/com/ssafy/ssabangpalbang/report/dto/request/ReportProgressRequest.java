package com.ssafy.ssabangpalbang.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "AI Report Worker 진행 단계 저장 요청")
public record ReportProgressRequest(
        @NotBlank
        @Schema(
                description = "처리권 획득 시 한 번만 발급된 불투명 Token",
                example = "opaque-one-time-token"
        )
        String processingToken,

        @NotNull
        @Min(1)
        @Schema(example = "1", minimum = "1")
        Integer processingAttempt,

        @NotNull
        @Schema(example = "NORMALIZATION")
        ReportWorkerProgressStage stage
) {

    @Override
    public String toString() {
        return "ReportProgressRequest[processingToken=[REDACTED], "
                + "processingAttempt=" + processingAttempt
                + ", stage=" + stage + "]";
    }
}
