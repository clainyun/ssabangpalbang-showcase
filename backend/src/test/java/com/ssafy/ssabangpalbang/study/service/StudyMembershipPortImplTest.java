package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyMembershipPortImplTest {

    private static final Long STUDY_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    @Mock
    private StudyRepository studyRepository;

    @Mock
    private StudyMemberRepository studyMemberRepository;

    private StudyMembershipPortImpl studyMembershipPort;

    @BeforeEach
    void setUp() {
        studyMembershipPort = new StudyMembershipPortImpl(
                studyRepository,
                studyMemberRepository
        );
    }

    @Test
    void 스터디가_존재하고_ACTIVE_멤버면_스터디_멤버로_판단한다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.IN_PROGRESS)));
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(
                        studyMember(StudyMemberStatus.ACTIVE)
                ));

        assertThat(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID))
                .isTrue();
    }

    @Test
    void 스터디가_존재하지_않으면_스터디_멤버가_아니다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.empty());

        assertThat(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID))
                .isFalse();
    }

    @Test
    void StudyMember가_없으면_스터디_멤버가_아니다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.IN_PROGRESS)));
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThat(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID))
                .isFalse();
    }

    @Test
    void StudyMember_상태가_REMOVED면_스터디_멤버가_아니다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.IN_PROGRESS)));
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(
                        studyMember(StudyMemberStatus.REMOVED)
                ));

        assertThat(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID))
                .isFalse();
    }

    @Test
    void COMPLETED_스터디도_ACTIVE_멤버면_스터디_멤버로_판단한다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.COMPLETED)));
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(
                        studyMember(StudyMemberStatus.ACTIVE)
                ));

        assertThat(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID))
                .isTrue();
    }

    @Test
    void COMPLETED_스터디는_SEND를_허용하지_않는다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.COMPLETED)));

        assertThat(studyMembershipPort.isStudyOpenForSend(STUDY_ID))
                .isFalse();
    }

    @Test
    void CANCELED_스터디는_SEND를_허용하지_않는다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.CANCELED)));

        assertThat(studyMembershipPort.isStudyOpenForSend(STUDY_ID))
                .isFalse();
    }

    @Test
    void IN_PROGRESS_스터디는_SEND를_허용한다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.IN_PROGRESS)));

        assertThat(studyMembershipPort.isStudyOpenForSend(STUDY_ID))
                .isTrue();
    }

    @Test
    void RECRUITING_스터디는_SEND를_허용한다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));

        assertThat(studyMembershipPort.isStudyOpenForSend(STUDY_ID))
                .isTrue();
    }

    @Test
    void CLOSED_스터디는_SEND를_허용한다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.CLOSED)));

        assertThat(studyMembershipPort.isStudyOpenForSend(STUDY_ID))
                .isTrue();
    }

    @Test
    void 생성자_ACTIVE_LEADER는_RECRUITING_스터디_멤버로_판단된다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(StudyMember.createLeader(STUDY_ID, MEMBER_ID)));

        assertThat(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID)).isTrue();
        assertThat(studyMembershipPort.isStudyOpenForSend(STUDY_ID)).isTrue();
    }

    @Test
    void 스터디가_존재하지_않으면_SEND를_허용하지_않는다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.empty());

        assertThat(studyMembershipPort.isStudyOpenForSend(STUDY_ID))
                .isFalse();
    }

    private Study study(StudyStatus status) {
        Study study = Study.create(
                1L, 100L, "title", "intro", "goal", 5, StudyPurpose.STUDY
        );
        ReflectionTestUtils.setField(study, "id", STUDY_ID);
        ReflectionTestUtils.setField(study, "status", status);
        return study;
    }

    private StudyMember studyMember(StudyMemberStatus status) {
        StudyMember studyMember = StudyMember.createLeader(STUDY_ID, MEMBER_ID);
        ReflectionTestUtils.setField(studyMember, "status", status);
        return studyMember;
    }
}
