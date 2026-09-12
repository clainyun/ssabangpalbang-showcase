package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteSequenceOptimizerTest {

    private final RouteSequenceOptimizer optimizer =
            new RouteSequenceOptimizer();

    @Test
    void 모든_순열에서_복귀_없는_편도_거리가_최소인_순서를_선택한다() {
        RouteCandidate east = candidate(FacilityType.MART, "east", 37.0, 127.01);
        RouteCandidate northEast = candidate(FacilityType.BANK, "north-east", 37.01, 127.01);
        RouteCandidate north = candidate(FacilityType.HOSPITAL, "north", 37.01, 127.0);

        List<RouteCandidate> order = optimizer.optimize(
                37.0, 127.0, List.of(northEast, east, north)
        );

        assertThat(order).extracting(candidate -> candidate.poi().id())
                .containsExactly("east", "north-east", "north");
    }

    @Test
    void 같은_거리의_경로도_안정적인_시설_이름_순서를_고른다() {
        RouteCandidate mart = candidate(FacilityType.MART, "mart", 37.0, 127.01);
        RouteCandidate bank = candidate(FacilityType.BANK, "bank", 37.0, 126.99);

        List<RouteCandidate> order = optimizer.optimize(
                37.0, 127.0, List.of(mart, bank)
        );

        assertThat(order).extracting(RouteCandidate::facilityType)
                .containsExactly(FacilityType.BANK, FacilityType.MART);
    }

    private static RouteCandidate candidate(
            FacilityType type,
            String id,
            double latitude,
            double longitude
    ) {
        return new RouteCandidate(
                type,
                new KakaoLocalPoi(id, id, "", null, latitude, longitude, 100),
                List.of(),
                100
        );
    }
}
