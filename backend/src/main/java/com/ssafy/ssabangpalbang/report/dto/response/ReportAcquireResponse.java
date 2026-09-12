package com.ssafy.ssabangpalbang.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "AI Report Worker 처리권 판정 결과")
public record ReportAcquireResponse(
        @Schema(
                example = "ACQUIRED",
                allowableValues = {
                        "ACQUIRED",
                        "ALREADY_COMPLETED",
                        "ALREADY_PROCESSING",
                        "ALREADY_FAILED",
                        "CONTRACT_CONFLICT"
                }
        )
        ReportAcquireStatus status,
        @Schema(nullable = true, example = "48")
        Long reportId,
        @Schema(
                description = "ACQUIRED일 때 한 번만 반환하는 불투명 처리 Token",
                nullable = true,
                accessMode = Schema.AccessMode.READ_ONLY
        )
        String processingToken,
        @Schema(nullable = true, minimum = "1", example = "1")
        Integer processingAttempt,
        @Schema(nullable = true, example = "2026-08-02T13:00:00+09:00")
        OffsetDateTime leaseExpiresAt,
        @Schema(nullable = true, minimum = "1", example = "120")
        Long retryAfterSeconds
) {

    public static ReportAcquireResponse acquired(
            Long reportId,
            String processingToken,
            int processingAttempt,
            OffsetDateTime leaseExpiresAt
    ) {
        return new ReportAcquireResponse(
                ReportAcquireStatus.ACQUIRED,
                reportId,
                processingToken,
                processingAttempt,
                leaseExpiresAt,
                null
        );
    }

    public static ReportAcquireResponse alreadyProcessing(
            Long reportId,
            long retryAfterSeconds
    ) {
        return new ReportAcquireResponse(
                ReportAcquireStatus.ALREADY_PROCESSING,
                reportId,
                null,
                null,
                null,
                retryAfterSeconds
        );
    }

    public static ReportAcquireResponse terminal(
            ReportAcquireStatus status,
            Long reportId
    ) {
        return new ReportAcquireResponse(
                status,
                reportId,
                null,
                null,
                null,
                null
        );
    }

    @Override
    public String toString() {
        return "ReportAcquireResponse[status=" + status
                + ", reportId=" + reportId
                + ", processingToken=[REDACTED]"
                + ", processingAttempt=" + processingAttempt
                + ", leaseExpiresAt=" + leaseExpiresAt
                + ", retryAfterSeconds=" + retryAfterSeconds
                + "]";
    }
}
