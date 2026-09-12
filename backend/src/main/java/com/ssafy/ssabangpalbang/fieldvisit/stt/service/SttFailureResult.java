package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import java.time.OffsetDateTime;

public record SttFailureResult(
        String sttId,
        int attemptNo,
        String failCode,
        String failReason,
        boolean retryable,
        OffsetDateTime failedAt
) {
}
