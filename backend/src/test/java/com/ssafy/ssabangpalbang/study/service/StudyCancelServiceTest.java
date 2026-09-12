package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplication;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import com.ssafy.ssabangpalbang.study.service.port.StudyNotificationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyCancelServiceTest {

    @Mock StudyRepository studyRepository;
    @Mock MemberRepository memberRepository;
    @Mock StudyMemberRepository studyMemberRepository;
    @Mock StudyApplicationRepository studyApplicationRepository;
    @Mock StudyNotificationPort studyNotificationPort;
    @InjectMocks StudyCancelService service;

    @Test
    void 취소하면_전용_시각을_저장하고_승인_멤버와_대기_신청자에게_한번씩_알린다() {
        Study study = study(StudyStatus.RECRUITING);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));
        when(studyMemberRepository.findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(
                        StudyMember.createLeader(10L, 7L),
                        StudyMember.createMember(10L, 8L)
                ));
        when(studyApplicationRepository.findByStudyIdAndStatus(
                10L, StudyApplicationStatus.PENDING))
                .thenReturn(List.of(
                        StudyApplication.create(10L, 8L, "중복 수신자", StudyPurpose.STUDY),
                        StudyApplication.create(10L, 9L, "대기 신청자", StudyPurpose.RESIDENCE)
                ));

        var response = service.cancel(7L, 10L);

        assertThat(response.studyId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(OffsetDateTime.parse(response.canceledAt()).getOffset().getTotalSeconds())
                .isEqualTo(9 * 60 * 60);
        assertThat(study.getStatus()).isEqualTo(StudyStatus.CANCELED);
        assertThat(study.getCanceledAt()).isNotNull();
        assertThat(study.getDeletedAt()).isNull();

        ArgumentCaptor<StudyNotificationPort.StudyCanceledNotification> captor =
                ArgumentCaptor.forClass(StudyNotificationPort.StudyCanceledNotification.class);
        verify(studyNotificationPort, org.mockito.Mockito.times(2))
                .notifyStudyCanceled(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(StudyNotificationPort.StudyCanceledNotification::recipientId)
                .containsExactly(8L, 9L);
        assertThat(captor.getAllValues())
                .allSatisfy(notification -> {
                    assertThat(notification.actorId()).isEqualTo(7L);
                    assertThat(notification.studyId()).isEqualTo(10L);
                    assertThat(notification.studyTitle()).isEqualTo("검증 스터디");
                });

        InOrder order = inOrder(studyRepository, studyMemberRepository,
                studyApplicationRepository, studyNotificationPort);
        order.verify(studyRepository).findForUpdateByIdAndDeletedAtIsNull(10L);
        order.verify(studyMemberRepository)
                .findByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE);
        order.verify(studyApplicationRepository)
                .findByStudyIdAndStatus(10L, StudyApplicationStatus.PENDING);
        order.verify(studyRepository).saveAndFlush(study);
        order.verify(studyNotificationPort, org.mockito.Mockito.times(2))
                .notifyStudyCanceled(any());
        verify(studyRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void 이미_취소된_스터디는_다시_취소하거나_알림을_보내지_않는다() {
        arrangeStudy(StudyStatus.CANCELED);

        assertError(ErrorCode.STUDY_ALREADY_CANCELED, () -> service.cancel(7L, 10L));

        verify(studyRepository, never()).saveAndFlush(any());
        verify(studyNotificationPort, never()).notifyStudyCanceled(any());
    }

    @Test
    void 진행_또는_완료_스터디는_현재_상태를_담아_취소를_거절한다() {
        for (StudyStatus status : List.of(StudyStatus.IN_PROGRESS, StudyStatus.COMPLETED)) {
            arrangeStudy(status);

            assertThatThrownBy(() -> service.cancel(7L, 10L))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.STUDY_CANCEL_NOT_ALLOWED);
                        assertThat(exception.getData())
                                .containsEntry("studyId", 10L)
                                .containsEntry("status", status.name());
                    });
        }
    }

    @Test
    void 스터디장이_아니면_취소할_수_없다() {
        when(memberRepository.findById(8L)).thenReturn(Optional.of(member(8L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));

        assertError(ErrorCode.STUDY_CANCEL_FORBIDDEN, () -> service.cancel(8L, 10L));
    }

    @Test
    void 탈퇴한_회원은_스터디를_조회하기_전에_차단한다() {
        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member(7L, MemberStatus.WITHDRAWN)));

        assertError(ErrorCode.MEMBER_NOT_FOUND, () -> service.cancel(7L, 10L));

        verify(studyRepository, never()).findForUpdateByIdAndDeletedAtIsNull(any());
    }

    private void arrangeStudy(StudyStatus status) {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(status)));
    }

    private Study study(StudyStatus status) {
        Study study = Study.create(
                1L, 7L, "검증 스터디", "소개", "목표", 5, StudyPurpose.STUDY);
        ReflectionTestUtils.setField(study, "id", 10L);
        ReflectionTestUtils.setField(study, "status", status);
        return study;
    }

    private Member member(Long id, MemberStatus status) {
        Member member = new Member(id + "@test.com", "hash", "회원" + id);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }

    private void assertError(ErrorCode errorCode, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
