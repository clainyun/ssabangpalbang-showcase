package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyDetailQueryRepository;
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
class StudyMemberCommandServiceTest {

    @Mock
    private StudyRepository studyRepository;

    @Mock
    private StudyMemberRepository studyMemberRepository;

    @Mock
    private StudyDetailQueryRepository studyDetailQueryRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private StudyMemberCommandService service;

    @Test
    void 강퇴하면_락과_flush_후_실제_ACTIVE_인원수를_반환한다() {
        Study study = study(StudyStatus.RECRUITING);
        StudyMember target = StudyMember.createMember(10L, 8L);
        arrangeActiveMembers();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 8L))
                .thenReturn(Optional.of(target));
        when(studyDetailQueryRepository.findFieldSessionStatus(10L)).thenReturn(Optional.empty());
        when(studyMemberRepository.countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE))
                .thenReturn(3L);

        var response = service.kick(7L, 10L, 8L);

        assertThat(target.getStatus()).isEqualTo(StudyMemberStatus.REMOVED);
        assertThat(target.getLeftAt()).isNotNull();
        assertThat(response.currentMemberCount()).isEqualTo(3L);
        assertThat(response.kickedAt()).endsWith("+09:00");
        verify(studyMemberRepository).saveAndFlush(target);
        verify(studyRepository, never()).findByIdAndDeletedAtIsNull(10L);
        verify(studyMemberRepository, never()).findByStudyIdAndMemberId(10L, 8L);

        InOrder order = inOrder(studyRepository, studyMemberRepository);
        order.verify(studyRepository).findForUpdateByIdAndDeletedAtIsNull(10L);
        order.verify(studyMemberRepository).findForUpdateByStudyIdAndMemberId(10L, 8L);
        order.verify(studyMemberRepository).saveAndFlush(target);
        order.verify(studyMemberRepository)
                .countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE);
    }

    @Test
    void 임장_세션이_존재하면_멤버_상태를_바꾸지_않는다() {
        Study study = study(StudyStatus.CLOSED);
        StudyMember target = StudyMember.createMember(10L, 8L);
        arrangeActiveMembers();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 8L))
                .thenReturn(Optional.of(target));
        when(studyDetailQueryRepository.findFieldSessionStatus(10L)).thenReturn(Optional.of("IN_PROGRESS"));

        assertThatThrownBy(() -> service.kick(7L, 10L, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_MEMBER_KICK_NOT_ALLOWED);

        assertThat(target.getStatus()).isEqualTo(StudyMemberStatus.ACTIVE);
        assertThat(target.getLeftAt()).isNull();
        verify(studyMemberRepository, never()).saveAndFlush(target);
    }

    @Test
    void 이미_강퇴된_멤버에_대한_동시_후속_요청은_404를_반환한다() {
        StudyMember removed = StudyMember.createMember(10L, 8L);
        removed.kick(java.time.Instant.now());
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 8L))
                .thenReturn(Optional.of(removed));

        assertThatThrownBy(() -> service.kick(7L, 10L, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_MEMBER_NOT_FOUND);

        verify(memberRepository, never()).findById(8L);
    }

    @Test
    void 탈퇴한_스터디장은_멤버를_강퇴할_수_없다() {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.WITHDRAWN)));

        assertThatThrownBy(() -> service.kick(7L, 10L, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verify(studyRepository, never()).findForUpdateByIdAndDeletedAtIsNull(10L);
    }

    @Test
    void 탈퇴한_대상_회원은_승인_멤버_행이_남아도_강퇴하지_않는다() {
        StudyMember target = StudyMember.createMember(10L, 8L);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(memberRepository.findById(8L)).thenReturn(Optional.of(member(8L, MemberStatus.WITHDRAWN)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 8L))
                .thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.kick(7L, 10L, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_MEMBER_NOT_FOUND);

        assertThat(target.getStatus()).isEqualTo(StudyMemberStatus.ACTIVE);
        verify(studyMemberRepository, never()).saveAndFlush(target);
    }

    @Test
    void 일반_멤버가_나가면_REMOVED로_변경하고_ACTIVE_인원수를_반환한다() {
        Study study = study(StudyStatus.RECRUITING);
        StudyMember membership = StudyMember.createMember(10L, 8L);
        when(memberRepository.findById(8L))
                .thenReturn(Optional.of(member(8L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study));
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 8L))
                .thenReturn(Optional.of(membership));
        when(studyDetailQueryRepository.findFieldSessionStatus(10L))
                .thenReturn(Optional.empty());
        when(studyMemberRepository.countByStudyIdAndStatus(
                10L, StudyMemberStatus.ACTIVE)).thenReturn(3L);

        var response = service.leave(8L, 10L);

        assertThat(membership.getStatus()).isEqualTo(StudyMemberStatus.REMOVED);
        assertThat(membership.getLeftAt()).isNotNull();
        assertThat(response.memberId()).isEqualTo(8L);
        assertThat(response.currentMemberCount()).isEqualTo(3L);
        assertThat(response.leftAt()).endsWith("+09:00");
        verify(studyMemberRepository).saveAndFlush(membership);
    }

    @Test
    void 모집_마감된_스터디의_일반_멤버도_나갈_수_있다() {
        StudyMember membership = StudyMember.createMember(10L, 8L);
        when(memberRepository.findById(8L))
                .thenReturn(Optional.of(member(8L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.CLOSED)));
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 8L))
                .thenReturn(Optional.of(membership));
        when(studyDetailQueryRepository.findFieldSessionStatus(10L))
                .thenReturn(Optional.empty());
        when(studyMemberRepository.countByStudyIdAndStatus(
                10L, StudyMemberStatus.ACTIVE)).thenReturn(2L);

        var response = service.leave(8L, 10L);

        assertThat(response.studyStatus()).isEqualTo("CLOSED");
        assertThat(response.currentMemberCount()).isEqualTo(2L);
        assertThat(membership.getStatus()).isEqualTo(StudyMemberStatus.REMOVED);
    }

    @Test
    void 스터디장은_스터디를_나갈_수_없다() {
        StudyMember leader = StudyMember.createLeader(10L, 7L);
        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 7L))
                .thenReturn(Optional.of(leader));

        assertThatThrownBy(() -> service.leave(7L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_LEADER_CANNOT_LEAVE);

        verify(studyDetailQueryRepository, never()).findFieldSessionStatus(10L);
        verify(studyMemberRepository, never()).saveAndFlush(leader);
    }

    @Test
    void 가입_정보가_없거나_이미_REMOVED면_스터디를_나갈_수_없다() {
        when(memberRepository.findById(8L))
                .thenReturn(Optional.of(member(8L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 8L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leave(8L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_MEMBER_NOT_FOUND);
    }

    @Test
    void 임장_시작_후에는_스터디를_나갈_수_없다() {
        StudyMember membership = StudyMember.createMember(10L, 8L);
        when(memberRepository.findById(8L))
                .thenReturn(Optional.of(member(8L, MemberStatus.ACTIVE)));
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.IN_PROGRESS)));
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 8L))
                .thenReturn(Optional.of(membership));
        when(studyDetailQueryRepository.findFieldSessionStatus(10L))
                .thenReturn(Optional.of("IN_PROGRESS"));

        assertThatThrownBy(() -> service.leave(8L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_MEMBER_LEAVE_NOT_ALLOWED);

        assertThat(membership.getStatus()).isEqualTo(StudyMemberStatus.ACTIVE);
        verify(studyMemberRepository, never()).saveAndFlush(membership);
    }

    @Test
    void 완료되거나_취소된_스터디에서는_나갈_수_없다() {
        StudyMember membership = StudyMember.createMember(10L, 8L);
        when(memberRepository.findById(8L))
                .thenReturn(Optional.of(member(8L, MemberStatus.ACTIVE)));
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(10L, 8L))
                .thenReturn(Optional.of(membership));
        when(studyDetailQueryRepository.findFieldSessionStatus(10L))
                .thenReturn(Optional.empty());

        for (StudyStatus status : new StudyStatus[]{
                StudyStatus.COMPLETED,
                StudyStatus.CANCELED
        }) {
            when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(study(status)));

            assertThatThrownBy(() -> service.leave(8L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getErrorCode())
                    .isEqualTo(ErrorCode.STUDY_MEMBER_LEAVE_NOT_ALLOWED);
        }

        assertThat(membership.getStatus()).isEqualTo(StudyMemberStatus.ACTIVE);
        verify(studyMemberRepository, never()).saveAndFlush(membership);
    }

    private void arrangeActiveMembers() {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE)));
        when(memberRepository.findById(8L)).thenReturn(Optional.of(member(8L, MemberStatus.ACTIVE)));
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
