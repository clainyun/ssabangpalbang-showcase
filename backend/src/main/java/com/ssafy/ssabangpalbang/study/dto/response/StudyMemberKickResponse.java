package com.ssafy.ssabangpalbang.study.dto.response;

public record StudyMemberKickResponse(
        Long studyId,
        Long memberId,
        long currentMemberCount,
        int capacity,
        String studyStatus,
        String kickedAt
) {
}
