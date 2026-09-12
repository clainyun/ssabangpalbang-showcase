package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.domain.ApartmentFavorite;
import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentCountRow;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentFavoriteRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentReportQueryRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentTransactionRepository;
import com.ssafy.ssabangpalbang.apartment.service.ApartmentImageUrlResolver;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFavoriteApartmentResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberFavoriteApartmentServiceTest {

    private MemberRepository memberRepository;
    private ApartmentFavoriteRepository favoriteRepository;
    private ApartmentRepository apartmentRepository;
    private ApartmentTransactionRepository transactionRepository;
    private StudyRepository studyRepository;
    private ApartmentReportQueryRepository reportQueryRepository;
    private ApartmentImageUrlResolver imageUrlResolver;
    private MemberFavoriteApartmentService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        favoriteRepository = mock(ApartmentFavoriteRepository.class);
        apartmentRepository = mock(ApartmentRepository.class);
        transactionRepository = mock(
                ApartmentTransactionRepository.class
        );
        studyRepository = mock(StudyRepository.class);
        reportQueryRepository = mock(
                ApartmentReportQueryRepository.class
        );
        imageUrlResolver = mock(ApartmentImageUrlResolver.class);
        service = new MemberFavoriteApartmentService(
                memberRepository,
                favoriteRepository,
                apartmentRepository,
                transactionRepository,
                studyRepository,
                reportQueryRepository,
                imageUrlResolver
        );
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(activeMember()));
    }

    @Test
    void 빈_목록이면_집계_배치_조회를_생략한다() {
        when(favoriteRepository
                .findByMemberIdOrderByCreatedAtDescIdDesc(
                        1L,
                        PageRequest.of(0, 20)
                )).thenReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(0, 20),
                        0
                ));

        PageResponse<MemberFavoriteApartmentResponse> result =
                service.getFavoriteApartments(1L, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        verify(apartmentRepository, never()).findAllById(anyCollection());
        verify(transactionRepository, never())
                .findLatestNormalByApartmentIds(anyCollection());
        verify(studyRepository, never())
                .countRecruitingByApartmentIds(anyCollection());
        verify(reportQueryRepository, never())
                .countByApartmentIds(anyCollection());
    }

    @Test
    void 아파트와_최신_거래와_집계를_배치로_조립한다() {
        ApartmentFavorite favorite = mock(ApartmentFavorite.class);
        when(favorite.getApartmentId()).thenReturn(15L);
        when(favorite.getCreatedAt()).thenReturn(
                Instant.parse("2026-07-22T09:30:00Z")
        );
        when(favoriteRepository
                .findByMemberIdOrderByCreatedAtDescIdDesc(
                        1L,
                        PageRequest.of(0, 20)
                )).thenReturn(new PageImpl<>(
                        List.of(favorite),
                        PageRequest.of(0, 20),
                        1
                ));

        Apartment apartment = Apartment.create(
                "A-15",
                "래미안 옥수 리버젠",
                "서울특별시 성동구 매봉길 15",
                "11200",
                "성동구",
                "옥수동",
                "1120011300",
                127.018,
                37.543,
                1821,
                "2012-12",
                2100
        );
        ReflectionTestUtils.setField(apartment, "id", 15L);
        when(apartmentRepository.findAllById(List.of(15L)))
                .thenReturn(List.of(apartment));

        ApartmentTransaction transaction = ApartmentTransaction.create(
                15L,
                LocalDate.of(2026, 6, 15),
                new BigDecimal("84.95"),
                183000L,
                10,
                false,
                "deal-15"
        );
        when(transactionRepository.findLatestNormalByApartmentIds(
                List.of(15L)
        )).thenReturn(List.of(transaction));

        StudyRepository.ApartmentStudyCountRow studyCount =
                mock(StudyRepository.ApartmentStudyCountRow.class);
        when(studyCount.getApartmentId()).thenReturn(15L);
        when(studyCount.getCount()).thenReturn(2L);
        when(studyRepository.countRecruitingByApartmentIds(
                List.of(15L)
        )).thenReturn(List.of(studyCount));
        when(reportQueryRepository.countByApartmentIds(
                List.of(15L)
        )).thenReturn(List.of(new ApartmentCountRow(15L, 5L)));

        PageResponse<MemberFavoriteApartmentResponse> result =
                service.getFavoriteApartments(1L, 0, 20);

        assertThat(result.content()).hasSize(1);
        MemberFavoriteApartmentResponse item = result.content().get(0);
        assertThat(item.apartmentId()).isEqualTo(15L);
        assertThat(item.districtName()).isEqualTo("성동구");
        assertThat(item.dongName()).isEqualTo("옥수동");
        assertThat(item.householdCount()).isEqualTo(1821);
        assertThat(item.latestTransaction().price())
                .isEqualTo(183000L);
        assertThat(item.recruitingStudyCount()).isEqualTo(2);
        assertThat(item.completedReportCount()).isEqualTo(5);
        assertThat(item.favoritedAt().toString())
                .isEqualTo("2026-07-22T18:30+09:00");
    }

    @Test
    void 집계와_거래가_없으면_기본값을_반환한다() {
        ApartmentFavorite favorite = mock(ApartmentFavorite.class);
        when(favorite.getApartmentId()).thenReturn(15L);
        when(favorite.getCreatedAt()).thenReturn(
                Instant.parse("2026-07-22T09:30:00Z")
        );
        when(favoriteRepository
                .findByMemberIdOrderByCreatedAtDescIdDesc(
                        1L,
                        PageRequest.of(0, 20)
                )).thenReturn(new PageImpl<>(List.of(favorite)));

        Apartment apartment = Apartment.create(
                "A-15",
                "아파트",
                "주소",
                null,
                null,
                null,
                null,
                127.0,
                37.0,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(apartment, "id", 15L);
        when(apartmentRepository.findAllById(List.of(15L)))
                .thenReturn(List.of(apartment));
        when(transactionRepository.findLatestNormalByApartmentIds(
                List.of(15L)
        )).thenReturn(List.of());
        when(studyRepository.countRecruitingByApartmentIds(
                List.of(15L)
        )).thenReturn(List.of());
        when(reportQueryRepository.countByApartmentIds(
                List.of(15L)
        )).thenReturn(List.of());

        MemberFavoriteApartmentResponse item =
                service.getFavoriteApartments(1L, 0, 20)
                        .content().get(0);

        assertThat(item.latestTransaction()).isNull();
        assertThat(item.recruitingStudyCount()).isZero();
        assertThat(item.completedReportCount()).isZero();
    }

    @Test
    void 존재하지_않거나_탈퇴한_회원은_숨긴다() {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.empty());

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
        verify(favoriteRepository, never())
                .findByMemberIdOrderByCreatedAtDescIdDesc(
                        1L,
                        PageRequest.of(0, 20)
                );
    }

    private Member activeMember() {
        return new Member("member@example.com", "hash", "member");
    }

    private void assertMemberNotFound() {
        assertThatThrownBy(() ->
                service.getFavoriteApartments(1L, 0, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
                );
    }
}
