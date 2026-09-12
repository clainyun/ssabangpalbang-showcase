package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import java.time.OffsetDateTime;

public record SttProcessingResult(
        String sttId,
        int attemptNo,
        OffsetDateTime startedAt
) {
}
