package com.ssafy.ssabangpalbang.apartment.service;

import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentSearchCondition;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentSearchMode;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentFavoriteRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentSearchQueryRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentSearchRow;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentTransactionRepository;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApartmentSearchServiceTest {

    private ApartmentSearchQueryRepository searchRepository;
    private ApartmentTransactionRepository transactionRepository;
    private ApartmentFavoriteRepository favoriteRepository;
    private ApartmentService service;

    @BeforeEach
    void setUp() {
        ApartmentRepository apartmentRepository = mock(ApartmentRepository.class);
        transactionRepository = mock(ApartmentTransactionRepository.class);
        favoriteRepository = mock(ApartmentFavoriteRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        searchRepository = mock(ApartmentSearchQueryRepository.class);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(new Member("member@test.com", "hash", "member")));
        service = new ApartmentService(apartmentRepository, transactionRepository);
        ReflectionTestUtils.setField(service, "apartmentFavoriteRepository", favoriteRepository);
        ReflectionTestUtils.setField(service, "memberRepository", memberRepository);
        ReflectionTestUtils.setField(service, "apartmentSearchQueryRepository", searchRepository);
    }

    @Test
    void 빈_결과는_성공이며_배치쿼리를_호출하지_않는다() {
        ApartmentSearchCondition condition = keywordCondition();
        when(searchRepository.search(condition)).thenReturn(new PageImpl<>(List.of()));

        var response = service.search(1L, condition);

        assertThat(response.searchMode()).isEqualTo(ApartmentSearchMode.KEYWORD);
        assertThat(response.currentLocation()).isNull();
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        verify(transactionRepository, never()).findLatestNormalByApartmentIds(List.of());
        verify(favoriteRepository, never()).findFavoritedApartmentIds(1L, List.of());
    }

    @Test
    void 거래와_찜을_각각_한번만_배치조회한다() {
        ApartmentSearchCondition condition = keywordCondition();
        List<ApartmentSearchRow> rows = java.util.stream.LongStream.rangeClosed(1, 10)
                .mapToObj(id -> new ApartmentSearchRow(
                        id, "아파트" + id, "주소", "강남구", "역삼동",
                        37.5010, 127.0396, null))
                .toList();
        when(searchRepository.search(condition)).thenReturn(new PageImpl<>(rows));
        List<Long> ids = rows.stream().map(ApartmentSearchRow::apartmentId).toList();
        when(transactionRepository.findLatestNormalByApartmentIds(ids)).thenReturn(List.of());
        when(favoriteRepository.findFavoritedApartmentIds(1L, ids)).thenReturn(List.of(2L));

        var response = service.search(1L, condition);

        assertThat(response.content()).hasSize(10);
        assertThat(response.content().get(1).favoritedByMe()).isTrue();
        assertThat(response.content().get(0).latestTransaction()).isNull();
        assertThat(response.content().get(0).latestTransactionAvailable()).isFalse();
        verify(transactionRepository).findLatestNormalByApartmentIds(ids);
        verify(favoriteRepository).findFavoritedApartmentIds(1L, ids);
    }

    private ApartmentSearchCondition keywordCondition() {
        return ApartmentSearchCondition.of(
                "래미안", null, null, null, null, null, null, null);
    }
}
