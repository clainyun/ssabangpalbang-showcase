package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoWalkingRoute;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoWalkingRouteClient;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoWalkingRouteUnavailableException;
import com.ssafy.ssabangpalbang.fieldvisit.client.WalkingRoutePoint;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 직선거리 편도 순서를 한 번의 카카오 보행 호출로 실제 경로로 확정한다. */
@Component
@RequiredArgsConstructor
public class WalkingRoutePlanner {

    private final RouteSequenceOptimizer sequenceOptimizer;
    private final KakaoWalkingRouteClient walkingRouteClient;

    public WalkingRoutePlan plan(
            double originLatitude,
            double originLongitude,
            List<RouteCandidate> candidates
    ) {
        List<RouteCandidate> ordered = sequenceOptimizer.optimize(
                originLatitude, originLongitude, candidates
        );
        try {
            KakaoWalkingRoute walkingRoute = walkingRouteClient.findRoute(
                    new WalkingRoutePoint(originLatitude, originLongitude),
                    ordered.stream()
                            .map(candidate -> new WalkingRoutePoint(
                                    candidate.poi().latitude(),
                                    candidate.poi().longitude()
                            ))
                            .toList()
            );
            return WalkingRoutePlan.from(
                    originLatitude, originLongitude, ordered, walkingRoute
            );
        } catch (KakaoWalkingRouteUnavailableException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.ROUTE_WALKING_UNAVAILABLE);
        }
    }
}
