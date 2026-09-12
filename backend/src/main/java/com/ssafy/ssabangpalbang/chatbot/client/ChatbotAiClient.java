package com.ssafy.ssabangpalbang.chatbot.client;

/**
 * 챗봇 답변 생성 포트다. 전송 방식(HTTP)을 서비스 계층에서 감춘다.
 */
public interface ChatbotAiClient {

    ChatbotAiAnswerResponse generateAnswer(ChatbotAiAnswerRequest request);
}
