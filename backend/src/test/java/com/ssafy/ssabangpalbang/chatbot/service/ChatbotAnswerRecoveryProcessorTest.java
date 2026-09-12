package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessage;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageStatus;
import com.ssafy.ssabangpalbang.chatbot.integration.ChatbotAnswerRequestedEvent;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotConversationRepository;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotAnswerRecoveryProcessorTest {

    private static final Long MESSAGE_ID = 54L;
    private static final Long CONVERSATION_ID = 41L;
    private static final Long APARTMENT_ID = 15L;

    @Mock
    private ChatbotMessageRepository messageRepository;
    @Mock
    private ChatbotConversationRepository conversationRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private ChatbotAnswerWriter writer;

    private ChatbotAnswerRecoveryProcessor processor;
    private ChatbotMessage assistant;
    private ChatbotConversation conversation;
    private Apartment apartment;

    @BeforeEach
    void setUp() {
        processor = new ChatbotAnswerRecoveryProcessor(
                messageRepository,
                conversationRepository,
                apartmentRepository,
                writer
        );
        assistant = ChatbotMessage.assistantPlaceholder(CONVERSATION_ID);
        ReflectionTestUtils.setField(assistant, "id", MESSAGE_ID);
        conversation = ChatbotConversation.start(7L, APARTMENT_ID);
        ReflectionTestUtils.setField(conversation, "id", CONVERSATION_ID);
        apartment = Apartment.create(
                "A-15", "테스트 아파트", "서울시 테스트구",
                "11000", "테스트구", "테스트동", "11000101",
                127.0, 37.0, 100, "2020-01", 120
        );
        ReflectionTestUtils.setField(apartment, "id", APARTMENT_ID);
    }

    @Test
    void 유실된_PENDING은_DB_문맥으로_이벤트를_복원한다() {
        ChatbotMessage user = ChatbotMessage.userQuestion(
                CONVERSATION_ID,
                "교통 어때요?",
                Instant.parse("2026-08-01T01:00:00Z")
        );
        when(messageRepository.findById(MESSAGE_ID))
                .thenReturn(Optional.of(assistant));
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation));
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.of(apartment));
        when(messageRepository.findLatestUserBefore(
                eq(CONVERSATION_ID),
                eq(MESSAGE_ID),
                any()
        )).thenReturn(List.of(user));

        processor.recover(MESSAGE_ID);

        ArgumentCaptor<ChatbotAnswerRequestedEvent> captor =
                ArgumentCaptor.forClass(ChatbotAnswerRequestedEvent.class);
        verify(writer).recover(captor.capture());
        assertThat(captor.getValue().assistantMessageId()).isEqualTo(MESSAGE_ID);
        assertThat(captor.getValue().conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(captor.getValue().apartmentId()).isEqualTo(APARTMENT_ID);
        assertThat(captor.getValue().apartmentName()).isEqualTo("테스트 아파트");
        assertThat(captor.getValue().question()).isEqualTo("교통 어때요?");
    }

    @Test
    void 질문_문맥이_없으면_FAILED로_종료해_잠금을_푼다() {
        when(messageRepository.findById(MESSAGE_ID))
                .thenReturn(Optional.of(assistant));
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation));
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.of(apartment));
        when(messageRepository.findLatestUserBefore(
                eq(CONVERSATION_ID),
                eq(MESSAGE_ID),
                any()
        )).thenReturn(List.of());

        processor.recover(MESSAGE_ID);

        verify(writer).markFailed(MESSAGE_ID);
        verify(writer, never()).recover(any(ChatbotAnswerRequestedEvent.class));
    }

    @Test
    void 오래된_PROCESSING은_PENDING으로_되돌린다() {
        Instant updatedAt = Instant.parse("2026-08-01T01:00:00Z");
        assistant.markProcessing();
        ReflectionTestUtils.setField(assistant, "updatedAt", updatedAt);
        when(messageRepository.findByIdForUpdate(MESSAGE_ID))
                .thenReturn(Optional.of(assistant));

        processor.requeueStaleProcessing(
                MESSAGE_ID,
                updatedAt.plusSeconds(1)
        );

        assertThat(assistant.getStatus()).isEqualTo(ChatbotMessageStatus.PENDING);
    }

    @Test
    void 아직_시간이_남은_PROCESSING은_건드리지_않는다() {
        Instant updatedAt = Instant.parse("2026-08-01T01:00:00Z");
        assistant.markProcessing();
        ReflectionTestUtils.setField(assistant, "updatedAt", updatedAt);
        when(messageRepository.findByIdForUpdate(MESSAGE_ID))
                .thenReturn(Optional.of(assistant));

        processor.requeueStaleProcessing(
                MESSAGE_ID,
                updatedAt.minusSeconds(1)
        );

        assertThat(assistant.getStatus())
                .isEqualTo(ChatbotMessageStatus.PROCESSING);
    }
}
