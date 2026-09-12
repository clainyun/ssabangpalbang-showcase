package com.ssafy.ssabangpalbang.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudyNoticeCreateRequest(
        @NotBlank
        @Size(max = 2000)
        String content
) {
    public StudyNoticeCreateRequest {
        if (content != null) {
            content = content.strip();
        }
    }
}
