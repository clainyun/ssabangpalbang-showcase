package com.ssafy.ssabangpalbang.report.dto.response;

import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record ReportRetryResponse(
        @Schema(example = "48")
        Long reportId,

        @Schema(example = "PENDING", allowableValues = {
                "PENDING", "IN_PROGRESS"
        })
        ReportStatus status,

        @Schema(minimum = "0", maximum = "100", example = "0")
        int progressRate,

        @Schema(example = "RECORD_COLLECTION", allowableValues = {
                "RECORD_COLLECTION", "STT_VALIDATION", "NORMALIZATION",
                "REPORT_GENERATION", "EVIDENCE_MAPPING", "RESULT_SAVING"
        })
        String progressStage,

        OffsetDateTime retryRequestedAt,

        @Schema(example = "/api/v1/reports/48/status")
        String statusApi
) {
}
