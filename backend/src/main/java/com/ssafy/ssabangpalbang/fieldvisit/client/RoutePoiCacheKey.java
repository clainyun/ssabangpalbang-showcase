package com.ssafy.ssabangpalbang.fieldvisit.client;

/** 동일 카카오 category라도 검색 반경이 다른 결과를 섞지 않기 위한 캐시 키다. */
public record RoutePoiCacheKey(
        Long apartmentId,
        String categoryGroupCode,
        int radiusMeters
) {
}
