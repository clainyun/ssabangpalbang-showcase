package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "스터디장 전체 임장 마감 응답")
public record FieldVisitCloseResponse(
        Long studyId,
        SessionBody session,
        List<ForcedEndedParticipantBody> forcedEndedParticipants,
        int forcedEndedCount,
        boolean reportTriggered,
        Long reportId,
        String reportStatus
) {
    @Schema(description = "종료된 세션 정보")
    public record SessionBody(
            Long sessionId,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            Long endedByMemberId,
            String endReason
    ) {
    }

    @Schema(description = "이번 요청으로 강제 종료된 참여자")
    public record ForcedEndedParticipantBody(
            Long participantId,
            Long memberId,
            String endReason,
            OffsetDateTime endedAt,
            Integer stayDurationSec
    ) {
    }
}
