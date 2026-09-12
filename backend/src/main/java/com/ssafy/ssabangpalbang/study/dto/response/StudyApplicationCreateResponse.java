package com.ssafy.ssabangpalbang.study.dto.response;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.study.domain.StudyApplication;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record StudyApplicationCreateResponse(
        Long applicationId,
        Long studyId,
        ApplicantSummary applicant,
        String intro,
        String purpose,
        String status,
        OffsetDateTime createdAt
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static StudyApplicationCreateResponse from(
            StudyApplication application,
            Member member
    ) {
        return new StudyApplicationCreateResponse(
                application.getId(),
                application.getStudyId(),
                new ApplicantSummary(
                        member.getId(),
                        member.getNickname(),
                        member.getSelectedCharacterId()
                ),
                application.getIntro(),
                application.getPurpose(),
                application.getStatus().name(),
                application.getCreatedAt().atZone(SEOUL).toOffsetDateTime()
        );
    }

    public record ApplicantSummary(
            Long memberId,
            String nickname,
            String selectedCharacterId
    ) {
    }
}
