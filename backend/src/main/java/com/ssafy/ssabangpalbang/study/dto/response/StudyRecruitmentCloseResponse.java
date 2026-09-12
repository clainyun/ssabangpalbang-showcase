package com.ssafy.ssabangpalbang.study.dto.response;

public record StudyRecruitmentCloseResponse(
        Long studyId,
        String status,
        long currentMemberCount,
        int capacity,
        long pendingApplicationCount,
        String recruitmentClosedAt
) {
}
