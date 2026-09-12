package com.ssafy.ssabangpalbang.chatbot.client;

/**
 * FastAPI {@code POST /internal/v1/chatbot/answers} 요청 본문이다.
 * 필드 이름은 {@code contracts/ai-009/chatbot_answer_request.json}과 일치해야 한다.
 */
public record ChatbotAiAnswerRequest(
        Long apartmentId,
        String question,
        String apartmentName,
        Long conversationId,
        Long messageId
) {
}
