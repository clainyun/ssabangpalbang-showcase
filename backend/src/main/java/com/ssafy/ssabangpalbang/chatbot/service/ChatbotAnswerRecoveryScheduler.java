package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.chatbot.config.ChatbotAnswerRecoveryProperties;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ssabangpalbang.chatbot.recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ChatbotAnswerRecoveryScheduler {

    private final ChatbotMessageRepository messageRepository;
    private final ChatbotAnswerRecoveryProcessor processor;
    private final ChatbotAnswerRecoveryProperties properties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString =
                    "${ssabangpalbang.chatbot.recovery.poll-interval:30s}"
    )
    public void recoverStaleAnswers() {
        Instant now = clock.instant();
        PageRequest page = PageRequest.of(0, properties.batchSize());

        List<Long> processingIds = messageRepository
                .findStaleProcessingAssistantIds(
                        now.minus(properties.processingTimeout()),
                        page
                );
        processingIds.forEach(id -> requeueSafely(
                id,
                now.minus(properties.processingTimeout())
        ));

        List<Long> pendingIds = messageRepository.findStalePendingAssistantIds(
                now.minus(properties.pendingTimeout()),
                page
        );
        pendingIds.forEach(this::recoverSafely);
    }

    private void requeueSafely(Long messageId, Instant cutoff) {
        try {
            processor.requeueStaleProcessing(messageId, cutoff);
        } catch (RuntimeException exception) {
            log.error(
                    "챗봇 PROCESSING 복구에 실패했습니다. messageId={}",
                    messageId,
                    exception
            );
        }
    }

    private void recoverSafely(Long messageId) {
        try {
            processor.recover(messageId);
        } catch (RuntimeException exception) {
            log.error(
                    "챗봇 PENDING 복구에 실패했습니다. messageId={}",
                    messageId,
                    exception
            );
        }
    }
}
