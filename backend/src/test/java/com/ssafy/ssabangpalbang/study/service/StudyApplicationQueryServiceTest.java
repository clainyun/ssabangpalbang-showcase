package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplication;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyApplicationQueryServiceTest {
    @Mock StudyRepository studyRepository;
    @Mock StudyApplicationRepository applicationRepository;
    @Mock StudyMemberRepository studyMemberRepository;
    @Mock MemberRepository memberRepository;
    @InjectMocks StudyApplicationQueryService service;

    @Test
    void 목록은_내림차순_커서와_필터_무관_summary를_반환한다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study()));
        when(applicationRepository.findPage(eq(10L), eq(StudyApplicationStatus.PENDING), isNull(), any(Pageable.class)))
                .thenReturn(List.of(application(25L, StudyApplicationStatus.PENDING), application(24L, StudyApplicationStatus.PENDING), application(23L, StudyApplicationStatus.PENDING)));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(8L)));
        when(studyMemberRepository.countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE)).thenReturn(1L);
        when(applicationRepository.countByStudyIdAndStatus(10L, StudyApplicationStatus.PENDING)).thenReturn(2L);
        when(applicationRepository.countByStudyIdAndStatus(10L, StudyApplicationStatus.APPROVED)).thenReturn(3L);
        when(applicationRepository.countByStudyIdAndStatus(10L, StudyApplicationStatus.REJECTED)).thenReturn(4L);

        var response = service.getApplications(7L, 10L, "PENDING", null, 2);

        assertThat(response.content()).extracting(item -> item.applicationId()).containsExactly(25L, 24L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(24L);
        assertThat(response.summary().approvedCount()).isEqualTo(3L);
        assertThat(response.content().get(0).applicant().selectedCharacterId()).isEqualTo("PALBANG");
    }

    @Test
    void CLOSED_스터디의_PENDING은_승인_불가이지만_거절_가능하다() {
        Study study = study();
        ReflectionTestUtils.setField(study, "status", StudyStatus.CLOSED);
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(applicationRepository.findPage(eq(10L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(application(25L, StudyApplicationStatus.PENDING)));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(8L)));
        when(studyMemberRepository.countByStudyIdAndStatus(10L, StudyMemberStatus.ACTIVE)).thenReturn(2L);
        for (StudyApplicationStatus status : StudyApplicationStatus.values()) {
            when(applicationRepository.countByStudyIdAndStatus(10L, status)).thenReturn(0L);
        }

        var response = service.getApplications(7L, 10L, null, null, 20);

        assertThat(response.content().get(0).canApprove()).isFalse();
        assertThat(response.content().get(0).canReject()).isTrue();
    }

    @Test
    void 잘못된_상태와_페이지값은_정해진_오류를_반환한다() {
        assertThatThrownBy(() -> service.getApplications(7L, 10L, "pending", null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_APPLICATION_STATUS_INVALID);
        assertThatThrownBy(() -> service.getApplications(7L, 10L, null, 0L, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    private Study study() {
        Study study = BeanUtils.instantiateClass(Study.class);
        ReflectionTestUtils.setField(study, "id", 10L);
        ReflectionTestUtils.setField(study, "leaderId", 7L);
        ReflectionTestUtils.setField(study, "capacity", 3);
        ReflectionTestUtils.setField(study, "status", StudyStatus.RECRUITING);
        return study;
    }

    private StudyApplication application(Long id, StudyApplicationStatus status) {
        StudyApplication application = BeanUtils.instantiateClass(StudyApplication.class);
        ReflectionTestUtils.setField(application, "id", id);
        ReflectionTestUtils.setField(application, "studyId", 10L);
        ReflectionTestUtils.setField(application, "applicantId", 8L);
        ReflectionTestUtils.setField(application, "status", status);
        ReflectionTestUtils.setField(application, "createdAt", Instant.parse("2026-07-24T09:00:00Z"));
        return application;
    }

    private Member member(Long id) {
        Member member = BeanUtils.instantiateClass(Member.class);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "nickname", "신청자");
        ReflectionTestUtils.setField(member, "selectedCharacterId", "PALBANG");
        return member;
    }
}
