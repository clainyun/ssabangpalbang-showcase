package com.ssafy.ssabangpalbang.report.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "AI Report Worker 실패 정보 저장 요청")
public record ReportFailRequest(
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
        @Schema(
                description = "실패가 확정된 Worker 진행 단계",
                example = "NORMALIZATION"
        )
        ReportWorkerProgressStage failedStage,

        @NotNull
        @Schema(example = "NORMALIZATION_FAILED", maxLength = 100)
        ReportWorkerErrorCode errorCode,

        @NotBlank
        @Size(min = 1, max = 500)
        @Schema(
                description = "errorCode에 대응하는 고정 안전 문구",
                example = "리포트 입력 정규화에 실패했습니다.",
                maxLength = 500
        )
        String message,

        @NotNull
        @Schema(example = "false")
        Boolean retryable
) {

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unexpected report contract field");
    }

    @Override
    public String toString() {
        return "ReportFailRequest[processingToken=[REDACTED], "
                + "processingAttempt=" + processingAttempt
                + ", failedStage=" + failedStage
                + ", errorCode=" + errorCode
                + ", message=[REDACTED], retryable=" + retryable + "]";
    }
}
