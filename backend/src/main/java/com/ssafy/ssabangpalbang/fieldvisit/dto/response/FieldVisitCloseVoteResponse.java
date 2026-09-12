package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전체 임장 종료 요청(과반수 투표) 응답")
public record FieldVisitCloseVoteResponse(
        Long studyId,
        Long sessionId,
        int startedParticipantCount,
        int voteCount,
        int requiredVoteCount,
        boolean hasVoted,
        boolean canVote,
        boolean sessionEnded,
        String sessionEndReason,
        boolean reportTriggered,
        Long reportId,
        String reportStatus
) {
}
