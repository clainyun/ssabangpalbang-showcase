package com.ssafy.ssabangpalbang.chatbot.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiAnswerRequest;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiAnswerResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java ↔ FastAPI 공유 JSON fixture 계약 테스트다.
 *
 * <p>Jenkins는 {@code backend} Docker context만 COPY하므로 저장소 루트
 * {@code contracts/}에 의존하지 않는다. Java는 classpath 복사본을 읽고,
 * 루트 canonical fixture와의 동등성은 Python 테스트에서 SHA-256으로 검증한다.</p>
 */
class ChatbotAiContractFixtureTest {

    private static final String CONTRACTS = "contracts/ai-009/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private <T> T read(String name, Class<T> type) throws IOException {
        String location = CONTRACTS + name;
        ClassPathResource resource = new ClassPathResource(location);
        assertThat(resource.exists())
                .as("classpath fixture가 없습니다: " + location)
                .isTrue();
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, type);
        }
    }

    @Test
    void f1_요청_픽스처가_역직렬화된다() throws IOException {
        ChatbotAiAnswerRequest request = read(
                "chatbot_answer_request.json", ChatbotAiAnswerRequest.class
        );

        assertThat(request.apartmentId()).isEqualTo(15L);
        assertThat(request.question()).isEqualTo("교통 어때요?");
        assertThat(request.apartmentName()).isEqualTo("래미안 옥수 리버젠");
        assertThat(request.conversationId()).isEqualTo(41L);
    }

    @Test
    void f2_요청_픽스처를_다시_직렬화해도_필드가_유지된다() throws Exception {
        ChatbotAiAnswerRequest request = read(
                "chatbot_answer_request.json", ChatbotAiAnswerRequest.class
        );

        String json = objectMapper.writeValueAsString(request);

        assertThat(json)
                .contains("\"apartmentId\"")
                .contains("\"question\"")
                .contains("\"apartmentName\"")
                .contains("\"conversationId\"")
                .contains("\"messageId\"");
    }

    @Test
    void f3_리포트_기반_응답이_역직렬화된다() throws IOException {
        ChatbotAiAnswerResponse response = read(
                "chatbot_answer_response_report.json",
                ChatbotAiAnswerResponse.class
        );

        assertThat(response.basisType()).isEqualTo("REPORT");
        assertThat(response.basisLabel()).isEqualTo("리포트 기반");
        assertThat(response.hasAnswer()).isTrue();
        assertThat(response.sourcesOrEmpty()).hasSize(1);
        assertThat(response.sourcesOrEmpty().get(0).reportId()).isEqualTo(48L);
    }

    @Test
    void f4_웹_기반_응답이_역직렬화된다() throws IOException {
        ChatbotAiAnswerResponse response = read(
                "chatbot_answer_response_web.json",
                ChatbotAiAnswerResponse.class
        );

        assertThat(response.basisType()).isEqualTo("WEB");
        assertThat(response.basisLabel()).isEqualTo("웹 기반");
        assertThat(response.sourcesOrEmpty().get(0).sourceType())
                .isEqualTo("WEB");
        assertThat(response.sourcesOrEmpty().get(0).sourceId()).isNull();
        assertThat(response.sourcesOrEmpty().get(0).url()).isNotNull();
    }

    @Test
    void f5_리포트_전환_응답이_역직렬화된다() throws IOException {
        ChatbotAiAnswerResponse response = read(
                "chatbot_answer_response_fallback.json",
                ChatbotAiAnswerResponse.class
        );

        assertThat(response.fallbackToWeb()).isTrue();
        assertThat(response.hasAnswer()).isFalse();
        assertThat(response.sourcesOrEmpty()).isEmpty();
    }

    @Test
    void f7_정보_부족_응답은_basisType_NONE이고_라벨이_null이다()
            throws IOException {
        ChatbotAiAnswerResponse response = read(
                "chatbot_answer_response_none.json",
                ChatbotAiAnswerResponse.class
        );

        assertThat(response.basisType()).isEqualTo("NONE");
        assertThat(response.basisLabel()).isNull();
        assertThat(response.hasAnswer()).isTrue();
        assertThat(response.sourcesOrEmpty()).isEmpty();
    }
}
