package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoWalkingRoute;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class WalkingRoutePlanTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void 실제_leg_거리와_올림_시간을_저장하고_복귀_leg는_더하지_않는다() throws Exception {
        RouteCandidate mart = candidate(FacilityType.MART, "mart", 37.001, 127.001);
        RouteCandidate bank = candidate(FacilityType.BANK, "bank", 37.002, 127.002);
        JsonNode geometry = OBJECT_MAPPER.readTree(
                "{\"type\":\"LineString\",\"coordinates\":[[127.0,37.0],[127.002,37.002]]}"
        );
        KakaoWalkingRoute walkingRoute = new KakaoWalkingRoute(
                750,
                301,
                List.of(
                        new KakaoWalkingRoute.Leg(300, 61),
                        new KakaoWalkingRoute.Leg(450, 240)
                ),
                geometry
        );

        WalkingRoutePlan plan = WalkingRoutePlan.from(
                37.0, 127.0, List.of(mart, bank), walkingRoute
        );

        assertThat(plan.totalDistanceMeters()).isEqualTo(750);
        assertThat(plan.estimatedDurationMinutes()).isEqualTo(
                6 + FacilityType.MART.stayMinutes() + FacilityType.BANK.stayMinutes()
        );
        assertThat(plan.waypoints())
                .extracting(
                        WalkingRoutePlan.Waypoint::distanceFromPreviousMeters,
                        WalkingRoutePlan.Waypoint::walkingMinutesFromPrevious
                )
                .containsExactly(tuple(300, 2), tuple(450, 4));
        assertThat(plan.geometry()).isEqualTo(geometry);
    }

    private static RouteCandidate candidate(
            FacilityType type,
            String id,
            double latitude,
            double longitude
    ) {
        return new RouteCandidate(
                type,
                new KakaoLocalPoi(id, id, "", null, latitude, longitude, 0),
                List.of(),
                0
        );
    }
}
