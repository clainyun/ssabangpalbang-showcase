package com.ssafy.ssabangpalbang.apartment.service;

import com.ssafy.ssabangpalbang.apartment.config.ApartmentImageProperties;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentDetailResponse;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentTransactionSearchCondition;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentTransactionResponse;
import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentTransactionRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentFavoriteRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentReportQueryRepository;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.springframework.transaction.PlatformTransactionManager;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class ApartmentServiceTest {

    @Mock
    private ApartmentRepository apartmentRepository;

    @Mock
    private ApartmentTransactionRepository apartmentTransactionRepository;
    @Mock private ApartmentFavoriteRepository apartmentFavoriteRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ApartmentReportQueryRepository apartmentReportQueryRepository;
    @Mock private StudyRepository studyRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private ApartmentService apartmentService;

    @BeforeEach
    void setUp() {
        apartmentService = new ApartmentService(
                apartmentRepository, apartmentTransactionRepository,
                apartmentFavoriteRepository, memberRepository, transactionManager);
        ReflectionTestUtils.setField(
                apartmentService, "apartmentReportQueryRepository",
                apartmentReportQueryRepository);
        ReflectionTestUtils.setField(
                apartmentService, "studyRepository", studyRepository);
        ReflectionTestUtils.setField(
                apartmentService,
                "apartmentImageUrlResolver",
                new ApartmentImageUrlResolver(
                        new ApartmentImageProperties("https://cdn.example.com")
                )
        );
        Member member = org.mockito.Mockito.mock(Member.class);
        lenient().when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        lenient().when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member));
    }

    @Test
    void returnsApartmentDetail() {
        Apartment apartment = apartment(
                1L,
                "A13245",
                "래미안강남포레스트",
                "서울특별시 강남구 개포로 310",
                "11680",
                "강남구",
                "개포동",
                127.0654,
                37.4812,
                2296,
                "2020-09",
                3100
        );
        when(apartmentRepository.findById(1L))
                .thenReturn(Optional.of(apartment));
        when(apartmentRepository.findImageObjectKeyByApartmentId(1L))
                .thenReturn(Optional.of("apartment-images/v1/A13245.webp"));

        when(apartmentTransactionRepository.findLatestNormalByApartmentIds(List.of(1L)))
                .thenReturn(List.of());
        ApartmentDetailResponse response = apartmentService.getDetail(7L, 1L);

        assertThat(response.apartmentId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("래미안강남포레스트");
        assertThat(response.latitude()).isEqualTo(37.4812);
        assertThat(response.longitude()).isEqualTo(127.0654);
        assertThat(response.imageUrl())
                .isEqualTo("https://cdn.example.com/apartment-images/v1/A13245.webp");
        assertThat(response.parkingSpacesPerHousehold())
                .isEqualByComparingTo("1.35");
        assertThat(response.recruitingStudyCount()).isZero();
        assertThat(response.completedReportCount()).isZero();
        assertThat(response.latestTransactionAvailable()).isFalse();
    }

    @Test
    void throwsWhenApartmentDoesNotExist() {
        when(apartmentRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> apartmentService.getDetail(7L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APARTMENT_NOT_FOUND);
    }

    @Test
    void convertsNullableColumnsWhenNull() {
        Apartment apartment = apartment(
                1L,
                "A13245",
                "래미안강남포레스트",
                null,
                null,
                null,
                null,
                127.0654,
                37.4812,
                null,
                null,
                null
        );
        when(apartmentRepository.findById(1L))
                .thenReturn(Optional.of(apartment));

        when(apartmentTransactionRepository.findLatestNormalByApartmentIds(List.of(1L)))
                .thenReturn(List.of());
        ApartmentDetailResponse response = apartmentService.getDetail(7L, 1L);

        assertThat(response.address()).isNull();
        assertThat(response.districtCode()).isNull();
        assertThat(response.districtName()).isNull();
        assertThat(response.dongName()).isNull();
        assertThat(response.householdCount()).isNull();
        assertThat(response.completionYearMonth()).isNull();
        assertThat(response.parkingSpaceCount()).isNull();
    }

    @Test
    void throwsNotFoundWithApartmentIdBeforeSearchingTransactions() {
        when(apartmentRepository.existsById(25L)).thenReturn(false);

        assertThatThrownBy(() -> apartmentService.getTransactions(
                25L, condition(), 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.APARTMENT_NOT_FOUND);
                    assertThat(exception.getData()).containsEntry("apartmentId", 25L);
                });
        verify(apartmentTransactionRepository, never())
                .search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void returnsEmptyTransactionPageWithoutException() {
        when(apartmentRepository.existsById(1L)).thenReturn(true);
        when(apartmentTransactionRepository.search(
                eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(), invocation.getArgument(5), 0
                ));

        PageResponse<ApartmentTransactionResponse> response =
                apartmentService.getTransactions(1L, condition(), 0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void returnsTransactionDtoPageWithMetadataAndConvertedFields() {
        ApartmentTransaction transaction = transaction();
        when(apartmentRepository.existsById(1L)).thenReturn(true);
        when(apartmentTransactionRepository.search(
                eq(1L), any(), any(), any(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(5);
                    return new PageImpl<>(
                            java.util.Collections.nCopies(12, transaction),
                            pageable,
                            12
                    );
                });

        PageResponse<ApartmentTransactionResponse> response =
                apartmentService.getTransactions(1L, condition(), 0, 20);

        assertThat(response.totalElements()).isEqualTo(12);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.page()).isZero();
        assertThat(response.content()).hasSize(12);
        ApartmentTransactionResponse item = response.content().get(0);
        assertThat(item).isInstanceOf(ApartmentTransactionResponse.class);
        assertThat(item.priceUnit()).isEqualTo("TEN_THOUSAND_KRW");
        assertThat(item.exclusiveArea()).isEqualTo(84.80);
    }

    private ApartmentTransactionSearchCondition condition() {
        return ApartmentTransactionSearchCondition.of(
                null, null, null, LocalDate.of(2026, 7, 28));
    }

    private ApartmentTransaction transaction() {
        ApartmentTransaction transaction = BeanUtils.instantiateClass(ApartmentTransaction.class);
        ReflectionTestUtils.setField(transaction, "id", 381L);
        ReflectionTestUtils.setField(transaction, "apartmentId", 1L);
        ReflectionTestUtils.setField(transaction, "dealDate", LocalDate.of(2026, 7, 10));
        ReflectionTestUtils.setField(transaction, "price", 245000L);
        ReflectionTestUtils.setField(transaction, "exclusiveArea", new BigDecimal("84.80"));
        ReflectionTestUtils.setField(transaction, "floor", 15);
        return transaction;
    }

    private Apartment apartment(
            Long id,
            String complexCode,
            String name,
            String address,
            String districtCode,
            String districtName,
            String dongName,
            Double longitude,
            Double latitude,
            Integer householdCount,
            String completionYearMonth,
            Integer parkingSpaceCount
    ) {
        Apartment apartment = BeanUtils.instantiateClass(Apartment.class);
        ReflectionTestUtils.setField(apartment, "id", id);
        ReflectionTestUtils.setField(apartment, "complexCode", complexCode);
        ReflectionTestUtils.setField(apartment, "name", name);
        ReflectionTestUtils.setField(apartment, "address", address);
        ReflectionTestUtils.setField(apartment, "districtCode", districtCode);
        ReflectionTestUtils.setField(apartment, "districtName", districtName);
        ReflectionTestUtils.setField(apartment, "dongName", dongName);
        ReflectionTestUtils.setField(apartment, "longitude", longitude);
        ReflectionTestUtils.setField(apartment, "latitude", latitude);
        ReflectionTestUtils.setField(apartment, "householdCount", householdCount);
        ReflectionTestUtils.setField(
                apartment,
                "completionYearMonth",
                completionYearMonth
        );
        ReflectionTestUtils.setField(
                apartment,
                "parkingSpaceCount",
                parkingSpaceCount
        );
        return apartment;
    }
}
