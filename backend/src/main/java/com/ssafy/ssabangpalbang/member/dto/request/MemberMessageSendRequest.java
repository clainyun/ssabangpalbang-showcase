package com.ssafy.ssabangpalbang.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberMessageSendRequest(
        @Schema(
                description = "전송할 쪽지 내용 (앞뒤 공백 제거 후 1~500자)",
                example = "다음 임장도 같이 참여해요!",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 1,
                maxLength = 500
        )
        String content,
        @Schema(
                description = "동일 요청 재시도에 사용하는 UUID",
                example = "8e70e108-7c81-477a-bb55-a9f334fb5e67",
                requiredMode = Schema.RequiredMode.REQUIRED,
                format = "uuid"
        )
        String clientMessageId
) {
}
