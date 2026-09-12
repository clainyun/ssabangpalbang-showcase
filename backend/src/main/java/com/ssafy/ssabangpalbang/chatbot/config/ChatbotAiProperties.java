package com.ssafy.ssabangpalbang.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ssabangpalbang.chatbot.ai")
public class ChatbotAiProperties {

    /**
     * FastAPI base URL. 로컬 기본값 {@code http://localhost:8000},
     * Docker Compose {@code http://ai:8000}.
     * {@code CHATBOT_AI_BASE_URL}로 덮어쓴다.
     */
    private String baseUrl = "http://localhost:8000";

    private String answerPath = "/internal/v1/chatbot/answers";

    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 리포트 벡터 검색 + LLM 호출이라 체크리스트(20s)보다 길어야 한다. */
    private Duration readTimeout = Duration.ofSeconds(60);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAnswerPath() {
        return answerPath;
    }

    public void setAnswerPath(String answerPath) {
        this.answerPath = answerPath;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String answerPath() {
        return answerPath;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }
}
