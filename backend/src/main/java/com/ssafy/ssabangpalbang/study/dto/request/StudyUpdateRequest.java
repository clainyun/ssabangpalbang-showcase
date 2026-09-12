package com.ssafy.ssabangpalbang.study.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StudyUpdateRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "스터디 제목은 공백일 수 없습니다.")
        @Size(max = 200)
        String title,

        @Pattern(regexp = "(?s).*\\S.*", message = "스터디 목표는 공백일 수 없습니다.")
        @Size(max = 300)
        String goal,

        @Size(max = 1000)
        String intro
) {
    public StudyUpdateRequest {
        if (title != null) {
            title = title.strip();
        }
        if (goal != null) {
            goal = goal.strip();
        }
        if (intro != null) {
            intro = intro.strip();
        }
    }
}
