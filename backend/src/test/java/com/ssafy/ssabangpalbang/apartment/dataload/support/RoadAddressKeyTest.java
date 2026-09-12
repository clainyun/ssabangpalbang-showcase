package com.ssafy.ssabangpalbang.apartment.dataload.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoadAddressKeyTest {

    @Test
    void normalizesFullRoadAddresses() {
        assertThat(RoadAddressKey.fromFullAddress("서울특별시 강남구 테헤란로48길 10"))
                .isEqualTo("테헤란로48길|10|0");
        assertThat(RoadAddressKey.fromFullAddress("서울특별시 강남구 학동로 432"))
                .isEqualTo("학동로|432|0");
        assertThat(RoadAddressKey.fromFullAddress("경기도 성남시 분당구 판교로 100-2"))
                .isEqualTo("판교로|100|2");
    }

    @Test
    void returnsEmptyForNonRoadOrMissingFullAddress() {
        assertThat(RoadAddressKey.fromFullAddress("서울특별시 강남구 도곡동 953")).isEmpty();
        assertThat(RoadAddressKey.fromFullAddress(null)).isEmpty();
        assertThat(RoadAddressKey.fromFullAddress("")).isEmpty();
    }

    @Test
    void normalizesRoadAddressParts() {
        assertThat(RoadAddressKey.fromParts("학동로", "00432", "00000"))
                .isEqualTo("학동로|432|0");
        assertThat(RoadAddressKey.fromParts("광평로47길", "00017", "00003"))
                .isEqualTo("광평로47길|17|3");
        assertThat(RoadAddressKey.fromParts("학동로", "", "")).isEmpty();
    }

    @Test
    void fullAddressAndPartsProduceSameKey() {
        assertThat(RoadAddressKey.fromFullAddress("서울특별시 강남구 학동로 432"))
                .isEqualTo(RoadAddressKey.fromParts("학동로", "00432", "00000"));
    }
}
