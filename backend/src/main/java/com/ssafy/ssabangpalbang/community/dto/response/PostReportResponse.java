package com.ssafy.ssabangpalbang.community.dto.response;

import com.ssafy.ssabangpalbang.report.domain.ReportStatus;

public record PostReportResponse(
        Long reportId,
        ReportStatus status,
        boolean reportAvailable
) {
}
