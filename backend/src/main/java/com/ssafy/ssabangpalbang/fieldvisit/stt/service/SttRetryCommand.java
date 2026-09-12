package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SttRetryCommand(
        Long memberId,
        Long studyId,
        String sttId,
        UUID clientRequestId,
        OffsetDateTime requestedAt
) {
}
