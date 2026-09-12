package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotConversationCreateResponse;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotConversationRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatbotConversationService {

    private static final List<String> RECOMMENDED_QUESTIONS =
            List.of("교통 어때요?", "시세 알려줘", "스터디 추천");

    private final ChatbotConversationRepository conversationRepository;
    private final MemberRepository memberRepository;
    private final ApartmentRepository apartmentRepository;
    private final ReportRepository reportRepository;

    @Transactional
    public ChatbotConversationCreateResponse create(
            Long apartmentId,
            Long memberId
    ) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));

        Apartment apartment = apartmentRepository.findById(apartmentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.APARTMENT_NOT_FOUND
                ));

        List<Report> reports = reportRepository.findDoneByApartmentId(
                apartmentId,
                PageRequest.of(0, 1)
        );
        Report report = reports.isEmpty() ? null : reports.get(0);

        ChatbotConversation conversation = conversationRepository.save(
                ChatbotConversation.start(memberId, apartmentId)
        );

        return ChatbotConversationCreateResponse.from(
                conversation,
                apartment,
                report,
                RECOMMENDED_QUESTIONS
        );
    }
}
