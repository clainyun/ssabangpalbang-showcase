package com.ssafy.ssabangpalbang.chatbot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code @Size}는 trim 이전 길이를 본다. 정본은 「앞뒤 공백 제거 후 1~1000자」이므로
 * Service가 trim 후 길이를 다시 검증한다.
 */
public record ChatbotMessageCreateRequest(
        @NotBlank(message = "질문 내용을 입력해 주세요.")
        @Size(max = 1000, message = "질문은 1000자 이하여야 합니다.")
        String content
) {
}
