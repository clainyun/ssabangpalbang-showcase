package com.ssafy.ssabangpalbang.fieldvisit.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoDistanceCalculatorTest {

    @Test
    void 동일_좌표_거리는_0이다() {
        assertThat(GeoDistanceCalculator.distanceMeters(
                37.5133, 127.0842, 37.5133, 127.0842
        )).isZero();
    }

    @Test
    void 서울_근처_단거리는_미터_단위로_계산된다() {
        int meters = GeoDistanceCalculator.distanceMeters(
                37.5133, 127.0842,
                37.5142, 127.0842
        );
        assertThat(meters).isBetween(90, 110);
    }

    @Test
    void 반경_경계_비교용_거리는_양의_정수다() {
        int meters = GeoDistanceCalculator.distanceMeters(
                37.0, 127.0, 37.001, 127.0
        );
        assertThat(meters).isGreaterThan(0);
    }

    @Test
    void 거의_대척점인_좌표도_유한한_거리로_계산한다() {
        int meters = GeoDistanceCalculator.distanceMeters(
                42.196287939929164,
                -166.89525353998764,
                -42.196288053185754,
                13.10474634318678
        );

        assertThat(meters).isBetween(20_000_000, 20_020_000);
    }
}
