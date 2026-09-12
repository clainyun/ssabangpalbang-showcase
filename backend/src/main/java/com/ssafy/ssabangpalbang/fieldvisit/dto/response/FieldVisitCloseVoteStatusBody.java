package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "임장 상태 조회의 과반수 종료 투표 현황")
public record FieldVisitCloseVoteStatusBody(
        int startedParticipantCount,
        int voteCount,
        int requiredVoteCount,
        boolean hasVoted,
        boolean canVote
) {
}
