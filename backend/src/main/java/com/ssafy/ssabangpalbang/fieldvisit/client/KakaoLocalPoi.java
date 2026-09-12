package com.ssafy.ssabangpalbang.fieldvisit.client;

/** 카카오 로컬 카테고리 검색에서 경로에 필요한 필드만 정규화한 값이다. */
public record KakaoLocalPoi(
        String id,
        String name,
        String categoryName,
        String address,
        double latitude,
        double longitude,
        int distanceMeters
) {
}
