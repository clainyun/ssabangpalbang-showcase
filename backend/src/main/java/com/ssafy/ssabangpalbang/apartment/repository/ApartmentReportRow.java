package com.ssafy.ssabangpalbang.apartment.repository;

import java.time.Instant;

public record ApartmentReportRow(
        Long reportId,
        String resultJson,
        Instant completedAt,
        boolean favoritedByMe
) {
}
