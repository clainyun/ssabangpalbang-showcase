package com.ssafy.ssabangpalbang.chat.service;

import com.ssafy.ssabangpalbang.chat.domain.ChatMessage;
import com.ssafy.ssabangpalbang.chat.dto.ChatImageResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageBroadcastResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatSenderResponse;
import com.ssafy.ssabangpalbang.chat.event.ChatMessageBroadcastEvent;
import com.ssafy.ssabangpalbang.chat.event.ChatMessagePushRequestedEvent;
import com.ssafy.ssabangpalbang.chat.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatMessageWriter가 저장 성공 시에만 방송·푸시 이벤트를
 * 게시하는지 직접 검증한다. (GPT 리뷰 지적사항 4번: 중복 요청에서 이벤트가
 * 재발행되지 않는지는 ChatMessageService 레벨의 Mock만으로는 보장되지 않고,
 * 이 클래스의 saveAndFlush→publish 순서 자체를 검증해야 한다.)
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageWriterTest {

    private static final Long STUDY_ID = 7L;
    private static final Long SENDER_ID = 42L;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ChatMessageWriter chatMessageWriter;

    @BeforeEach
    void setUp() {
        chatMessageWriter = new ChatMessageWriter(chatMessageRepository, eventPublisher);
    }

    @Test
    void saveText_저장에_성공하면_방송과_푸시_이벤트를_발행한다() {
        ChatSenderResponse sender = new ChatSenderResponse(SENDER_ID, "루돌푸", "PALBANG");
        ChatMessage saved = ChatMessage.createText(STUDY_ID, SENDER_ID, "내용", "client-message-id");
        ReflectionTestUtils.setField(saved, "id", 1080L);
        ReflectionTestUtils.setField(saved, "createdAt", Instant.parse("2026-07-22T04:40:00Z"));
        when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenReturn(saved);

        ChatMessage result = chatMessageWriter.saveText(
                STUDY_ID, SENDER_ID, "내용", "client-message-id", sender
        );

        assertThat(result).isSameAs(saved);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        ChatMessageBroadcastEvent broadcastEvent = captor.getAllValues().stream()
                .filter(ChatMessageBroadcastEvent.class::isInstance)
                .map(ChatMessageBroadcastEvent.class::cast)
                .findFirst()
                .orElseThrow();
        ChatMessagePushRequestedEvent pushEvent = captor.getAllValues().stream()
                .filter(ChatMessagePushRequestedEvent.class::isInstance)
                .map(ChatMessagePushRequestedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        ChatMessageBroadcastResponse payload = broadcastEvent.payload();
        assertThat(payload.messageId()).isEqualTo(1080L);
        assertThat(payload.sender()).isEqualTo(sender);
        assertThat(pushEvent.messageId()).isEqualTo(1080L);
        assertThat(pushEvent.senderId()).isEqualTo(SENDER_ID);
        assertThat(pushEvent.messageType()).isEqualTo("TEXT");
        assertThat(pushEvent.content()).isEqualTo("내용");
    }

    @Test
    void saveImage_저장에_성공하면_IMAGE_푸시_이벤트를_발행한다() {
        ChatSenderResponse sender = new ChatSenderResponse(SENDER_ID, "루돌푸", "PALBANG");
        ChatMessage saved = ChatMessage.createImage(
                STUDY_ID, SENDER_ID, 91L, "client-message-id"
        );
        ReflectionTestUtils.setField(saved, "id", 1081L);
        ReflectionTestUtils.setField(saved, "createdAt", Instant.parse("2026-07-22T04:40:00Z"));
        when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenReturn(saved);

        chatMessageWriter.saveImage(
                STUDY_ID, SENDER_ID, 91L, "client-message-id", sender,
                new ChatImageResponse(91L, "https://s3/presigned", "COMPLETED")
        );

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        ChatMessagePushRequestedEvent pushEvent = captor.getAllValues().stream()
                .filter(ChatMessagePushRequestedEvent.class::isInstance)
                .map(ChatMessagePushRequestedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(pushEvent.messageType()).isEqualTo("IMAGE");
        assertThat(pushEvent.content()).isNull();
    }

    @Test
    void saveText가_유일성_제약_위반으로_실패하면_이벤트를_발행하지_않는다() {
        when(chatMessageRepository.saveAndFlush(any(ChatMessage.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> chatMessageWriter.saveText(
                STUDY_ID, SENDER_ID, "내용", "client-message-id",
                new ChatSenderResponse(SENDER_ID, "루돌푸", "PALBANG")
        )).isInstanceOf(DataIntegrityViolationException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void saveImage가_유일성_제약_위반으로_실패하면_이벤트를_발행하지_않는다() {
        when(chatMessageRepository.saveAndFlush(any(ChatMessage.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> chatMessageWriter.saveImage(
                STUDY_ID, SENDER_ID, 91L, "client-message-id",
                new ChatSenderResponse(SENDER_ID, "루돌푸", "PALBANG"),
                new ChatImageResponse(91L, "https://s3/presigned", "COMPLETED")
        )).isInstanceOf(DataIntegrityViolationException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void saveSystem은_sender와_image가_null인_Payload로_발행한다() {
        ChatMessage saved = ChatMessage.createSystem(STUDY_ID, "일정이 변경되었습니다.");
        ReflectionTestUtils.setField(saved, "id", 2000L);
        ReflectionTestUtils.setField(saved, "createdAt", Instant.parse("2026-07-22T04:40:00Z"));
        when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenReturn(saved);

        chatMessageWriter.saveSystem(STUDY_ID, "일정이 변경되었습니다.");

        ArgumentCaptor<ChatMessageBroadcastEvent> captor =
                ArgumentCaptor.forClass(ChatMessageBroadcastEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        ChatMessageBroadcastResponse payload = captor.getValue().payload();
        assertThat(payload.sender()).isNull();
        assertThat(payload.image()).isNull();
        assertThat(payload.messageType()).isEqualTo("SYSTEM");
    }
}
