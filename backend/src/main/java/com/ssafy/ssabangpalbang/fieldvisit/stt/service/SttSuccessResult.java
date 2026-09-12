package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import java.time.OffsetDateTime;

public record SttSuccessResult(
        String sttId,
        int attemptNo,
        String textContent,
        OffsetDateTime completedAt
) {
}
