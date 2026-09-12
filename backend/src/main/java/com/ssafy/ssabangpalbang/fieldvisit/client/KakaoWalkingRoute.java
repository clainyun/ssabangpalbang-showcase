package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** 카카오 보행 응답에서 저장·노출에 필요한 검증 완료 값이다. */
public record KakaoWalkingRoute(
        int totalDistanceMeters,
        int totalTimeSeconds,
        List<Leg> legs,
        JsonNode geometry
) {

    public KakaoWalkingRoute {
        legs = List.copyOf(legs);
        geometry = geometry.deepCopy();
    }

    public record Leg(
            int distanceMeters,
            int timeSeconds
    ) {
    }
}
