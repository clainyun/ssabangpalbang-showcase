package com.ssafy.ssabangpalbang.fieldvisit.geo;

/**
 * GPS 직선거리(미터) 계산기. Haversine 공식.
 */
public final class GeoDistanceCalculator {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoDistanceCalculator() {
    }

    /**
     * 두 WGS84 좌표 사이 대원 거리를 미터 단위 정수로 반환한다(반올림).
     */
    public static int distanceMeters(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {
        double lat1Rad = Math.toRadians(latitude1);
        double lat2Rad = Math.toRadians(latitude2);
        double deltaLat = Math.toRadians(latitude2 - latitude1);
        double deltaLng = Math.toRadians(longitude2 - longitude1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double clampedA = Math.max(0.0, Math.min(1.0, a));
        double c = 2 * Math.atan2(
                Math.sqrt(clampedA),
                Math.sqrt(1 - clampedA)
        );
        return (int) Math.round(EARTH_RADIUS_METERS * c);
    }
}
