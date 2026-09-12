package com.ssafy.ssabangpalbang.fieldvisit.integration;

import java.time.Instant;

public record ReportRequestedEvent(
        Long studyId,
        Long sessionId,
        Long apartmentId,
        Instant occurredAt
) {
}
