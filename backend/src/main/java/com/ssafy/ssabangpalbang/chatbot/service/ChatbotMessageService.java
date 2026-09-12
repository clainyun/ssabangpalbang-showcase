package com.ssafy.ssabangpalbang.chatbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessage;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageListResponse;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageListResponse.MessageBody;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageListResponse.SourceBody;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotConversationRepository;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotMessageRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotMessageService {

    private static final TypeReference<List<SourceBody>> SOURCE_LIST_TYPE =
            new TypeReference<>() {
            };

    private final ChatbotConversationRepository conversationRepository;
    private final ChatbotMessageRepository messageRepository;
    private final ApartmentRepository apartmentRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ChatbotMessageListResponse getMessages(
            Long apartmentId,
            Long conversationId,
            Long memberId,
            Long cursor,
            int size
    ) {
        ChatbotConversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHATBOT_CONVERSATION_NOT_FOUND
                ));

        if (!conversation.getMemberId().equals(memberId)) {
            throw new BusinessException(
                    ErrorCode.CHATBOT_CONVERSATION_ACCESS_DENIED
            );
        }
        if (!conversation.getApartmentId().equals(apartmentId)) {
            throw new BusinessException(
                    ErrorCode.CHATBOT_APARTMENT_MISMATCH
            );
        }

        Apartment apartment = apartmentRepository.findById(apartmentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.APARTMENT_NOT_FOUND
                ));

        List<ChatbotMessage> rows = messageRepository.findPage(
                conversationId,
                cursor,
                PageRequest.of(0, size + 1)
        );
        boolean hasNext = rows.size() > size;
        List<ChatbotMessage> page = hasNext
                ? rows.subList(0, size)
                : rows;
        Long nextCursor = hasNext
                ? page.get(page.size() - 1).getId()
                : null;
        List<MessageBody> content = page.stream()
                .map(message -> ChatbotMessageListResponse.messageFrom(
                        message,
                        parseSources(message)
                ))
                .toList();
        boolean hasResponseInProgress =
                messageRepository.existsInProgress(conversationId);

        return ChatbotMessageListResponse.from(
                conversation,
                apartment,
                content,
                nextCursor,
                hasNext,
                hasResponseInProgress
        );
    }

    private List<SourceBody> parseSources(ChatbotMessage message) {
        String sourcesJson = message.getSourcesJson();
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(sourcesJson, SOURCE_LIST_TYPE);
        } catch (Exception exception) {
            log.warn(
                    "Failed to parse chatbot sources. messageId={}",
                    message.getId(),
                    exception
            );
            return List.of();
        }
    }
}
