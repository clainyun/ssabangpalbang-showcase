package com.ssafy.ssabangpalbang.study.dto.response;

public record StudyCancelResponse(
        Long studyId,
        String status,
        String canceledAt
) {
}
