package com.ssafy.ssabangpalbang.study.dto.response;

public record StudyApplicationRejectResponse(
        Long applicationId,
        Long studyId,
        Applicant applicant,
        String status,
        String decidedAt
) {
    public record Applicant(
            Long memberId,
            String nickname,
            String selectedCharacterId
    ) {
    }
}
