package com.ssafy.ssabangpalbang.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiAnswerResponse;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiClient;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiException;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessage;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageStatus;
import com.ssafy.ssabangpalbang.chatbot.integration.ChatbotAnswerRequestedEvent;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotConversationRepository;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatbotAnswerWriterTest {

    private static final Long CONVERSATION_ID = 41L;
    private static final Long MESSAGE_ID = 54L;
    private static final String ANSWER = "옥수역까지 도보 8분으로 기록돼 있습니다.";

    @Mock
    private ChatbotAiClient aiClient;
    @Mock
    private ChatbotMessageRepository messageRepository;
    @Mock
    private ChatbotConversationRepository conversationRepository;

    private ChatbotAnswerWriter writer;
    private ChatbotMessage message;
    private ChatbotConversation conversation;

    @BeforeEach
    void setUp() {
        writer = new ChatbotAnswerWriter(
                aiClient,
                messageRepository,
                conversationRepository,
                new ObjectMapper()
        );
        message = ChatbotMessage.assistantPlaceholder(CONVERSATION_ID);
        ReflectionTestUtils.setField(message, "id", MESSAGE_ID);
        conversation = ChatbotConversation.start(7L, 15L);
        ReflectionTestUtils.setField(conversation, "id", CONVERSATION_ID);

        when(messageRepository.findPendingAssistantByIdForUpdateSkipLocked(
                MESSAGE_ID
        ))
                .thenReturn(Optional.of(message));
        when(messageRepository.findByIdForUpdate(MESSAGE_ID))
                .thenReturn(Optional.of(message));
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation));
    }

    private ChatbotAnswerRequestedEvent event() {
        return new ChatbotAnswerRequestedEvent(
                CONVERSATION_ID, MESSAGE_ID, 15L, "래미안 옥수 리버젠", "교통 어때요?"
        );
    }

    private ChatbotAiAnswerResponse aiResponse(
            String basisType,
            String basisLabel,
            String answer,
            List<ChatbotAiAnswerResponse.Source> sources
    ) {
        return new ChatbotAiAnswerResponse(
                basisType, basisLabel, answer, sources, 0.72, false
        );
    }

    private ChatbotAiAnswerResponse.Source reportSource() {
        return new ChatbotAiAnswerResponse.Source(
                "REPORT", 48L, 48L, "래미안 옥수 리버젠 임장 리포트",
                null, null, "2026-07-25T16:00:00+09:00"
        );
    }

    @Test
    void b1_워커는_PROCESSING으로_먼저_전이한다() {
        when(aiClient.generateAnswer(any()))
                .thenAnswer(invocation -> {
                    assertThat(message.getStatus())
                            .isEqualTo(ChatbotMessageStatus.PROCESSING);
                    return aiResponse("REPORT", "리포트 기반", ANSWER,
                            List.of(reportSource()));
                });

        writer.generate(event());

        assertThat(message.getStatus())
                .isEqualTo(ChatbotMessageStatus.COMPLETED);
    }

    @Test
    void b2_REPORT_응답을_그대로_저장한다() {
        when(aiClient.generateAnswer(any())).thenReturn(
                aiResponse("REPORT", "리포트 기반", ANSWER, List.of(reportSource()))
        );

        writer.generate(event());

        assertThat(message.getStatus())
                .isEqualTo(ChatbotMessageStatus.COMPLETED);
        assertThat(message.getContent()).isEqualTo(ANSWER);
        assertThat(message.getBasisType()).isEqualTo("REPORT");
        assertThat(message.getBasisLabel()).isEqualTo("리포트 기반");
        assertThat(message.getSourcesJson()).contains("\"reportId\":48");
        assertThat(message.getCompletedAt()).isNotNull();
    }

    @Test
    void b3_WEB_응답을_재판정하지_않고_그대로_저장한다() {
        when(aiClient.generateAnswer(any())).thenReturn(
                aiResponse("WEB", "웹 기반", ANSWER, List.of())
        );

        writer.generate(event());

        assertThat(message.getBasisType()).isEqualTo("WEB");
        assertThat(message.getBasisLabel()).isEqualTo("웹 기반");
    }

    @Test
    void b4_NONE_응답은_basisLabel이_null이다() {
        when(aiClient.generateAnswer(any())).thenReturn(
                aiResponse("NONE", null, "신뢰할 수 있는 자료를 찾지 못했습니다.", List.of())
        );

        writer.generate(event());

        assertThat(message.getBasisType()).isEqualTo("NONE");
        assertThat(message.getBasisLabel()).isNull();
        assertThat(message.getStatus())
                .isEqualTo(ChatbotMessageStatus.COMPLETED);
    }

    @Test
    void b5_AI_타임아웃이면_FAILED와_failReason을_저장한다() {
        when(aiClient.generateAnswer(any()))
                .thenThrow(new ChatbotAiException("AI 서비스에 연결하지 못했습니다."));

        writer.generate(event());

        assertThat(message.getStatus()).isEqualTo(ChatbotMessageStatus.FAILED);
        assertThat(message.getFailReason()).isNotBlank();
        assertThat(message.getCompletedAt()).isNotNull();
    }

    @Test
    void b6_AI_5xx도_같은_처리를_한다() {
        when(aiClient.generateAnswer(any())).thenThrow(
                new ChatbotAiException("AI 서비스가 HTTP 503을 반환했습니다.")
        );

        writer.generate(event());

        assertThat(message.getStatus()).isEqualTo(ChatbotMessageStatus.FAILED);
    }

    @Test
    void 복구_중_AI_일시_장애면_PENDING으로_돌려_다음_시도를_기다린다() {
        when(aiClient.generateAnswer(any())).thenThrow(
                new ChatbotAiException(
                        "AI 서비스가 HTTP 503을 반환했습니다.",
                        null,
                        true
                )
        );

        writer.recover(event());

        assertThat(message.getStatus()).isEqualTo(ChatbotMessageStatus.PENDING);
        assertThat(message.getUpdatedAt()).isNotNull();
        assertThat(message.getFailReason()).isNull();
        assertThat(message.getCompletedAt()).isNull();
    }

    @Test
    void 복구_중_재시도_불가_오류면_FAILED로_종료한다() {
        when(aiClient.generateAnswer(any())).thenThrow(
                new ChatbotAiException("AI 서비스가 HTTP 400을 반환했습니다.")
        );

        writer.recover(event());

        assertThat(message.getStatus()).isEqualTo(ChatbotMessageStatus.FAILED);
    }

    @Test
    void b7_빈_answer는_FAILED로_저장한다() {
        when(aiClient.generateAnswer(any())).thenReturn(
                aiResponse("WEB", "웹 기반", "   ", List.of())
        );

        writer.generate(event());

        // COMPLETED + content NULL 조합은 DB CHECK가 거절한다.
        assertThat(message.getStatus()).isEqualTo(ChatbotMessageStatus.FAILED);
        assertThat(message.getContent()).isNull();
        assertThat(message.getFailReason()).isNotBlank();
    }

    @Test
    void b8_완료_후_lastMessageAt이_완료_시각으로_갱신된다() {
        when(aiClient.generateAnswer(any())).thenReturn(
                aiResponse("REPORT", "리포트 기반", ANSWER, List.of(reportSource()))
        );

        writer.generate(event());

        assertThat(conversation.getLastMessageAt())
                .isEqualTo(message.getCompletedAt());
    }

    @Test
    void b9_failReason에_내부_오류_정보가_들어가지_않는다() {
        when(aiClient.generateAnswer(any())).thenThrow(new ChatbotAiException(
                "AI 서비스가 HTTP 500을 반환했습니다. http://ai:8000/internal/v1/chatbot/answers"
        ));

        writer.generate(event());

        assertThat(message.getFailReason())
                .isEqualTo(ChatbotAnswerWriter.GENERIC_FAIL_REASON)
                .doesNotContain("http")
                .doesNotContain("500")
                .doesNotContain("Exception");
    }

    @Test
    void b10_출처_배열을_가공하지_않고_그대로_저장한다() {
        ChatbotAiAnswerResponse.Source web = new ChatbotAiAnswerResponse.Source(
                "WEB", null, null, "래미안 옥수 리버젠 단지 정보",
                null, "https://example.com/a", "2026-07-31T12:00:00+00:00"
        );
        when(aiClient.generateAnswer(any())).thenReturn(
                aiResponse("WEB", "웹 기반", ANSWER, List.of(reportSource(), web))
        );

        writer.generate(event());

        assertThat(message.getSourcesJson())
                .contains("\"sourceType\":\"REPORT\"")
                .contains("\"sourceType\":\"WEB\"")
                .contains("https://example.com/a");
    }

    @Test
    void 메시지가_없으면_조용히_끝낸다() {
        when(messageRepository.findPendingAssistantByIdForUpdateSkipLocked(
                MESSAGE_ID
        ))
                .thenReturn(Optional.empty());

        writer.generate(event());

        assertThat(message.getStatus())
                .isEqualTo(ChatbotMessageStatus.PENDING);
    }

    @Test
    void 완료된_메시지는_늦은_실패_처리가_덮어쓰지_않는다() {
        when(aiClient.generateAnswer(any())).thenReturn(
                aiResponse("REPORT", "리포트 기반", ANSWER, List.of())
        );
        writer.generate(event());

        writer.markFailed(MESSAGE_ID);

        assertThat(message.getStatus()).isEqualTo(ChatbotMessageStatus.COMPLETED);
        assertThat(message.getContent()).isEqualTo(ANSWER);
    }
}
