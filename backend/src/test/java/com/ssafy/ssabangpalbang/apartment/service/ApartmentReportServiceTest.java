package com.ssafy.ssabangpalbang.apartment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentReportSearchCondition;
import com.ssafy.ssabangpalbang.apartment.repository.*;
import com.ssafy.ssabangpalbang.apartment.support.ReportResultJsonParser;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ApartmentReportServiceTest {

    private ApartmentRepository apartmentRepository;
    private MemberRepository memberRepository;
    private ApartmentReportQueryRepository reportRepository;
    private ApartmentService service;

    @BeforeEach
    void setUp() {
        apartmentRepository = mock(ApartmentRepository.class);
        memberRepository = mock(MemberRepository.class);
        reportRepository = mock(ApartmentReportQueryRepository.class);
        service = new ApartmentService(
                apartmentRepository,
                mock(ApartmentTransactionRepository.class),
                mock(ApartmentFavoriteRepository.class),
                memberRepository,
                mock(PlatformTransactionManager.class)
        );
        ReflectionTestUtils.setField(service, "apartmentReportQueryRepository", reportRepository);
        ReflectionTestUtils.setField(service, "reportResultJsonParser",
                new ReportResultJsonParser(new ObjectMapper()));
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(apartmentRepository.existsById(10L)).thenReturn(true);
    }

    @Test
    void 리포트_목록을_파싱하고_찜과_서울시각을_매핑한다() {
        var rows = List.of(
                new ApartmentReportRow(2L,
                        "{\"title\":\"정상\",\"summary\":\"요약\",\"analysisTags\":[\"교통\"]}",
                        Instant.parse("2026-07-22T09:07:00Z"), true),
                new ApartmentReportRow(1L, "{broken", null, false)
        );
        when(reportRepository.findPublicReports(10L, 1L, 0, 20))
                .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 2));

        var response = service.getReports(
                1L,
                ApartmentReportSearchCondition.of(10L, null, null)
        );

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).title()).isEqualTo("정상");
        assertThat(response.content().get(0).analysisTags()).containsExactly("교통");
        assertThat(response.content().get(0).favoritedByMe()).isTrue();
        assertThat(response.content().get(0).completedAt().getOffset().toString())
                .isEqualTo("+09:00");
        assertThat(response.content().get(1).title()).isNull();
    }

    @Test
    void 빈_목록은_200용_빈_페이지를_반환한다() {
        when(reportRepository.findPublicReports(10L, 1L, 0, 20))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        assertThat(service.getReports(
                1L,
                ApartmentReportSearchCondition.of(10L, 0, 20)
        ).content()).isEmpty();
    }

    @Test
    void 회원과_아파트_오류를_구분하고_쿼리_순서를_지킨다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());
        assertError(ErrorCode.MEMBER_NOT_FOUND);

        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(apartmentRepository.existsById(10L)).thenReturn(false);
        assertError(ErrorCode.APARTMENT_NOT_FOUND);
        verify(reportRepository, never())
                .findPublicReports(anyLong(), anyLong(), anyInt(), anyInt());
    }

    private void assertError(ErrorCode code) {
        assertThatThrownBy(() -> service.getReports(
                1L,
                ApartmentReportSearchCondition.of(10L, 0, 20)
        )).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
