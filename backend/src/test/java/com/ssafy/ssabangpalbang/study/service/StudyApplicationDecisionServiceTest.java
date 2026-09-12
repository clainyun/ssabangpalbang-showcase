package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplication;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
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
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyApplicationDecisionServiceTest {
    @Mock StudyRepository studyRepository;
    @Mock StudyApplicationRepository studyApplicationRepository;
    @Mock StudyMemberRepository studyMemberRepository;
    @Mock MemberRepository memberRepository;
    @Mock StudyNotificationPort notificationPort;
    @InjectMocks StudyApplicationDecisionService service;

    @Test
    void 승인하면_study_member가_ACTIVE_MEMBER로_생성되고_알림을_한번_발행한다() {
        Study study = study(2, StudyStatus.RECRUITING);
        StudyApplication application = application(StudyApplicationStatus.PENDING);
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyApplicationRepository.findById(25L)).thenReturn(Optional.of(application));
        when(studyMemberRepository.countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE)).thenReturn(1L);
        when(studyMemberRepository.findByStudyIdAndMemberId(10L, 8L)).thenReturn(Optional.empty());
        when(memberRepository.findById(8L)).thenReturn(Optional.of(member(8L)));

        service.approve(7L, 10L, 25L);

        ArgumentCaptor<StudyMember> memberCaptor = ArgumentCaptor.forClass(StudyMember.class);
        verify(studyMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getStatus()).isEqualTo(StudyMemberStatus.ACTIVE);
        assertThat(application.getStatus()).isEqualTo(StudyApplicationStatus.APPROVED);
        assertThat(application.getDecidedAt()).isNotNull();
        ArgumentCaptor<StudyNotificationPort.ApplicationDecisionNotification> notification = ArgumentCaptor.forClass(
                StudyNotificationPort.ApplicationDecisionNotification.class);
        verify(notificationPort).notifyApplicationApproved(notification.capture());
        assertThat(notification.getValue().applicationId()).isEqualTo(25L);
        assertThat(notification.getValue().recipientId()).isEqualTo(8L);
        assertThat(notification.getValue().serviceNotificationAgreed()).isTrue();
    }

    @Test
    void 정원_직전_승인은_자동_마감하고_락_후_정원을_센다() {
        Study study = study(2, StudyStatus.RECRUITING);
        StudyApplication application = application(StudyApplicationStatus.PENDING);
        arrangeApproval(study, application, Optional.empty(), 1L);

        var response = service.approve(7L, 10L, 25L);

        assertThat(response.studyStatus()).isEqualTo("CLOSED");
        assertThat(study.getRecruitmentClosedAt()).isNotNull();
        InOrder order = inOrder(studyRepository, studyMemberRepository);
        order.verify(studyRepository).findForUpdateByIdAndDeletedAtIsNull(10L);
        order.verify(studyMemberRepository).countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE);
        verify(studyRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void 승인하면_REMOVED_상태의_기존_멤버_행을_재활성화한다() {
        StudyMember removed = StudyMember.createMember(10L, 8L);
        ReflectionTestUtils.setField(removed, "status", StudyMemberStatus.REMOVED);
        ReflectionTestUtils.setField(removed, "leftAt", Instant.now());
        arrangeApproval(study(3, StudyStatus.RECRUITING), application(StudyApplicationStatus.PENDING),
                Optional.of(removed), 1L);

        service.approve(7L, 10L, 25L);

        assertThat(removed.getStatus()).isEqualTo(StudyMemberStatus.ACTIVE);
        assertThat(removed.getLeftAt()).isNull();
        verify(studyMemberRepository, never()).save(any());
    }

    @Test
    void 거절하면_멤버를_만들지_않고_거절_알림을_한번_발행한다() {
        Study study = study(2, StudyStatus.CLOSED);
        StudyApplication application = application(StudyApplicationStatus.PENDING);
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));
        when(studyApplicationRepository.findById(25L)).thenReturn(Optional.of(application));
        when(memberRepository.findById(8L)).thenReturn(Optional.of(member(8L)));

        var response = service.reject(7L, 10L, 25L);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(study.getStatus()).isEqualTo(StudyStatus.CLOSED);
        verify(studyMemberRepository, never()).save(any());
        verify(notificationPort).notifyApplicationRejected(any());
        verify(notificationPort, never()).notifyApplicationApproved(any());
    }

    @Test
    void 이미_처리된_신청은_상태를_담아_충돌을_반환한다() {
        Study study = study(2, StudyStatus.RECRUITING);
        StudyApplication application = application(StudyApplicationStatus.APPROVED);
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyApplicationRepository.findById(25L)).thenReturn(Optional.of(application));
        when(studyMemberRepository.countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE)).thenReturn(1L);
        when(studyMemberRepository.findByStudyIdAndMemberId(10L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(7L, 10L, 25L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_APPLICATION_ALREADY_PROCESSED);
    }

    private void arrangeApproval(Study study, StudyApplication application, Optional<StudyMember> existing, long count) {
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyApplicationRepository.findById(25L)).thenReturn(Optional.of(application));
        when(studyMemberRepository.countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE)).thenReturn(count);
        when(studyMemberRepository.findByStudyIdAndMemberId(10L, 8L)).thenReturn(existing);
        when(memberRepository.findById(8L)).thenReturn(Optional.of(member(8L)));
    }

    private Study study(int capacity, StudyStatus status) {
        Study study = BeanUtils.instantiateClass(Study.class);
        ReflectionTestUtils.setField(study, "id", 10L);
        ReflectionTestUtils.setField(study, "leaderId", 7L);
        ReflectionTestUtils.setField(study, "title", "검증 스터디");
        ReflectionTestUtils.setField(study, "capacity", capacity);
        ReflectionTestUtils.setField(study, "status", status);
        return study;
    }

    private StudyApplication application(StudyApplicationStatus status) {
        StudyApplication application = BeanUtils.instantiateClass(StudyApplication.class);
        ReflectionTestUtils.setField(application, "id", 25L);
        ReflectionTestUtils.setField(application, "studyId", 10L);
        ReflectionTestUtils.setField(application, "applicantId", 8L);
        ReflectionTestUtils.setField(application, "status", status);
        return application;
    }

    private Member member(Long id) {
        Member member = BeanUtils.instantiateClass(Member.class);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "nickname", "신청자");
        ReflectionTestUtils.setField(member, "selectedCharacterId", "PALBANG");
        ReflectionTestUtils.setField(member, "serviceNotificationAgreed", true);
        return member;
    }
}
