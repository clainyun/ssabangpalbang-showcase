package com.ssafy.ssabangpalbang.apartment.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ApartmentDetailServiceTest {

    @Test
    void 상세_응답은_주차대수_거래_카운트와_찜을_매핑한다() {
        Apartment apartment = apartment(1511, 1830);
        ApartmentTransaction transaction =
                BeanUtils.instantiateClass(ApartmentTransaction.class);
        ReflectionTestUtils.setField(transaction, "id", 381L);
        ReflectionTestUtils.setField(transaction, "price", 245000L);
        ReflectionTestUtils.setField(
                transaction, "exclusiveArea", new BigDecimal("84.80"));
        ReflectionTestUtils.setField(
                transaction, "dealDate", LocalDate.of(2026, 7, 10));
        ReflectionTestUtils.setField(transaction, "floor", 15);

        ApartmentDetailResponse response = ApartmentDetailResponse.of(
                apartment, transaction, 2, 3, true);

        assertThat(response.parkingSpacesPerHousehold())
                .isEqualByComparingTo("1.21");
        assertThat(response.latestTransaction().transactionId()).isEqualTo(381L);
        assertThat(response.latestTransaction().priceUnit())
                .isEqualTo("TEN_THOUSAND_KRW");
        assertThat(response.latestTransactionAvailable()).isTrue();
        assertThat(response.recruitingStudyCount()).isEqualTo(2);
        assertThat(response.completedReportCount()).isEqualTo(3);
        assertThat(response.favoritedByMe()).isTrue();
    }

    @Test
    void 주차_계산_불가와_거래없음은_null과_false다() {
        assertThat(ApartmentDetailResponse.of(
                apartment(null, 1000), null, 0, 0, false)
                .parkingSpacesPerHousehold()).isNull();
        assertThat(ApartmentDetailResponse.of(
                apartment(0, 1000), null, 0, 0, false)
                .parkingSpacesPerHousehold()).isNull();
        var response = ApartmentDetailResponse.of(
                apartment(500, 1000), null, 0, 0, false);
        assertThat(response.parkingSpacesPerHousehold())
                .isEqualByComparingTo("2.00");
        assertThat(response.latestTransaction()).isNull();
        assertThat(response.latestTransactionAvailable()).isFalse();
    }

    private Apartment apartment(Integer households, Integer parking) {
        Apartment apartment = BeanUtils.instantiateClass(Apartment.class);
        ReflectionTestUtils.setField(apartment, "id", 1L);
        ReflectionTestUtils.setField(apartment, "name", "아파트");
        ReflectionTestUtils.setField(apartment, "latitude", 37.5);
        ReflectionTestUtils.setField(apartment, "longitude", 127.0);
        ReflectionTestUtils.setField(apartment, "householdCount", households);
        ReflectionTestUtils.setField(apartment, "parkingSpaceCount", parking);
        return apartment;
    }
}
