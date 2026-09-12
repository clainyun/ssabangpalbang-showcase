package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

/** GET 추천 경로 조회 응답 data다. */
public record FieldVisitRouteDetailResponse(
        Long studyId,
        boolean readOnly,
        RouteResponse route
) {

    public record RouteResponse(
            Long sessionId,
            Long routeId,
            OffsetDateTime generatedAt,
            int totalDistanceMeters,
            int estimatedDurationMinutes,
            FieldVisitRouteGenerateResponse.OriginResponse origin,
            JsonNode geometry,
            FieldVisitRouteGenerateResponse.MyProgressResponse myProgress,
            List<RouteWaypointResponse> waypoints
    ) {
    }
}
