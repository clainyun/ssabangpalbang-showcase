package com.ssafy.ssabangpalbang.chatbot.client;

import com.ssafy.ssabangpalbang.chatbot.config.ChatbotAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * FastAPI {@code POST /internal/v1/chatbot/answers} RestClient 구현체다.
 * 중복 답변이 더 나쁘므로 자동 재시도하지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class RestChatbotAiClient implements ChatbotAiClient {

    private final RestClient restClient;
    private final ChatbotAiProperties properties;

    @Override
    public ChatbotAiAnswerResponse generateAnswer(
            ChatbotAiAnswerRequest request
    ) {
        try {
            ChatbotAiAnswerResponse response = restClient.post()
                    .uri(properties.answerPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatbotAiAnswerResponse.class);

            if (response == null) {
                throw new ChatbotAiException("AI 서비스가 빈 응답을 반환했습니다.");
            }
            return response;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ChatbotAiException(
                    "AI 서비스가 HTTP "
                            + status
                            + "를 반환했습니다.",
                    exception,
                    exception.getStatusCode().is5xxServerError()
                            || status == 408
                            || status == 429
            );
        } catch (ResourceAccessException exception) {
            throw new ChatbotAiException(
                    "AI 서비스에 연결하지 못했습니다.",
                    exception,
                    true
            );
        }
    }
}
