package com.ssafy.ssabangpalbang.report.client;

public record ReportRagIndexResponse(
        Long reportId,
        Long apartmentId,
        boolean indexed,
        int chunkCount,
        int deletedCount,
        String skipReason
) {
}
