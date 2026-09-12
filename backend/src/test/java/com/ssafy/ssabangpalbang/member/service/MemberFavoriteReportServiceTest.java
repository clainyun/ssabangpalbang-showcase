package com.ssafy.ssabangpalbang.member.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.apartment.service.ApartmentImageUrlResolver;
import com.ssafy.ssabangpalbang.apartment.support.ReportResultJsonParser;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFavoriteReportResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.repository.FavoriteReportRow;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberFavoriteReportServiceTest {

    private MemberRepository memberRepository;
    private ReportRepository reportRepository;
    private ApartmentRepository apartmentRepository;
    private ApartmentImageUrlResolver imageUrlResolver;
    private MemberFavoriteReportService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        reportRepository = mock(ReportRepository.class);
        apartmentRepository = mock(ApartmentRepository.class);
        imageUrlResolver = mock(ApartmentImageUrlResolver.class);
        service = new MemberFavoriteReportService(
                memberRepository,
                reportRepository,
                new ReportResultJsonParser(new ObjectMapper()),
                apartmentRepository,
                imageUrlResolver
        );
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(activeMember()));
    }

    @Test
    void 결과_JSON과_아파트와_찜_시각을_카드로_변환한다() {
        FavoriteReportRow row = favoriteReportRow(
                """
                        {
                          "title":"래미안 임장 리포트",
                          "summary":"교통 접근성이 좋습니다.",
                          "analysisTags":["교통 우수","단지 경사"]
                        }
                        """
        );
        when(reportRepository.findFavoriteDoneReports(
                1L,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(row),
                PageRequest.of(0, 20),
                1
        ));

        PageResponse<MemberFavoriteReportResponse> result =
                service.getFavoriteReports(1L, 0, 20);

        assertThat(result.content()).hasSize(1);
        MemberFavoriteReportResponse item = result.content().get(0);
        assertThat(item.reportId()).isEqualTo(48L);
        assertThat(item.title()).isEqualTo("래미안 임장 리포트");
        assertThat(item.summary()).isEqualTo("교통 접근성이 좋습니다.");
        assertThat(item.analysisTags())
                .containsExactly("교통 우수", "단지 경사");
        assertThat(item.apartment().apartmentId()).isEqualTo(15L);
        assertThat(item.apartment().address())
                .isEqualTo("서울특별시 성동구 매봉길 15");
        assertThat(item.apartment().dongName()).isEqualTo("옥수동");
        assertThat(item.favoritedByMe()).isTrue();
        assertThat(item.completedAt().toString())
                .isEqualTo("2026-07-22T18:07+09:00");
        assertThat(item.favoritedAt().toString())
                .isEqualTo("2026-07-23T09:00+09:00");
    }

    @Test
    void 결과_JSON이_잘못되어도_목록은_빈_요약으로_반환한다() {
        FavoriteReportRow row = favoriteReportRow("{invalid-json");
        when(reportRepository.findFavoriteDoneReports(
                1L,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(List.of(row)));

        MemberFavoriteReportResponse item =
                service.getFavoriteReports(1L, 0, 20)
                        .content().get(0);

        assertThat(item.title()).isNull();
        assertThat(item.summary()).isNull();
        assertThat(item.analysisTags()).isEmpty();
    }

    @Test
    void 찜한_리포트가_없으면_빈_페이지를_반환한다() {
        when(reportRepository.findFavoriteDoneReports(
                1L,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0
        ));

        PageResponse<MemberFavoriteReportResponse> result =
                service.getFavoriteReports(1L, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void 존재하지_않거나_탈퇴한_회원은_숨긴다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());
        assertMemberNotFound();

        Member withdrawn = activeMember();
        ReflectionTestUtils.setField(
                withdrawn,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(withdrawn));
        assertMemberNotFound();

        verify(reportRepository, never()).findFavoriteDoneReports(
                1L,
                PageRequest.of(0, 20)
        );
    }

    private FavoriteReportRow favoriteReportRow(String resultJson) {
        FavoriteReportRow row = mock(FavoriteReportRow.class);
        when(row.getReportId()).thenReturn(48L);
        when(row.getResultJson()).thenReturn(resultJson);
        when(row.getApartmentId()).thenReturn(15L);
        when(row.getApartmentName()).thenReturn("래미안 옥수 리버젠");
        when(row.getApartmentAddress()).thenReturn(
                "서울특별시 성동구 매봉길 15"
        );
        when(row.getDongName()).thenReturn("옥수동");
        when(row.getCompletedAt()).thenReturn(
                Instant.parse("2026-07-22T09:07:00Z")
        );
        when(row.getFavoritedAt()).thenReturn(
                Instant.parse("2026-07-23T00:00:00Z")
        );
        return row;
    }

    private Member activeMember() {
        return new Member("member@example.com", "hash", "member");
    }

    private void assertMemberNotFound() {
        assertThatThrownBy(() ->
                service.getFavoriteReports(1L, 0, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
                );
    }
}
