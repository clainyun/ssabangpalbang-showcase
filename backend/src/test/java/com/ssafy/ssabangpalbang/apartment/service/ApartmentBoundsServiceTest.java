package com.ssafy.ssabangpalbang.apartment.service;

import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentBoundsCondition;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentBoundsQueryRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentBoundsRow;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentCountRow;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentFavoriteRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentTransactionRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentReportQueryRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApartmentBoundsServiceTest {

    private ApartmentBoundsQueryRepository boundsRepository;
    private ApartmentFavoriteRepository favoriteRepository;
    private MemberRepository memberRepository;
    private StudyRepository studyRepository;
    private ApartmentReportQueryRepository reportRepository;
    private ApartmentService service;
    private final ApartmentBoundsCondition condition =
            ApartmentBoundsCondition.of(37.46, 127.0, 37.54, 127.13);

    @BeforeEach
    void setUp() {
        boundsRepository = mock(ApartmentBoundsQueryRepository.class);
        favoriteRepository = mock(ApartmentFavoriteRepository.class);
        memberRepository = mock(MemberRepository.class);
        studyRepository = mock(StudyRepository.class);
        reportRepository = mock(ApartmentReportQueryRepository.class);
        service = new ApartmentService(
                mock(ApartmentRepository.class),
                mock(ApartmentTransactionRepository.class)
        );
        ReflectionTestUtils.setField(service, "apartmentBoundsQueryRepository", boundsRepository);
        ReflectionTestUtils.setField(service, "apartmentFavoriteRepository", favoriteRepository);
        ReflectionTestUtils.setField(service, "memberRepository", memberRepository);
        ReflectionTestUtils.setField(service, "studyRepository", studyRepository);
        ReflectionTestUtils.setField(
                service, "apartmentReportQueryRepository", reportRepository);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(new Member("member@test.com", "hash", "member")));
    }

    @Test
    void 아파트별_최근_거래와_집계를_조립한다() {
        List<ApartmentBoundsRow> apartments = List.of(
                new ApartmentBoundsRow(
                        1L, "아파트1", "주소1", 37.5, 127.1,
                        183000L, new BigDecimal("84.95"), LocalDate.of(2026, 6, 15)
                ),
                new ApartmentBoundsRow(
                        2L, "아파트2", null, 37.51, 127.11,
                        92000L, new BigDecimal("59.90"), LocalDate.of(2026, 6, 10)
                )
        );
        List<Long> ids = List.of(1L, 2L);
        when(boundsRepository.findApartments(condition)).thenReturn(apartments);
        StudyRepository.ApartmentStudyCountRow studyCount =
                mock(StudyRepository.ApartmentStudyCountRow.class);
        when(studyCount.getApartmentId()).thenReturn(1L);
        when(studyCount.getCount()).thenReturn(2L);
        when(studyRepository.countRecruitingByApartmentIds(ids))
                .thenReturn(List.of(studyCount));
        when(reportRepository.countByApartmentIds(ids))
                .thenReturn(List.of(new ApartmentCountRow(1L, 1)));
        when(favoriteRepository.findFavoritedApartmentIds(1L, ids))
                .thenReturn(List.of(1L));

        var response = service.getBounds(1L, condition);

        assertThat(response.count()).isEqualTo(2);
        assertThat(response.apartments().get(0).latestTransaction().exclusiveArea())
                .isEqualByComparingTo("84.95");
        assertThat(response.apartments().get(0).latestTransactionAvailable()).isTrue();
        assertThat(response.apartments().get(0).recruitingStudyCount()).isEqualTo(2);
        assertThat(response.apartments().get(0).completedReportCount()).isEqualTo(1);
        assertThat(response.apartments().get(0).favoritedByMe()).isTrue();
        assertThat(response.apartments().get(1).latestTransaction().price()).isEqualTo(92000L);
        assertThat(response.apartments().get(1).latestTransactionAvailable()).isTrue();
        assertThat(response.apartments().get(1).recruitingStudyCount()).isZero();
        assertThat(response.apartments().get(1).completedReportCount()).isZero();
        assertThat(response.apartments().get(1).favoritedByMe()).isFalse();
        verify(studyRepository).countRecruitingByApartmentIds(ids);
        verify(reportRepository).countByApartmentIds(ids);
        verify(favoriteRepository).findFavoritedApartmentIds(1L, ids);
    }

    @Test
    void 최근_거래가_없는_아파트도_집계_0으로_포함한다() {
        var apartment = new ApartmentBoundsRow(
                3L, "거래 없음", "주소", 37.5, 127.1,
                null, null, null
        );
        List<Long> ids = List.of(3L);
        when(boundsRepository.findApartments(condition)).thenReturn(List.of(apartment));
        when(studyRepository.countRecruitingByApartmentIds(ids)).thenReturn(List.of());
        when(reportRepository.countByApartmentIds(ids)).thenReturn(List.of());
        when(favoriteRepository.findFavoritedApartmentIds(1L, ids)).thenReturn(List.of());

        var response = service.getBounds(1L, condition);

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.apartments().get(0).latestTransaction()).isNull();
        assertThat(response.apartments().get(0).latestTransactionAvailable()).isFalse();
        assertThat(response.apartments().get(0).recruitingStudyCount()).isZero();
        assertThat(response.apartments().get(0).completedReportCount()).isZero();
    }

    @Test
    void 빈_결과면_후속_배치쿼리를_호출하지_않는다() {
        when(boundsRepository.findApartments(condition)).thenReturn(List.of());

        var response = service.getBounds(1L, condition);

        assertThat(response.apartments()).isEmpty();
        assertThat(response.count()).isZero();
        verify(studyRepository, never()).countRecruitingByApartmentIds(List.of());
        verify(reportRepository, never()).countByApartmentIds(List.of());
        verify(favoriteRepository, never()).findFavoritedApartmentIds(1L, List.of());
    }

    @Test
    void 활성_회원이_아니면_MEMBER_NOT_FOUND다() {
        Member withdrawn = new Member("withdrawn@test.com", "hash", "withdrawn");
        ReflectionTestUtils.setField(withdrawn, "status", MemberStatus.WITHDRAWN);
        when(memberRepository.findById(2L)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.getBounds(2L, condition))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void 삭제된_회원이면_MEMBER_NOT_FOUND다() {
        Member deleted = new Member("deleted@test.com", "hash", "deleted");
        ReflectionTestUtils.setField(deleted, "deletedAt", Instant.parse("2026-08-04T00:00:00Z"));
        when(memberRepository.findById(3L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.getBounds(3L, condition))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }
}
