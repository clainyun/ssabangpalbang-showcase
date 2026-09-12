package com.ssafy.ssabangpalbang.study.dto.response;

import com.ssafy.ssabangpalbang.study.domain.Study;

public record StudyUpdateResponse(
        Long studyId,
        String title,
        String intro,
        String goal
) {
    public static StudyUpdateResponse from(Study study) {
        return new StudyUpdateResponse(
                study.getId(),
                study.getTitle(),
                study.getIntro(),
                study.getGoal()
        );
    }
}
