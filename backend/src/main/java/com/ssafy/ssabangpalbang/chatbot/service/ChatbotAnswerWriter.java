package com.ssafy.ssabangpalbang.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiAnswerRequest;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiAnswerResponse;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiClient;
import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiException;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessage;
import com.ssafy.ssabangpalbang.chatbot.integration.ChatbotAnswerRequestedEvent;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotConversationRepository;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 답변 생성의 트랜잭션 경계다.
 *
 * <p>{@link ChatbotAnswerWorker}와 <b>별도 빈이어야 한다</b>. 같은 클래스에 두고
 * 자기 자신을 호출하면 프록시를 거치지 않아 {@code @Transactional}이 적용되지 않고,
 * 변경 감지가 일어나지 않아 메시지가 영영 {@code PENDING}으로 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatbotAnswerWriter {

    /** 사용자에게 그대로 보이는 문구다. 내부 오류 내용을 넣지 않는다. */
    static final String GENERIC_FAIL_REASON =
            "답변을 생성하지 못했습니다. 잠시 후 다시 질문해 주세요.";

    private final ChatbotAiClient aiClient;
    private final ChatbotMessageRepository messageRepository;
    private final ChatbotConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void generate(ChatbotAnswerRequestedEvent event) {
        generate(event, false);
    }

    /** 유실 이벤트 복구 시 AI 일시 장애는 FAILED로 확정하지 않고 다시 시도한다. */
    @Transactional
    public void recover(ChatbotAnswerRequestedEvent event) {
        generate(event, true);
    }

    private void generate(
            ChatbotAnswerRequestedEvent event,
            boolean deferRetryableFailure
    ) {
        ChatbotMessage message = loadAssistantMessage(
                event.assistantMessageId()
        );
        if (message == null) {
            return;
        }
        message.markProcessing();

        ChatbotAiAnswerResponse response;
        try {
            response = aiClient.generateAnswer(new ChatbotAiAnswerRequest(
                    event.apartmentId(),
                    event.question(),
                    event.apartmentName(),
                    event.conversationId(),
                    event.assistantMessageId()
            ));
        } catch (RuntimeException exception) {
            log.warn(
                    "AI 답변 호출이 실패했습니다. messageId={}",
                    event.assistantMessageId(),
                    exception
            );
            if (deferRetryableFailure
                    && exception instanceof ChatbotAiException aiException
                    && aiException.isRetryable()) {
                message.deferRetry(Instant.now());
                return;
            }
            failAndTouch(message, event.conversationId());
            return;
        }

        // 빈 답변을 COMPLETED로 저장하면 DB CHECK가 거절한다.
        if (response == null || !response.hasAnswer()) {
            log.warn(
                    "AI가 빈 답변을 반환했습니다. messageId={}",
                    event.assistantMessageId()
            );
            failAndTouch(message, event.conversationId());
            return;
        }

        Instant completedAt = Instant.now();
        // basisType을 재판정하지 않는다. AI-009의 결과를 그대로 저장한다.
        message.complete(
                response.answer(),
                response.basisType(),
                response.basisLabel(),
                serializeSources(response),
                completedAt
        );
        touchConversation(event.conversationId(), completedAt);
    }

    /** 생성 트랜잭션이 통째로 실패했을 때 실패만 별도로 기록한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long assistantMessageId) {
        messageRepository.findByIdForUpdate(assistantMessageId).ifPresent(message -> {
            if (message.isInProgressAssistant()) {
                Instant failedAt = Instant.now();
                message.fail(GENERIC_FAIL_REASON, failedAt);
                touchConversation(message.getConversationId(), failedAt);
            }
        });
    }

    private ChatbotMessage loadAssistantMessage(Long assistantMessageId) {
        Optional<ChatbotMessage> found =
                messageRepository.findPendingAssistantByIdForUpdateSkipLocked(
                        assistantMessageId
                );
        if (found.isEmpty()) {
            log.debug(
                    "답변 대상 PENDING 메시지가 없거나 이미 처리 중입니다. messageId={}",
                    assistantMessageId
            );
            return null;
        }
        ChatbotMessage message = found.get();
        return message;
    }

    private void failAndTouch(ChatbotMessage message, Long conversationId) {
        Instant completedAt = Instant.now();
        message.fail(GENERIC_FAIL_REASON, completedAt);
        touchConversation(conversationId, completedAt);
    }

    private void touchConversation(Long conversationId, Instant completedAt) {
        conversationRepository.findById(conversationId)
                .ifPresent(conversation ->
                        conversation.touchLastMessageAt(completedAt));
    }

    /** AI가 준 배열을 가공하지 않고 그대로 직렬화한다. */
    private String serializeSources(ChatbotAiAnswerResponse response) {
        try {
            return objectMapper.writeValueAsString(response.sourcesOrEmpty());
        } catch (Exception exception) {
            log.warn("출처 직렬화에 실패했습니다.", exception);
            return null;
        }
    }
}
