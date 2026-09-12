package com.ssafy.ssabangpalbang.study.dto.response;

public record StudyMemberLeaveResponse(
        Long studyId,
        Long memberId,
        long currentMemberCount,
        int capacity,
        String studyStatus,
        String leftAt
) {
}
