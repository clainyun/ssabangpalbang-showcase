package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoWalkingRoute;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoWalkingRouteClient;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoWalkingRouteUnavailableException;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalkingRoutePlannerTest {

    @Test
    void 선택된_전체_편도_순서를_보행_API_한번으로_확정한다() {
        RouteSequenceOptimizer optimizer = mock(RouteSequenceOptimizer.class);
        KakaoWalkingRouteClient client = mock(KakaoWalkingRouteClient.class);
        WalkingRoutePlanner planner = new WalkingRoutePlanner(optimizer, client);
        RouteCandidate first = candidate(FacilityType.MART, "mart", 37.001, 127.001);
        RouteCandidate second = candidate(FacilityType.BANK, "bank", 37.002, 127.002);
        when(optimizer.optimize(anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(first, second));
        when(client.findRoute(any(), any())).thenReturn(new KakaoWalkingRoute(
                700,
                300,
                List.of(
                        new KakaoWalkingRoute.Leg(300, 120),
                        new KakaoWalkingRoute.Leg(400, 180)
                ),
                JsonNodeFactory.instance.objectNode()
                        .put("type", "LineString")
                        .set("coordinates", JsonNodeFactory.instance.arrayNode()
                                .add(JsonNodeFactory.instance.arrayNode().add(127.0).add(37.0))
                                .add(JsonNodeFactory.instance.arrayNode().add(127.002).add(37.002)))
        ));

        WalkingRoutePlan plan = planner.plan(
                37.0, 127.0, List.of(second, first)
        );

        assertThat(plan.waypoints()).extracting(point -> point.candidate().poi().id())
                .containsExactly("mart", "bank");
        verify(client, times(1)).findRoute(any(), any());
    }

    @Test
    void 보행_API_실패는_503_비즈니스_오류로_변환한다() {
        RouteSequenceOptimizer optimizer = mock(RouteSequenceOptimizer.class);
        KakaoWalkingRouteClient client = mock(KakaoWalkingRouteClient.class);
        WalkingRoutePlanner planner = new WalkingRoutePlanner(optimizer, client);
        RouteCandidate candidate = candidate(FacilityType.MART, "mart", 37.001, 127.001);
        when(optimizer.optimize(anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(candidate));
        when(client.findRoute(any(), any())).thenThrow(
                new KakaoWalkingRouteUnavailableException("unavailable")
        );

        assertThatThrownBy(() -> planner.plan(37.0, 127.0, List.of(candidate)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ROUTE_WALKING_UNAVAILABLE);
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
