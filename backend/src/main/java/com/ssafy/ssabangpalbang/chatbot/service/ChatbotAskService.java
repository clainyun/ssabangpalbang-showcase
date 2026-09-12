package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessage;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageCreateResponse;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageCreateResponse.BasisPolicyBody;
import com.ssafy.ssabangpalbang.chatbot.integration.ChatbotAnswerRequestedEvent;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotConversationRepository;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotMessageRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatbotAskService {

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final String REPORT_BASIS_TYPE = "REPORT";
    private static final String REPORT_BASIS_LABEL = "리포트 기반";
    private static final String WEB_BASIS_TYPE = "WEB";
    private static final String WEB_BASIS_LABEL = "웹 기반";

    private final ChatbotConversationRepository conversationRepository;
    private final ChatbotMessageRepository messageRepository;
    private final MemberRepository memberRepository;
    private final ApartmentRepository apartmentRepository;
    private final ReportRepository reportRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatbotMessageCreateResponse ask(
            Long apartmentId,
            Long conversationId,
            Long memberId,
            String rawContent
    ) {
        String content = normalize(rawContent);

        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));

        // 대화 행을 잠가 같은 대화의 동시 질문을 직렬화한다.
        ChatbotConversation conversation = conversationRepository
                .findByIdForUpdate(conversationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHATBOT_CONVERSATION_NOT_FOUND
                ));
        if (!conversation.getMemberId().equals(memberId)) {
            throw new BusinessException(
                    ErrorCode.CHATBOT_CONVERSATION_ACCESS_DENIED
            );
        }
        if (!conversation.getApartmentId().equals(apartmentId)) {
            throw new BusinessException(ErrorCode.CHATBOT_APARTMENT_MISMATCH);
        }

        Apartment apartment = apartmentRepository.findById(apartmentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.APARTMENT_NOT_FOUND
                ));

        if (messageRepository.existsInProgress(conversationId)) {
            throw new BusinessException(
                    ErrorCode.CHATBOT_RESPONSE_IN_PROGRESS
            );
        }

        Instant now = Instant.now();
        ChatbotMessage userMessage = messageRepository.save(
                ChatbotMessage.userQuestion(conversationId, content, now)
        );
        ChatbotMessage assistantMessage = messageRepository.save(
                ChatbotMessage.assistantPlaceholder(conversationId)
        );
        conversation.touchLastMessageAt(userMessage.getCreatedAt() == null
                ? now
                : userMessage.getCreatedAt());

        eventPublisher.publishEvent(new ChatbotAnswerRequestedEvent(
                conversationId,
                assistantMessage.getId(),
                apartmentId,
                apartment.getName(),
                content
        ));

        return ChatbotMessageCreateResponse.of(
                conversationId,
                apartmentId,
                basisPolicy(apartmentId),
                userMessage,
                assistantMessage
        );
    }

    /**
     * 사전 안내값이다. 최종 근거는 AI가 결정하므로 이 값과 달라질 수 있다.
     */
    private BasisPolicyBody basisPolicy(Long apartmentId) {
        List<Report> reports = reportRepository.findDoneByApartmentId(
                apartmentId,
                PageRequest.of(0, 1)
        );
        if (reports.isEmpty()) {
            return new BasisPolicyBody(WEB_BASIS_TYPE, WEB_BASIS_LABEL, null);
        }
        return new BasisPolicyBody(
                REPORT_BASIS_TYPE,
                REPORT_BASIS_LABEL,
                reports.get(0).getId()
        );
    }

    private String normalize(String rawContent) {
        String content = rawContent == null ? "" : rawContent.strip();
        if (!hasVisibleText(content) || content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return content;
    }

    /**
     * 제로폭 문자만 입력하면 {@code @NotBlank}를 통과한다.
     * 프론트({@code post/create.tsx})와 같은 판정을 서버에서도 한다.
     */
    private boolean hasVisibleText(String content) {
        return content.codePoints().anyMatch(codePoint ->
                !Character.isWhitespace(codePoint)
                        && Character.getType(codePoint) != Character.FORMAT);
    }
}
