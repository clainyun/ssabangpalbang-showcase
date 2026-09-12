package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyRecruitmentServiceTest {

    @Mock
    private StudyRepository studyRepository;

    @Mock
    private StudyMemberRepository studyMemberRepository;

    @Mock
    private StudyApplicationRepository studyApplicationRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private StudyRecruitmentService service;

    @Test
    void 모집을_마감하면_락을_잡고_전용_마감시각과_현재_집계를_반환한다() {
        Study study = study(StudyStatus.RECRUITING);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(4L);
        when(studyApplicationRepository.countByStudyIdAndStatus(10L, StudyApplicationStatus.PENDING))
                .thenReturn(2L);

        var response = service.closeRecruitment(7L, 10L);

        assertThat(study.getStatus()).isEqualTo(StudyStatus.CLOSED);
        assertThat(study.getRecruitmentClosedAt()).isNotNull();
        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(response.currentMemberCount()).isEqualTo(4L);
        assertThat(response.pendingApplicationCount()).isEqualTo(2L);
        assertThat(response.recruitmentClosedAt()).endsWith("+09:00");
        verify(studyRepository).saveAndFlush(study);
        verify(studyRepository, never()).findByIdAndDeletedAtIsNull(10L);

        InOrder order = inOrder(memberRepository, studyRepository);
        order.verify(memberRepository).findById(7L);
        order.verify(studyRepository).findForUpdateByIdAndDeletedAtIsNull(10L);
    }

    @Test
    void 이미_마감된_스터디는_락을_획득한_뒤_409를_반환한다() {
        Study study = study(StudyStatus.CLOSED);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));

        assertThatThrownBy(() -> service.closeRecruitment(7L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_RECRUITMENT_ALREADY_CLOSED);

        verify(studyRepository, never()).saveAndFlush(study);
        verify(studyMemberRepository, never())
                .countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE);
    }

    @Test
    void 탈퇴한_스터디장은_모집을_마감할_수_없다() {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.WITHDRAWN)));

        assertThatThrownBy(() -> service.closeRecruitment(7L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verify(studyRepository, never()).findForUpdateByIdAndDeletedAtIsNull(10L);
    }

    @Test
    void 모집을_재개하면_락을_잡고_RECRUITING과_null_마감시각을_반환한다() {
        Study study = study(StudyStatus.CLOSED);
        ReflectionTestUtils.setField(study, "recruitmentClosedAt", java.time.Instant.now());
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(4L);
        when(studyApplicationRepository.countByStudyIdAndStatus(10L, StudyApplicationStatus.PENDING))
                .thenReturn(2L);

        var response = service.reopenRecruitment(7L, 10L);

        assertThat(study.getStatus()).isEqualTo(StudyStatus.RECRUITING);
        assertThat(study.getRecruitmentClosedAt()).isNull();
        assertThat(response.status()).isEqualTo("RECRUITING");
        assertThat(response.currentMemberCount()).isEqualTo(4L);
        assertThat(response.capacity()).isEqualTo(6);
        assertThat(response.pendingApplicationCount()).isEqualTo(2L);
        assertThat(response.recruitmentClosedAt()).isNull();
        verify(studyRepository).saveAndFlush(study);

        InOrder order = inOrder(memberRepository, studyRepository);
        order.verify(memberRepository).findById(7L);
        order.verify(studyRepository).findForUpdateByIdAndDeletedAtIsNull(10L);
    }

    @Test
    void 이미_모집_중인_스터디는_락을_획득한_뒤_409를_반환한다() {
        Study study = study(StudyStatus.RECRUITING);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));

        assertThatThrownBy(() -> service.reopenRecruitment(7L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_RECRUITMENT_ALREADY_OPEN);

        verify(studyRepository, never()).saveAndFlush(study);
        verify(studyMemberRepository, never())
                .countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE);
    }

    @Test
    void 진행_중_완료_취소_상태에서는_모집을_재개할_수_없다() {
        for (StudyStatus status : new StudyStatus[]{
                StudyStatus.IN_PROGRESS, StudyStatus.COMPLETED, StudyStatus.CANCELED
        }) {
            Study study = study(status);
            when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
            when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));

            assertThatThrownBy(() -> service.reopenRecruitment(7L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getErrorCode())
                    .isEqualTo(ErrorCode.STUDY_RECRUITMENT_OPEN_NOT_ALLOWED);
        }

        verify(studyRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 스터디장이_아니면_모집을_재개할_수_없다() {
        Study study = study(StudyStatus.CLOSED);
        ReflectionTestUtils.setField(study, "leaderId", 99L);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));

        assertThatThrownBy(() -> service.reopenRecruitment(7L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_RECRUITMENT_OPEN_FORBIDDEN);

        verify(studyRepository, never()).saveAndFlush(study);
    }

    @Test
    void 정원이_가득_찬_스터디는_모집을_재개할_수_없다() {
        Study study = study(StudyStatus.CLOSED);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(6L);

        assertThatThrownBy(() -> service.reopenRecruitment(7L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_CAPACITY_FULL);

        verify(studyRepository, never()).saveAndFlush(study);
    }

    @Test
    void 존재하지_않는_스터디는_모집을_재개할_수_없다() {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reopenRecruitment(7L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_NOT_FOUND);
    }

    private Study study(StudyStatus status) {
        Study study = BeanUtils.instantiateClass(Study.class);
        ReflectionTestUtils.setField(study, "id", 10L);
        ReflectionTestUtils.setField(study, "leaderId", 7L);
        ReflectionTestUtils.setField(study, "capacity", 6);
        ReflectionTestUtils.setField(study, "status", status);
        return study;
    }

    private Member member(Long id, MemberStatus status) {
        Member member = new Member("member" + id + "@example.com", "hash", "회원" + id);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }
}
