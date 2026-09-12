package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.dto.response.MemberReportResponse;
import com.ssafy.ssabangpalbang.report.repository.MemberReportRow;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
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
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private MemberRepository memberRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(
                reportRepository,
                memberRepository,
                new ObjectMapper()
        );
    }

    @Test
    void 내_완료_리포트를_페이지로_조회한다() {
        Member activeMember = activeMember();
        MemberReportRow row = reportRow();
        PageRequest pageable = PageRequest.of(0, 20);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(activeMember));
        when(reportRepository.findAccessibleDoneReports(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        PageResponse<MemberReportResponse> response =
                reportService.getMyReports(1L, 0, 20);

        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);

        MemberReportResponse report = response.content().get(0);
        assertThat(report.reportId()).isEqualTo(48L);
        assertThat(report.title()).isEqualTo("래미안 옥수 리버젠 임장 리포트");
        assertThat(report.summary()).isEqualTo("교통은 좋고 경사는 주의가 필요합니다.");
        assertThat(report.analysisTags())
                .containsExactly("교통 우수", "단지 경사");
        assertThat(report.apartment().apartmentId()).isEqualTo(15L);
        assertThat(report.apartment().name()).isEqualTo("래미안 옥수 리버젠");
        assertThat(report.study().studyId()).isEqualTo(10L);
        assertThat(report.study().participantCount()).isEqualTo(4L);
        assertThat(report.study().visitedAt().getOffset().toString())
                .isEqualTo("+09:00");
        assertThat(report.favoritedByMe()).isTrue();
        assertThat(report.canViewEvidence()).isFalse();
        assertThat(report.completedAt().getOffset().toString())
                .isEqualTo("+09:00");
    }

    @Test
    void 결과_JSON의_목록용_필드가_없어도_목록_전체를_실패시키지_않는다() {
        Member activeMember = activeMember();
        MemberReportRow row = reportRow();
        when(row.getResultJson()).thenReturn("{\"metrics\":{}}");
        PageRequest pageable = PageRequest.of(0, 20);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(activeMember));
        when(reportRepository.findAccessibleDoneReports(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        MemberReportResponse report = reportService
                .getMyReports(1L, 0, 20)
                .content()
                .get(0);

        assertThat(report.title()).isNull();
        assertThat(report.summary()).isNull();
        assertThat(report.analysisTags()).isEmpty();
    }

    @Test
    void 잘못된_결과_JSON도_목록_전체를_실패시키지_않는다() {
        Member activeMember = activeMember();
        MemberReportRow row = reportRow();
        when(row.getResultJson()).thenReturn("not-json");
        PageRequest pageable = PageRequest.of(0, 20);

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(activeMember));
        when(reportRepository.findAccessibleDoneReports(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        MemberReportResponse report = reportService
                .getMyReports(1L, 0, 20)
                .content()
                .get(0);

        assertThat(report.title()).isNull();
        assertThat(report.summary()).isNull();
        assertThat(report.analysisTags()).isEmpty();
    }

    @Test
    void 회원이_없으면_404_오류를_반환한다() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getMyReports(99L, 0, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
                );

        verify(reportRepository, never())
                .findAccessibleDoneReports(99L, PageRequest.of(0, 20));
    }

    @Test
    void 탈퇴_회원은_존재하지_않는_회원과_같은_404를_반환한다() {
        Member withdrawnMember = mock(Member.class);
        when(withdrawnMember.getStatus()).thenReturn(MemberStatus.WITHDRAWN);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(withdrawnMember));

        assertThatThrownBy(() -> reportService.getMyReports(1L, 0, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
                );

        verify(reportRepository, never())
                .findAccessibleDoneReports(1L, PageRequest.of(0, 20));
    }

    private Member activeMember() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getDeletedAt()).thenReturn(null);
        return member;
    }

    private MemberReportRow reportRow() {
        MemberReportRow row = mock(MemberReportRow.class);
        when(row.getReportId()).thenReturn(48L);
        when(row.getResultJson()).thenReturn("""
                {
                  "title": "래미안 옥수 리버젠 임장 리포트",
                  "summary": "교통은 좋고 경사는 주의가 필요합니다.",
                  "analysisTags": ["교통 우수", "단지 경사"]
                }
                """);
        when(row.getStatus()).thenReturn("DONE");
        when(row.getFavoritedByMe()).thenReturn(true);
        when(row.getApartmentId()).thenReturn(15L);
        when(row.getApartmentName()).thenReturn("래미안 옥수 리버젠");
        when(row.getStudyId()).thenReturn(10L);
        when(row.getStudyTitle()).thenReturn("옥수동 주말 임장");
        when(row.getVisitedAt()).thenReturn(
                Instant.parse("2026-07-20T05:00:00Z")
        );
        when(row.getParticipantCount()).thenReturn(4L);
        when(row.getCanViewEvidence()).thenReturn(false);
        when(row.getCompletedAt()).thenReturn(
                Instant.parse("2026-07-22T09:07:00Z")
        );
        return row;
    }
}
