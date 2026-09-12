package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

/** POST 추천 경로 생성 응답 data다. */
public record FieldVisitRouteGenerateResponse(
        Long studyId,
        Long sessionId,
        Long routeId,
        OffsetDateTime generatedAt,
        int totalDistanceMeters,
        int estimatedDurationMinutes,
        OriginResponse origin,
        JsonNode geometry,
        MyProgressResponse myProgress,
        List<RouteWaypointResponse> waypoints
) {

    public record OriginResponse(
            Long apartmentId,
            String name,
            double latitude,
            double longitude
    ) {
    }

    public record MyProgressResponse(
            int completedWaypointCount,
            int totalWaypointCount,
            int completedItemCount,
            int totalItemCount
    ) {
    }
}
