package com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record SttStatusResponse(
        String sttId,
        Long studyId,
        Long checklistItemId,
        SttStatus status,
        @Schema(nullable = true)
        Long sourceId,
        @Schema(nullable = true)
        String textContent,
        @Schema(nullable = true)
        String failReason,
        boolean retryable,
        OffsetDateTime requestedAt,
        @Schema(nullable = true)
        OffsetDateTime completedAt
) {
}
