package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "임장 세션 상태 조회 응답")
public record FieldVisitStatusResponse(
        Long studyId,
        String status,
        SessionBody session,
        ParticipantBody participant,
        ChecklistProgressBody checklistProgress,
        PermissionsBody permissions,
        FieldVisitCloseVoteStatusBody closeVote
) {
    @Schema(description = "세션 정보. 미시작이면 null")
    public record SessionBody(
            Long sessionId,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            Long endedByMemberId,
            String endReason,
            Integer elapsedSeconds,
            @Schema(description = "현재 임장을 진행 중(IN_PROGRESS)인 참여자 수")
            int activeParticipantCount,
            @Schema(description = "임장을 종료(ENDED)한 참여자 수")
            int endedParticipantCount
    ) {
    }

    @Schema(description = "본인 참여 정보. 미참여이면 null")
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
    public record ChecklistProgressBody(
            boolean generated,
            Long checklistId,
            int completedCount,
            int totalCount,
            int recordCount
    ) {
    }

    @Schema(description = "현재 사용자 임장 기능 권한")
    public record PermissionsBody(
            boolean canStart,
            boolean canEditChecklist,
            boolean canCreateRecord,
            boolean canFinish,
            boolean canCloseSession
    ) {
    }
}
