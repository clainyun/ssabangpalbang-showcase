package com.ssafy.ssabangpalbang.study.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StudyNoticeUpdateRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "공지 내용은 공백일 수 없습니다.")
        @Size(max = 2000)
        String content
) {
    public StudyNoticeUpdateRequest {
        if (content != null) {
            content = content.strip();
        }
    }
}
