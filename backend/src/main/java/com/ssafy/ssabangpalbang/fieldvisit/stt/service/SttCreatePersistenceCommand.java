package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SttCreatePersistenceCommand(
        String sttId,
        Long memberId,
        Long studyId,
        Long sessionId,
        Long audioFileId,
        Long checklistItemId,
        UUID clientRequestId,
        OffsetDateTime requestedAt,
        String objectKey,
        String contentType
) {
}
