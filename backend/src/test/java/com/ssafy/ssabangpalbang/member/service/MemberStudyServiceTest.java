package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.request.MemberStudyStatus;
import com.ssafy.ssabangpalbang.member.dto.response.MemberStudyResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.repository.MemberStudyRow;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberStudyServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StudyRepository studyRepository;

    private MemberStudyService service;

    @BeforeEach
    void setUp() {
        service = new MemberStudyService(
                memberRepository,
                studyRepository
        );
    }

    @Test
    void 참여_스터디를_페이지_응답으로_변환한다() {
        Member member = activeMember();
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        MemberStudyRow row = studyRow();
        PageRequest pageable = PageRequest.of(0, 20);
        when(studyRepository.findMemberStudies(
                1L,
                "ACTIVE",
                pageable
        )).thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        PageResponse<MemberStudyResponse> response = service.getMyStudies(
                1L,
                MemberStudyStatus.ACTIVE,
                0,
                20
        );

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).studyId()).isEqualTo(10L);
        assertThat(response.content().get(0).role().name())
                .isEqualTo("MEMBER");
        assertThat(response.content().get(0).nextSchedule().startAt()
                .toString()).isEqualTo("2026-07-27T15:00+09:00");
        assertThat(response.content().get(0).unreadChatCount())
                .isEqualTo(3);
        assertThat(response.content().get(0).pendingReviewCount())
                .isEqualTo(2);
        assertThat(response.content().get(0).readOnly()).isFalse();
        assertThat(response.content().get(0).hasReturnableFieldVisit())
                .isTrue();

        verify(studyRepository).findMemberStudies(
                1L,
                "ACTIVE",
                pageable
        );
    }

    @Test
    void 완료_스터디는_읽기_전용으로_변환한다() {
        Member member = activeMember();
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        MemberStudyRow row = studyRow();
        when(row.getStatus()).thenReturn("COMPLETED");
        PageRequest pageable = PageRequest.of(0, 20);
        when(studyRepository.findMemberStudies(
                1L,
                "COMPLETED",
                pageable
        )).thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        PageResponse<MemberStudyResponse> response = service.getMyStudies(
                1L,
                MemberStudyStatus.COMPLETED,
                0,
                20
        );

        assertThat(response.content().get(0).readOnly()).isTrue();
    }

    @Test
    void 진행중_필터를_저장소에_그대로_전달한다() {
        Member member = activeMember();
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        PageRequest pageable = PageRequest.of(0, 20);
        when(studyRepository.findMemberStudies(
                1L,
                "IN_PROGRESS",
                pageable
        )).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getMyStudies(
                1L,
                MemberStudyStatus.IN_PROGRESS,
                0,
                20
        );

        verify(studyRepository).findMemberStudies(
                1L,
                "IN_PROGRESS",
                pageable
        );
    }

    @Test
    void 존재하지_않거나_탈퇴한_회원은_404로_처리한다() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyStudies(
                99L,
                MemberStudyStatus.ALL,
                0,
                20
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
        );

        verify(studyRepository, never()).findMemberStudies(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private Member activeMember() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getDeletedAt()).thenReturn(null);
        return member;
    }

    private MemberStudyRow studyRow() {
        MemberStudyRow row = mock(MemberStudyRow.class);
        when(row.getStudyId()).thenReturn(10L);
        when(row.getTitle()).thenReturn("옥수동 주말 임장");
        when(row.getIntro()).thenReturn("교통과 단지 환경을 확인합니다.");
        when(row.getGoal()).thenReturn("역 접근성과 경사를 확인합니다.");
        when(row.getStatus()).thenReturn("CLOSED");
        when(row.getRole()).thenReturn("MEMBER");
        when(row.getApartmentId()).thenReturn(15L);
        when(row.getApartmentName()).thenReturn("래미안 옥수 리버젠");
        when(row.getScheduleId()).thenReturn(7L);
        when(row.getStartAt()).thenReturn(
                Instant.parse("2026-07-27T06:00:00Z")
        );
        when(row.getMeetingPlace()).thenReturn("옥수역 3번 출구");
        when(row.getUnreadChatCount()).thenReturn(3L);
        when(row.getPendingReviewCount()).thenReturn(2L);
        when(row.getHasReturnableFieldVisit()).thenReturn(true);
        return row;
    }
}
