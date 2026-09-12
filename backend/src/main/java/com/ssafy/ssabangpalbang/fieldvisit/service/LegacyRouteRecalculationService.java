package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoiClient;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoiUnavailableException;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitRouteProperties;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRoute;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** geometry가 없는 V16 경로를 응답 전에 새 편도 보행 계약으로 원자 갱신한다. */
@Component
@RequiredArgsConstructor
public class LegacyRouteRecalculationService {

    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistItemFacilityMapper facilityMapper;
    private final KakaoLocalPoiClient poiClient;
    private final RoutePoiSelector poiSelector;
    private final WalkingRoutePlanner walkingRoutePlanner;
    private final RouteWriter routeWriter;
    private final FieldVisitRouteProperties properties;

    public FieldVisitRoute ensureWalkingRoute(
            FieldVisitRoute route,
            Apartment apartment
    ) {
        if (route.getGeometry() != null) {
            return route;
        }
        List<ChecklistItem> sessionItems = checklistItemRepository
                .findBySessionIdOrderByChecklistIdAndDisplayOrder(route.getSessionId());
        Map<FacilityType, List<ChecklistItem>> mappedItems = facilityMapper.map(sessionItems);
        if (mappedItems.isEmpty()) {
            throw new BusinessException(ErrorCode.ROUTE_NOT_APPLICABLE);
        }
        Map<FacilityType, Optional<KakaoLocalPoi>> pois = new LinkedHashMap<>();
        try {
            for (FacilityType type : mappedItems.keySet()) {
                pois.put(type, poiClient.findNearest(
                        type,
                        apartment.getId(),
                        apartment.getLatitude(),
                        apartment.getLongitude()
                ));
            }
        } catch (KakaoLocalPoiUnavailableException exception) {
            throw new BusinessException(ErrorCode.ROUTE_POI_UNAVAILABLE);
        }
        List<RouteCandidate> candidates = poiSelector.select(
                apartment.getLatitude(),
                apartment.getLongitude(),
                mappedItems,
                pois
        );
        if (candidates.size() < properties.getMinWaypoints()) {
            throw new BusinessException(ErrorCode.ROUTE_NOT_APPLICABLE);
        }
        WalkingRoutePlan plan = walkingRoutePlanner.plan(
                apartment.getLatitude(), apartment.getLongitude(), candidates
        );
        return routeWriter.replaceLegacy(route.getId(), plan);
    }
}
