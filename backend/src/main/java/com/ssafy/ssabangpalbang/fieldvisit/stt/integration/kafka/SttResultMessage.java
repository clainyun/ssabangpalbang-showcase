package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.kafka;

import java.time.OffsetDateTime;

public record SttResultMessage(
        Integer schemaVersion,
        String sttId,
        Integer attemptNo,
        SttResultStatus status,
        OffsetDateTime startedAt,
        String textContent,
        OffsetDateTime completedAt,
        String failCode,
        String failReason,
        Boolean retryable,
        OffsetDateTime failedAt
) {
}
