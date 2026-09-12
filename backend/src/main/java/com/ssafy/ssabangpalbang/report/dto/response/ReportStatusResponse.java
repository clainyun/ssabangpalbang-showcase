package com.ssafy.ssabangpalbang.report.dto.response;

import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record ReportStatusResponse(
        @Schema(example = "48")
        Long reportId,
        @Schema(example = "IN_PROGRESS", allowableValues = {
                "PENDING", "IN_PROGRESS", "DONE", "FAILED"
        })
        ReportStatus status,
        @Schema(minimum = "0", maximum = "100", example = "70")
        int progressRate,
        @Schema(example = "REPORT_GENERATION", allowableValues = {
                "RECORD_COLLECTION", "STT_VALIDATION", "NORMALIZATION",
                "REPORT_GENERATION", "EVIDENCE_MAPPING", "RESULT_SAVING",
                "COMPLETED"
        })
        String progressStage,
        @Schema(example = "참여자들의 현장 의견을 분석하고 있습니다.")
        String progressMessage,
        @Schema(example = "false")
        boolean detailAvailable,
        @Schema(example = "false")
        boolean retryAvailable,
        @Schema(nullable = true)
        String failReason,
        OffsetDateTime createdAt,
        @Schema(nullable = true)
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {
}
