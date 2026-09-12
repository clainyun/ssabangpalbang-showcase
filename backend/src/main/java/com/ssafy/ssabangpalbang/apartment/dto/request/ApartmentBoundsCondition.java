package com.ssafy.ssabangpalbang.apartment.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.util.Map;

public record ApartmentBoundsCondition(
        Double southWestLat,
        Double southWestLng,
        Double northEastLat,
        Double northEastLng
) {
    private static final double MAX_SPAN = 0.5;

    public static ApartmentBoundsCondition of(
            Double southWestLat,
            Double southWestLng,
            Double northEastLat,
            Double northEastLng
    ) {
        if (southWestLat == null || southWestLng == null
                || northEastLat == null || northEastLng == null) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_BOUNDS_REQUIRED,
                    Map.of("reason", "남서쪽과 북동쪽 좌표가 모두 필요합니다.")
            );
        }
        if (!validLatitude(southWestLat) || !validLatitude(northEastLat)
                || !validLongitude(southWestLng) || !validLongitude(northEastLng)) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_BOUNDS_COORDINATE_INVALID,
                    Map.of("reason", "위도는 -90~90, 경도는 -180~180 범위의 유한한 값이어야 합니다.")
            );
        }
        if (southWestLat >= northEastLat || southWestLng >= northEastLng) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_BOUNDS_ORDER_INVALID,
                    Map.of("reason", "남서쪽 좌표는 북동쪽 좌표보다 작아야 합니다.")
            );
        }
        if (northEastLat - southWestLat > MAX_SPAN
                || northEastLng - southWestLng > MAX_SPAN) {
            throw new BusinessException(ErrorCode.APARTMENT_BOUNDS_TOO_LARGE);
        }
        return new ApartmentBoundsCondition(
                southWestLat, southWestLng, northEastLat, northEastLng
        );
    }

    private static boolean validLatitude(double value) {
        return Double.isFinite(value) && value >= -90 && value <= 90;
    }

    private static boolean validLongitude(double value) {
        return Double.isFinite(value) && value >= -180 && value <= 180;
    }
}
