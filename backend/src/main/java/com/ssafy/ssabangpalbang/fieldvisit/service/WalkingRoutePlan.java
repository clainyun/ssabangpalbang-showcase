package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoWalkingRoute;
import com.ssafy.ssabangpalbang.fieldvisit.geo.GeoDistanceCalculator;

import java.util.ArrayList;
import java.util.List;

/** 편도 방문 순서에 카카오 보행 leg와 GeoJSON을 결합한 저장 모델이다. */
public record WalkingRoutePlan(
        List<Waypoint> waypoints,
        int totalDistanceMeters,
        int estimatedDurationMinutes,
        JsonNode geometry
) {

    public WalkingRoutePlan {
        waypoints = List.copyOf(waypoints);
        geometry = geometry.deepCopy();
    }

    public static WalkingRoutePlan from(
            double originLatitude,
            double originLongitude,
            List<RouteCandidate> orderedCandidates,
            KakaoWalkingRoute walkingRoute
    ) {
        if (orderedCandidates.size() != walkingRoute.legs().size()) {
            throw new IllegalArgumentException("보행 경로 leg 수가 경유지 수와 다릅니다.");
        }
        List<Waypoint> waypoints = new ArrayList<>();
        int stayMinutes = 0;
        for (int index = 0; index < orderedCandidates.size(); index++) {
            RouteCandidate candidate = orderedCandidates.get(index);
            KakaoWalkingRoute.Leg leg = walkingRoute.legs().get(index);
            waypoints.add(new Waypoint(
                    candidate,
                    GeoDistanceCalculator.distanceMeters(
                            originLatitude,
                            originLongitude,
                            candidate.poi().latitude(),
                            candidate.poi().longitude()
                    ),
                    leg.distanceMeters(),
                    ceilMinutes(leg.timeSeconds())
            ));
            stayMinutes += candidate.facilityType().stayMinutes();
        }
        return new WalkingRoutePlan(
                waypoints,
                walkingRoute.totalDistanceMeters(),
                ceilMinutes(walkingRoute.totalTimeSeconds()) + stayMinutes,
                walkingRoute.geometry()
        );
    }

    private static int ceilMinutes(int seconds) {
        return (int) Math.ceil(seconds / 60.0);
    }

    public record Waypoint(
            RouteCandidate candidate,
            int distanceFromOriginMeters,
            int distanceFromPreviousMeters,
            int walkingMinutesFromPrevious
    ) {
    }
}
