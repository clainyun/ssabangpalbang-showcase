package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessage;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageRole;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageStatus;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageCreateResponse;
import com.ssafy.ssabangpalbang.chatbot.integration.ChatbotAnswerRequestedEvent;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotConversationRepository;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotMessageRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatbotAskServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long APARTMENT_ID = 15L;
    private static final Long CONVERSATION_ID = 41L;
    private static final Instant CREATED_AT =
            Instant.parse("2026-07-25T07:10:00Z");

    @Mock
    private ChatbotConversationRepository conversationRepository;
    @Mock
    private ChatbotMessageRepository messageRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ChatbotAskService service;

    @BeforeEach
    void setUp() {
        service = new ChatbotAskService(
                conversationRepository,
                messageRepository,
                memberRepository,
                apartmentRepository,
                reportRepository,
                eventPublisher
        );
        givenValidContext();
    }

    private void givenValidContext() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(MemberStatus.ACTIVE, null)));
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(MEMBER_ID, APARTMENT_ID)));
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.of(apartment()));
        when(messageRepository.existsInProgress(CONVERSATION_ID))
                .thenReturn(false);
        when(reportRepository.findDoneByApartmentId(
                eq(APARTMENT_ID), any(Pageable.class)
        )).thenReturn(List.of());
        when(messageRepository.save(any(ChatbotMessage.class)))
                .thenAnswer(invocation -> {
                    ChatbotMessage message = invocation.getArgument(0);
                    if (message.getId() == null) {
                        ReflectionTestUtils.setField(
                                message,
                                "id",
                                message.getRole() == ChatbotMessageRole.USER
                                        ? 53L
                                        : 54L
                        );
                        ReflectionTestUtils.setField(
                                message, "createdAt", CREATED_AT
                        );
                    }
                    return message;
                });
    }

    private ChatbotMessageCreateResponse ask(String content) {
        return service.ask(APARTMENT_ID, CONVERSATION_ID, MEMBER_ID, content);
    }

    private List<ChatbotMessage> savedMessages() {
        ArgumentCaptor<ChatbotMessage> captor =
                ArgumentCaptor.forClass(ChatbotMessage.class);
        verify(messageRepository, times(2)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void a1_사용자_질문과_AI_placeholder_두_건을_저장한다() {
        ChatbotMessageCreateResponse response = ask("교통 어때요?");

        List<ChatbotMessage> saved = savedMessages();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getRole()).isEqualTo(ChatbotMessageRole.USER);
        assertThat(saved.get(1).getRole())
                .isEqualTo(ChatbotMessageRole.ASSISTANT);
        assertThat(response.conversationId()).isEqualTo(CONVERSATION_ID);
    }

    @Test
    void a2_USER_메시지는_trim된_질문과_완료시각을_갖는다() {
        ask("  교통 어때요?  ");

        ChatbotMessage user = savedMessages().get(0);
        assertThat(user.getContent()).isEqualTo("교통 어때요?");
        assertThat(user.getStatus()).isEqualTo(ChatbotMessageStatus.COMPLETED);
        assertThat(user.getCompletedAt()).isNotNull();
        assertThat(user.getBasisType()).isNull();
    }

    @Test
    void a3_ASSISTANT_placeholder는_content가_null이다() {
        ask("교통 어때요?");

        ChatbotMessage assistant = savedMessages().get(1);
        assertThat(assistant.getContent()).isNull();
        assertThat(assistant.getStatus())
                .isEqualTo(ChatbotMessageStatus.PENDING);
        assertThat(assistant.getCompletedAt()).isNull();
    }

    @Test
    void a4_대화의_lastMessageAt이_사용자_메시지_시각으로_갱신된다() {
        ChatbotConversation conversation = conversation(MEMBER_ID, APARTMENT_ID);
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation));

        ask("교통 어때요?");

        assertThat(conversation.getLastMessageAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void a5_답변_요청_이벤트를_한_번_발행한다() {
        ask("교통 어때요?");

        ArgumentCaptor<ChatbotAnswerRequestedEvent> captor =
                ArgumentCaptor.forClass(ChatbotAnswerRequestedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        ChatbotAnswerRequestedEvent event = captor.getValue();
        assertThat(event.assistantMessageId()).isEqualTo(54L);
        assertThat(event.question()).isEqualTo("교통 어때요?");
        assertThat(event.apartmentName()).isEqualTo("래미안 옥수 리버젠");
    }

    @Test
    void a6_DONE_리포트가_있으면_basisPolicy가_REPORT다() {
        when(reportRepository.findDoneByApartmentId(
                eq(APARTMENT_ID), any(Pageable.class)
        )).thenReturn(List.of(report(48L)));

        ChatbotMessageCreateResponse response = ask("교통 어때요?");

        assertThat(response.basisPolicy().basisType()).isEqualTo("REPORT");
        assertThat(response.basisPolicy().basisLabel()).isEqualTo("리포트 기반");
        assertThat(response.basisPolicy().reportId()).isEqualTo(48L);
    }

    @Test
    void a7_DONE_리포트가_없으면_basisPolicy가_WEB이다() {
        ChatbotMessageCreateResponse response = ask("교통 어때요?");

        assertThat(response.basisPolicy().basisType()).isEqualTo("WEB");
        assertThat(response.basisPolicy().basisLabel()).isEqualTo("웹 기반");
        assertThat(response.basisPolicy().reportId()).isNull();
    }

    @Test
    void a8_응답의_userMessage_basisType은_NONE이다() {
        ChatbotMessageCreateResponse response = ask("교통 어때요?");

        assertThat(response.userMessage().basisType()).isEqualTo("NONE");
        assertThat(response.userMessage().basisLabel()).isNull();
    }

    @Test
    void a9_PENDING_답변이_있으면_409이고_저장하지_않는다() {
        when(messageRepository.existsInProgress(CONVERSATION_ID))
                .thenReturn(true);

        assertBusinessException(
                ErrorCode.CHATBOT_RESPONSE_IN_PROGRESS,
                () -> ask("교통 어때요?")
        );
        verify(messageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void a12_공백만_입력하면_400이다() {
        assertBusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                () -> ask("   ")
        );
        verify(messageRepository, never()).save(any());
    }

    @Test
    void a12_제로폭_문자만_입력하면_400이다() {
        assertBusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                () -> ask("​​")
        );
        verify(messageRepository, never()).save(any());
    }

    @Test
    void a13_trim_후_1000자를_넘으면_400이다() {
        assertBusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                () -> ask("가".repeat(1001))
        );
    }

    @Test
    void a14_trim_전_1005자가_trim_후_999자면_정상_저장된다() {
        String content = "   " + "가".repeat(999) + "   ";
        assertThat(content).hasSize(1005);

        ask(content);

        assertThat(savedMessages().get(0).getContent()).hasSize(999);
    }

    @Test
    void a15_대화가_없으면_404다() {
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.empty());

        assertBusinessException(
                ErrorCode.CHATBOT_CONVERSATION_NOT_FOUND,
                () -> ask("교통 어때요?")
        );
    }

    @Test
    void a15_남의_대화면_403이다() {
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(99L, APARTMENT_ID)));

        assertBusinessException(
                ErrorCode.CHATBOT_CONVERSATION_ACCESS_DENIED,
                () -> ask("교통 어때요?")
        );
    }

    @Test
    void a15_아파트가_일치하지_않으면_400이다() {
        when(conversationRepository.findByIdForUpdate(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(MEMBER_ID, 99L)));

        assertBusinessException(
                ErrorCode.CHATBOT_APARTMENT_MISMATCH,
                () -> ask("교통 어때요?")
        );
    }

    @Test
    void a15_비활성_회원이면_404다() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(MemberStatus.WITHDRAWN, null)));

        assertBusinessException(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> ask("교통 어때요?")
        );
        verify(messageRepository, never()).save(any());
    }

    @Test
    void a15_아파트가_없으면_404다() {
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.empty());

        assertBusinessException(
                ErrorCode.APARTMENT_NOT_FOUND,
                () -> ask("교통 어때요?")
        );
    }

    @Test
    void polling_정보는_이력_조회_경로와_권장_간격을_담는다() {
        ChatbotMessageCreateResponse response = ask("교통 어때요?");

        assertThat(response.polling().messageHistoryApi()).isEqualTo(
                "/api/v1/apartments/15/chatbot/conversations/41/messages"
        );
        assertThat(response.polling().recommendedIntervalMs()).isEqualTo(2500);
    }

    private Member member(MemberStatus status, Instant deletedAt) {
        Member member = new Member("member@example.com", "hash", "회원");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "deletedAt", deletedAt);
        return member;
    }

    private ChatbotConversation conversation(Long memberId, Long apartmentId) {
        ChatbotConversation conversation =
                ChatbotConversation.start(memberId, apartmentId);
        ReflectionTestUtils.setField(conversation, "id", CONVERSATION_ID);
        return conversation;
    }

    private Apartment apartment() {
        Apartment apartment = Apartment.create(
                "A15", "래미안 옥수 리버젠", "서울특별시 성동구 매봉길 15",
                "11200", "성동구", "옥수동", "1120011300",
                127.0, 37.0, 1000, "2016-11", 1200
        );
        ReflectionTestUtils.setField(apartment, "id", APARTMENT_ID);
        return apartment;
    }

    private Report report(Long id) {
        Report report = BeanUtils.instantiateClass(Report.class);
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }

    private void assertBusinessException(
            ErrorCode errorCode,
            Runnable invocation
    ) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(errorCode)
                );
    }
}
