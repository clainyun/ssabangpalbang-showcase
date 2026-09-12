package com.ssafy.ssabangpalbang.chatbot.client;

/**
 * AI 답변 호출의 기술적 실패다. 202를 이미 보낸 뒤이므로 HTTP로 알릴 수 없고,
 * 워커가 메시지를 FAILED로 저장하는 신호로만 쓴다.
 */
public class ChatbotAiException extends RuntimeException {

    private final boolean retryable;

    public ChatbotAiException(String message) {
        this(message, null, false);
    }

    public ChatbotAiException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public ChatbotAiException(
            String message,
            Throwable cause,
            boolean retryable
    ) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
