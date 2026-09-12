package com.ssafy.ssabangpalbang.chatbot.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotConversationCreateResponse;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotConversationRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotConversationServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long APARTMENT_ID = 15L;
    private static final Instant CREATED_AT =
            Instant.parse("2026-07-25T07:00:00Z");

    @Mock
    private ChatbotConversationRepository conversationRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private ReportRepository reportRepository;

    private ChatbotConversationService service;

    @BeforeEach
    void setUp() {
        service = new ChatbotConversationService(
                conversationRepository,
                memberRepository,
                apartmentRepository,
                reportRepository
        );
    }

    @Test
    void DONE_리포트가_있으면_REPORT_근거로_대화를_생성한다() {
        Report report = report(48L, ReportStatus.DONE, CREATED_AT);
        givenValidContext(List.of(report));

        ChatbotConversationCreateResponse response =
                service.create(APARTMENT_ID, MEMBER_ID);

        assertThat(response.preferredBasisType()).isEqualTo("REPORT");
        assertThat(response.preferredBasisLabel()).isEqualTo("리포트 기반");
        assertThat(response.availableReport()).isNotNull();
        assertThat(response.availableReport().reportId()).isEqualTo(48L);
        assertThat(response.availableReport().title())
                .isEqualTo("래미안 옥수 리버젠 임장 리포트");
        verify(conversationRepository).save(any(ChatbotConversation.class));
    }

    @Test
    void DONE_리포트가_없으면_WEB_근거와_null_리포트를_반환한다() {
        givenValidContext(List.of());

        ChatbotConversationCreateResponse response =
                service.create(APARTMENT_ID, MEMBER_ID);

        assertThat(response.preferredBasisType()).isEqualTo("WEB");
        assertThat(response.preferredBasisLabel()).isEqualTo("웹 기반");
        assertThat(response.availableReport()).isNull();
    }

    @Test
    void 리포트가_여러_건이어도_조회된_첫_리포트_하나만_반환한다() {
        Report latest = report(49L, ReportStatus.DONE, CREATED_AT);
        Report older = report(
                48L,
                ReportStatus.DONE,
                CREATED_AT.minusSeconds(60)
        );
        givenValidContext(List.of(latest, older));

        ChatbotConversationCreateResponse response =
                service.create(APARTMENT_ID, MEMBER_ID);

        assertThat(response.availableReport()).isNotNull();
        assertThat(response.availableReport().reportId()).isEqualTo(49L);
        verify(reportRepository).findDoneByApartmentId(
                eq(APARTMENT_ID),
                any(Pageable.class)
        );
    }

    @Test
    void 생성_직후_lastMessageAt은_null이다() {
        givenValidContext(List.of());

        ChatbotConversationCreateResponse response =
                service.create(APARTMENT_ID, MEMBER_ID);

        assertThat(response.lastMessageAt()).isNull();
    }

    @Test
    void 추천_질문은_정본의_세_문구다() {
        givenValidContext(List.of());

        ChatbotConversationCreateResponse response =
                service.create(APARTMENT_ID, MEMBER_ID);

        assertThat(response.recommendedQuestions())
                .containsExactly(
                        "교통 어때요?",
                        "시세 알려줘",
                        "스터디 추천"
                );
    }

    @Test
    void 회원이_없으면_MEMBER_NOT_FOUND이고_저장하지_않는다() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.empty());

        assertBusinessException(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> service.create(APARTMENT_ID, MEMBER_ID)
        );
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void 비활성_회원이면_MEMBER_NOT_FOUND이고_저장하지_않는다() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(MemberStatus.WITHDRAWN, null)));

        assertBusinessException(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> service.create(APARTMENT_ID, MEMBER_ID)
        );
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void 삭제된_회원이면_MEMBER_NOT_FOUND이고_저장하지_않는다() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(
                        MemberStatus.ACTIVE,
                        CREATED_AT
                )));

        assertBusinessException(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> service.create(APARTMENT_ID, MEMBER_ID)
        );
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void 아파트가_없으면_APARTMENT_NOT_FOUND이고_저장하지_않는다() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(MemberStatus.ACTIVE, null)));
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.empty());

        assertBusinessException(
                ErrorCode.APARTMENT_NOT_FOUND,
                () -> service.create(APARTMENT_ID, MEMBER_ID)
        );
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void 회원과_아파트가_모두_없으면_회원을_먼저_검증한다() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.empty());

        assertBusinessException(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> service.create(APARTMENT_ID, MEMBER_ID)
        );
        verify(apartmentRepository, never()).findById(any());
    }

    @Test
    void 저장_대화에는_회원과_아파트_ID가_있고_마지막_메시지는_null이다() {
        givenValidContext(List.of());
        ArgumentCaptor<ChatbotConversation> captor =
                ArgumentCaptor.forClass(ChatbotConversation.class);

        service.create(APARTMENT_ID, MEMBER_ID);

        verify(conversationRepository).save(captor.capture());
        ChatbotConversation saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getApartmentId()).isEqualTo(APARTMENT_ID);
        assertThat(saved.getLastMessageAt()).isNull();
    }

    private void givenValidContext(List<Report> reports) {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(MemberStatus.ACTIVE, null)));
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.of(apartment()));
        when(reportRepository.findDoneByApartmentId(
                eq(APARTMENT_ID),
                any(Pageable.class)
        )).thenReturn(reports);
        when(conversationRepository.save(any(ChatbotConversation.class)))
                .thenAnswer(invocation -> {
                    ChatbotConversation conversation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(conversation, "id", 41L);
                    ReflectionTestUtils.setField(
                            conversation,
                            "createdAt",
                            CREATED_AT
                    );
                    return conversation;
                });
    }

    private Member member(MemberStatus status, Instant deletedAt) {
        Member member = new Member("member@example.com", "hash", "회원");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "deletedAt", deletedAt);
        return member;
    }

    private Apartment apartment() {
        Apartment apartment = Apartment.create(
                "A15",
                "래미안 옥수 리버젠",
                "서울특별시 성동구 매봉길 15",
                "11200",
                "성동구",
                "옥수동",
                "1120011300",
                127.0,
                37.0,
                1000,
                "2016-11",
                1200
        );
        ReflectionTestUtils.setField(apartment, "id", APARTMENT_ID);
        return apartment;
    }

    private Report report(Long id, ReportStatus status, Instant completedAt) {
        Report report = BeanUtils.instantiateClass(Report.class);
        ReflectionTestUtils.setField(report, "id", id);
        ReflectionTestUtils.setField(report, "status", status);
        ReflectionTestUtils.setField(report, "completedAt", completedAt);
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
