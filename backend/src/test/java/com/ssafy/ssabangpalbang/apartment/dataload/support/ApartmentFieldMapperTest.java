package com.ssafy.ssabangpalbang.apartment.dataload.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApartmentFieldMapperTest {

    @Test
    void mapsHouseholdCount() {
        assertThat(ApartmentFieldMapper.householdCount("0")).isNull();
        assertThat(ApartmentFieldMapper.householdCount("233")).isEqualTo(233);
        assertThat(ApartmentFieldMapper.householdCount("233.0")).isEqualTo(233);
    }

    @Test
    void mapsCompletionYearMonth() {
        assertThat(ApartmentFieldMapper.completionYearMonth("20040806")).isEqualTo("2004-08");
        assertThat(ApartmentFieldMapper.completionYearMonth("2004")).isNull();
        assertThat(ApartmentFieldMapper.completionYearMonth("20041306")).isNull();
        assertThat(ApartmentFieldMapper.completionYearMonth(null)).isNull();
    }

    @Test
    void sumsParkingSpaces() {
        assertThat(ApartmentFieldMapper.parkingSpaceCount("100", "200")).isEqualTo(300);
        assertThat(ApartmentFieldMapper.parkingSpaceCount(null, null)).isNull();
        assertThat(ApartmentFieldMapper.parkingSpaceCount("100", null)).isEqualTo(100);
    }

    @Test
    void mapsLegalDongCodes() {
        assertThat(ApartmentFieldMapper.districtCode("1168010100")).isEqualTo("11680");
        assertThat(ApartmentFieldMapper.legalDongCode("1168010100")).isEqualTo("1168010100");
    }
}
