package com.ssafy.ssabangpalbang.fieldvisit.integration;

import java.time.Instant;
import java.util.List;

public record FieldVisitSessionStartedEvent(
        Long studyId,
        Long sessionId,
        Long starterId,
        String studyTitle,
        Instant startedAt,
        List<Long> recipientIds
) {
    public FieldVisitSessionStartedEvent {
        recipientIds = List.copyOf(recipientIds);
    }
}
