package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.chatbot.config.ChatbotAnswerRecoveryProperties;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotAnswerRecoverySchedulerTest {

    @Mock
    private ChatbotMessageRepository messageRepository;
    @Mock
    private ChatbotAnswerRecoveryProcessor processor;

    @Test
    void 오래된_PROCESSING을_되돌린_뒤_PENDING을_복구한다() {
        Instant now = Instant.parse("2026-08-01T03:00:00Z");
        ChatbotAnswerRecoveryProperties properties =
                new ChatbotAnswerRecoveryProperties(
                        true,
                        Duration.ofSeconds(30),
                        20,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(3)
                );
        ChatbotAnswerRecoveryScheduler scheduler =
                new ChatbotAnswerRecoveryScheduler(
                        messageRepository,
                        processor,
                        properties,
                        Clock.fixed(now, ZoneOffset.UTC)
                );
        when(messageRepository.findStaleProcessingAssistantIds(
                eq(now.minus(Duration.ofMinutes(3))),
                any()
        )).thenReturn(List.of(51L));
        when(messageRepository.findStalePendingAssistantIds(
                eq(now.minus(Duration.ofMinutes(2))),
                any()
        )).thenReturn(List.of(52L, 53L));

        scheduler.recoverStaleAnswers();

        verify(processor).requeueStaleProcessing(
                51L,
                now.minus(Duration.ofMinutes(3))
        );
        verify(processor).recover(52L);
        verify(processor).recover(53L);
    }
}
