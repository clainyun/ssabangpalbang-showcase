package com.ssafy.ssabangpalbang.chatbot.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * FastAPI 답변 응답이다. 필드 이름은 {@code contracts/ai-009/}의 응답 픽스처와
 * 일치해야 한다. 백엔드는 {@code basisType}을 재판정하지 않고 그대로 저장한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatbotAiAnswerResponse(
        String basisType,
        String basisLabel,
        String answer,
        List<Source> sources,
        Double topSimilarity,
        Boolean fallbackToWeb
) {

    public List<Source> sourcesOrEmpty() {
        return sources == null ? List.of() : sources;
    }

    public boolean hasAnswer() {
        return answer != null && !answer.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(
            String sourceType,
            Long sourceId,
            Long reportId,
            String title,
            String sectionLabel,
            String url,
            String sourceAt
    ) {
    }
}
