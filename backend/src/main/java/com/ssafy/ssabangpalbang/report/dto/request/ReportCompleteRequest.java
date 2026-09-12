package com.ssafy.ssabangpalbang.report.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "AI Report Worker 결과·근거 완료 저장 요청")
public record ReportCompleteRequest(
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
        @Valid
        ReportGenerationResultRequest generationResult,

        @NotNull
        @Valid
        ReportEvidenceResultRequest evidenceResult
) {

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unexpected report contract field");
    }

    @Override
    public String toString() {
        return "ReportCompleteRequest[processingToken=[REDACTED], "
                + "processingAttempt=" + processingAttempt
                + ", generationResult=[REDACTED], "
                + "evidenceResult=[REDACTED]]";
    }
}
