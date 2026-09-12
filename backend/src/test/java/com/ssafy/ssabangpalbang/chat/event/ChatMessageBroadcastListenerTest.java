package com.ssafy.ssabangpalbang.chat.event;

import com.ssafy.ssabangpalbang.chat.dto.ChatMessageBroadcastResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatSenderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMessageBroadcastListenerTest {

    private static final Long STUDY_ID = 7L;
    private static final Long MESSAGE_ID = 1080L;
    private static final String DESTINATION = "/sub/studies/7/chat";

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChatMessageBroadcastListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatMessageBroadcastListener(messagingTemplate);
    }

    @Test
    void AFTER_COMMIT_발행은_destination으로_한_번_convertAndSend한다() {
        ChatMessageBroadcastEvent event = event();

        listener.onChatMessageBroadcast(event);

        verify(messagingTemplate, times(1)).convertAndSend(eq(DESTINATION), eq(event.payload()));
    }

    @Test
    void convertAndSend_실패는_예외를_삼키지_않는다() {
        ChatMessageBroadcastEvent event = event();
        doThrow(new IllegalStateException("broker down"))
                .when(messagingTemplate)
                .convertAndSend(eq(DESTINATION), eq(event.payload()));

        assertThatThrownBy(() -> listener.onChatMessageBroadcast(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broker down");
    }

    private ChatMessageBroadcastEvent event() {
        return new ChatMessageBroadcastEvent(new ChatMessageBroadcastResponse(
                MESSAGE_ID,
                STUDY_ID,
                "TEXT",
                "본문은 로그에 남기지 않음",
                null,
                new ChatSenderResponse(42L, "루돌푸", "PALBANG"),
                OffsetDateTime.of(2026, 8, 6, 10, 0, 0, 0, ZoneOffset.ofHours(9)),
                false,
                null
        ));
    }
}
