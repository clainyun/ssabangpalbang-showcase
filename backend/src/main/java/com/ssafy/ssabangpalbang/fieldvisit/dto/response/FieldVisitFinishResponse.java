package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "개인 임장 종료 응답")
public record FieldVisitFinishResponse(
        Long studyId,
        Long sessionId,
        ParticipantBody participant,
        ChecklistBody checklist,
        boolean sessionEnded,
        String sessionEndReason,
        boolean reportTriggered,
        Long reportId
) {
    @Schema(description = "종료된 참여자 정보")
    public record ParticipantBody(
            Long participantId,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            String endReason,
            Integer stayDurationSec
    ) {
    }

    @Schema(description = "본인 체크리스트 진행 현황")
    public record ChecklistBody(
            int completedCount,
            int totalCount,
            int incompleteCount
    ) {
    }
}
