package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessage;
import com.ssafy.ssabangpalbang.chatbot.integration.ChatbotAnswerRequestedEvent;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotConversationRepository;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotAnswerRecoveryProcessor {

    private final ChatbotMessageRepository messageRepository;
    private final ChatbotConversationRepository conversationRepository;
    private final ApartmentRepository apartmentRepository;
    private final ChatbotAnswerWriter writer;

    /** DB의 PENDING 메시지에서 유실된 이벤트 내용을 복원해 다시 처리한다. */
    public void recover(Long assistantMessageId) {
        ChatbotMessage assistant = messageRepository.findById(assistantMessageId)
                .filter(ChatbotMessage::isPendingAssistant)
                .orElse(null);
        if (assistant == null) {
            return;
        }

        ChatbotConversation conversation = conversationRepository
                .findById(assistant.getConversationId())
                .orElse(null);
        if (conversation == null) {
            failUnrecoverable(assistantMessageId, "conversation");
            return;
        }

        Apartment apartment = apartmentRepository
                .findById(conversation.getApartmentId())
                .orElse(null);
        if (apartment == null) {
            failUnrecoverable(assistantMessageId, "apartment");
            return;
        }

        List<ChatbotMessage> questions = messageRepository.findLatestUserBefore(
                conversation.getId(),
                assistantMessageId,
                PageRequest.of(0, 1)
        );
        if (questions.isEmpty() || questions.get(0).getContent() == null) {
            failUnrecoverable(assistantMessageId, "userMessage");
            return;
        }

        writer.recover(new ChatbotAnswerRequestedEvent(
                conversation.getId(),
                assistantMessageId,
                conversation.getApartmentId(),
                apartment.getName(),
                questions.get(0).getContent()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requeueStaleProcessing(Long assistantMessageId, Instant cutoff) {
        messageRepository.findByIdForUpdate(assistantMessageId)
                .filter(ChatbotMessage::isProcessingAssistant)
                .filter(message -> message.getUpdatedAt() != null)
                .filter(message -> !message.getUpdatedAt().isAfter(cutoff))
                .ifPresent(ChatbotMessage::requeue);
    }

    private void failUnrecoverable(Long assistantMessageId, String missing) {
        log.error(
                "챗봇 답변 복구 문맥이 없습니다. messageId={}, missing={}",
                assistantMessageId,
                missing
        );
        writer.markFailed(assistantMessageId);
    }
}
