package com.ssafy.ssabangpalbang.fieldvisit.client;

/** 카카오 보행 경로 요청에 사용하는 WGS84 좌표다. */
public record WalkingRoutePoint(
        double latitude,
        double longitude
) {
}
