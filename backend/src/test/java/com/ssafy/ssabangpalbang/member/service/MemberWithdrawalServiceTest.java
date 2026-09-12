package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.auth.token.RefreshTokenStore;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.request.MemberWithdrawalRequest;
import com.ssafy.ssabangpalbang.member.dto.response.MemberWithdrawalResponse;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.StudyApplication;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberRole;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemberWithdrawalServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final Instant NOW = Instant.parse("2026-07-30T06:45:00Z");

    private MemberRepository memberRepository;
    private StudyRepository studyRepository;
    private StudyApplicationRepository studyApplicationRepository;
    private StudyMemberRepository studyMemberRepository;
    private FieldParticipantRepository fieldParticipantRepository;
    private FcmTokenRepository fcmTokenRepository;
    private JwtTokenProvider jwtTokenProvider;
    private RefreshTokenStore refreshTokenStore;
    private MemberWithdrawalService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        studyRepository = mock(StudyRepository.class);
        studyApplicationRepository = mock(StudyApplicationRepository.class);
        studyMemberRepository = mock(StudyMemberRepository.class);
        fieldParticipantRepository = mock(FieldParticipantRepository.class);
        fcmTokenRepository = mock(FcmTokenRepository.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        refreshTokenStore = mock(RefreshTokenStore.class);

        service = new MemberWithdrawalService(
                memberRepository,
                studyRepository,
                studyApplicationRepository,
                studyMemberRepository,
                fieldParticipantRepository,
                fcmTokenRepository,
                jwtTokenProvider,
                refreshTokenStore,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void 회원과_연관_상태를_같은_시각으로_정리하고_인증_수단을_폐기한다() {
        Member member = activeMember();
        StudyApplication application = StudyApplication.create(
                10L,
                MEMBER_ID,
                "참여하고 싶습니다.",
                StudyPurpose.RESIDENCE
        );
        StudyMember studyMember = StudyMember.createMember(11L, MEMBER_ID);
        stubValidRequest(member);
        when(studyApplicationRepository
                .findAllForUpdateByApplicantIdAndStatus(
                        MEMBER_ID,
                        StudyApplicationStatus.PENDING
                )).thenReturn(List.of(application));
        when(studyMemberRepository
                .findAllForUpdateByMemberIdAndRoleAndStatus(
                        MEMBER_ID,
                        StudyMemberRole.MEMBER,
                        StudyMemberStatus.ACTIVE
                )).thenReturn(List.of(studyMember));

        MemberWithdrawalResponse response = service.withdraw(
                MEMBER_ID,
                request("회원탈퇴")
        );

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getDeletedAt()).isEqualTo(NOW);
        assertThat(application.getStatus())
                .isEqualTo(StudyApplicationStatus.REJECTED);
        assertThat(application.getDecidedAt()).isEqualTo(NOW);
        assertThat(studyMember.getStatus())
                .isEqualTo(StudyMemberStatus.REMOVED);
        assertThat(studyMember.getLeftAt()).isEqualTo(NOW);
        assertThat(response.memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.withdrawnAt().toInstant()).isEqualTo(NOW);
        assertThat(response.withdrawnAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));

        var order = inOrder(
                fcmTokenRepository,
                memberRepository,
                refreshTokenStore
        );
        order.verify(fcmTokenRepository).deleteAllByMemberId(MEMBER_ID);
        order.verify(memberRepository).flush();
        order.verify(refreshTokenStore).revokeAll(MEMBER_ID);
    }

    @Test
    void 확인_문구가_다르면_토큰과_DB를_조회하지_않는다() {
        BusinessException exception = catchThrowableOfType(
                () -> service.withdraw(MEMBER_ID, request("탈퇴")),
                BusinessException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_WITHDRAW_CONFIRMATION_INVALID
        );
        assertThat(exception.getData()).containsEntry(
                "expectedValue",
                "회원탈퇴"
        );
        verifyNoInteractions(jwtTokenProvider, memberRepository);
    }

    @Test
    void Refresh_Token_회원이_Access_Token_회원과_다르면_401이다() {
        when(jwtTokenProvider.parseRefreshToken(REFRESH_TOKEN))
                .thenReturn(2L);

        BusinessException exception = catchThrowableOfType(
                () -> service.withdraw(MEMBER_ID, request("회원탈퇴")),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        verifyNoInteractions(memberRepository, refreshTokenStore);
    }

    @Test
    void 회원이_없으면_404이다() {
        when(jwtTokenProvider.parseRefreshToken(REFRESH_TOKEN))
                .thenReturn(MEMBER_ID);
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = catchThrowableOfType(
                () -> service.withdraw(MEMBER_ID, request("회원탈퇴")),
                BusinessException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void 이미_탈퇴한_회원이면_409이다() {
        Member member = activeMember();
        member.withdraw(NOW.minusSeconds(60));
        when(jwtTokenProvider.parseRefreshToken(REFRESH_TOKEN))
                .thenReturn(MEMBER_ID);
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member));

        BusinessException exception = catchThrowableOfType(
                () -> service.withdraw(MEMBER_ID, request("회원탈퇴")),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_ALREADY_WITHDRAWN);
        verifyNoInteractions(fcmTokenRepository, refreshTokenStore);
    }

    @Test
    void 진행_중인_임장에_참여하면_스터디와_세션을_담아_409이다() {
        Member member = activeMember();
        FieldParticipantRepository.InProgressParticipation participation =
                mock(FieldParticipantRepository.InProgressParticipation.class);
        when(participation.getStudyId()).thenReturn(10L);
        when(participation.getSessionId()).thenReturn(5L);
        when(jwtTokenProvider.parseRefreshToken(REFRESH_TOKEN))
                .thenReturn(MEMBER_ID);
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(fieldParticipantRepository.findInProgressParticipation(MEMBER_ID))
                .thenReturn(Optional.of(participation));

        BusinessException exception = catchThrowableOfType(
                () -> service.withdraw(MEMBER_ID, request("회원탈퇴")),
                BusinessException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_WITHDRAWAL_FIELD_SESSION_IN_PROGRESS
        );
        assertThat(exception.getData())
                .containsEntry("studyId", 10L)
                .containsEntry("sessionId", 5L);
        verify(studyRepository, never()).findBlockingLeaderStudyIds(MEMBER_ID);
        verifyNoInteractions(fcmTokenRepository, refreshTokenStore);
    }

    @Test
    void 운영_중인_스터디장이면_스터디_ID_목록을_담아_409이다() {
        Member member = activeMember();
        when(jwtTokenProvider.parseRefreshToken(REFRESH_TOKEN))
                .thenReturn(MEMBER_ID);
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(fieldParticipantRepository.findInProgressParticipation(MEMBER_ID))
                .thenReturn(Optional.empty());
        when(studyRepository.findBlockingLeaderStudyIds(MEMBER_ID))
                .thenReturn(List.of(10L, 14L));

        BusinessException exception = catchThrowableOfType(
                () -> service.withdraw(MEMBER_ID, request("회원탈퇴")),
                BusinessException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_WITHDRAWAL_ACTIVE_STUDY_LEADER
        );
        assertThat(exception.getData())
                .containsEntry("studyIds", List.of(10L, 14L));
        verifyNoInteractions(fcmTokenRepository, refreshTokenStore);
    }

    private void stubValidRequest(Member member) {
        when(jwtTokenProvider.parseRefreshToken(REFRESH_TOKEN))
                .thenReturn(MEMBER_ID);
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(fieldParticipantRepository.findInProgressParticipation(MEMBER_ID))
                .thenReturn(Optional.empty());
        when(studyRepository.findBlockingLeaderStudyIds(MEMBER_ID))
                .thenReturn(List.of());
    }

    private Member activeMember() {
        return new Member("member@example.com", "password-hash", "임장러");
    }

    private MemberWithdrawalRequest request(String confirmationText) {
        return new MemberWithdrawalRequest(REFRESH_TOKEN, confirmationText);
    }
}
