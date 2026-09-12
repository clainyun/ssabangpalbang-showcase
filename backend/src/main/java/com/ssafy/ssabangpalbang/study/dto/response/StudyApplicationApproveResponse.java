package com.ssafy.ssabangpalbang.study.dto.response;

public record StudyApplicationApproveResponse(
        Long applicationId,
        Long studyId,
        Applicant applicant,
        String status,
        long currentMemberCount,
        int capacity,
        String studyStatus,
        String decidedAt
) {
    public record Applicant(
            Long memberId,
            String nickname,
            String profileImageUrl,
            String selectedCharacterId
    ) {
    }
}
