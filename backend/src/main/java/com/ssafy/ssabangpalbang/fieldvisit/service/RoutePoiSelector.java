package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitRouteProperties;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import com.ssafy.ssabangpalbang.fieldvisit.geo.GeoDistanceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** POI 후보의 시설 간 인접 병합과 상위 경유지 절단을 담당한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoutePoiSelector {

    private final FieldVisitRouteProperties properties;

    public List<RouteCandidate> select(
            double originLatitude,
            double originLongitude,
            Map<FacilityType, List<ChecklistItem>> mappedItems,
            Map<FacilityType, Optional<KakaoLocalPoi>> poiByType
    ) {
        List<RouteCandidate> candidates = new ArrayList<>();
        for (FacilityType type : FacilityType.values()) {
            List<ChecklistItem> items = mappedItems.get(type);
            Optional<KakaoLocalPoi> poi = poiByType.get(type);
            if (items == null || items.isEmpty() || poi == null || poi.isEmpty()) {
                if (items != null && !items.isEmpty()) {
                    log.debug("Route candidate excluded. reason=POI_NOT_FOUND, type={}", type);
                }
                continue;
            }
            KakaoLocalPoi found = poi.get();
            int distance = GeoDistanceCalculator.distanceMeters(
                    originLatitude, originLongitude, found.latitude(), found.longitude()
            );
            candidates.add(new RouteCandidate(type, found, items, distance));
        }

        List<RouteCandidate> merged = mergeNearby(candidates);
        List<RouteCandidate> selected = merged.stream()
                .sorted(selectionOrder())
                .limit(properties.getMaxWaypoints())
                .toList();
        if (merged.size() > selected.size()) {
            log.debug(
                    "Route candidates excluded. reason=TRIMMED_BY_LIMIT, count={}",
                    merged.size() - selected.size()
            );
        }
        return selected;
    }

    private List<RouteCandidate> mergeNearby(List<RouteCandidate> candidates) {
        int size = candidates.size();
        DisjointSet groups = new DisjointSet(size);
        for (int left = 0; left < size; left++) {
            for (int right = left + 1; right < size; right++) {
                RouteCandidate leftCandidate = candidates.get(left);
                RouteCandidate rightCandidate = candidates.get(right);
                int distance = GeoDistanceCalculator.distanceMeters(
                        leftCandidate.poi().latitude(), leftCandidate.poi().longitude(),
                        rightCandidate.poi().latitude(), rightCandidate.poi().longitude()
                );
                if (distance <= properties.getWaypointMergeMeters()) {
                    groups.union(left, right);
                }
            }
        }

        Map<Integer, List<RouteCandidate>> byGroup = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            byGroup.computeIfAbsent(groups.find(index), ignored -> new ArrayList<>())
                    .add(candidates.get(index));
        }
        return byGroup.values().stream()
                .map(this::mergeGroup)
                .toList();
    }

    private RouteCandidate mergeGroup(List<RouteCandidate> group) {
        RouteCandidate representative = group.stream()
                .sorted(selectionOrder())
                .findFirst()
                .orElseThrow();
        List<ChecklistItem> items = distinctItems(group);
        if (group.size() > 1) {
            log.debug(
                    "Route candidates merged. reason=NEARBY_MERGE, representative={}, count={}",
                    representative.facilityType(),
                    group.size()
            );
        }
        return new RouteCandidate(
                representative.facilityType(),
                representative.poi(),
                items,
                representative.distanceFromOriginMeters()
        );
    }

    private static List<ChecklistItem> distinctItems(
            Collection<RouteCandidate> candidates
    ) {
        Map<Long, ChecklistItem> itemsById = new LinkedHashMap<>();
        Set<ChecklistItem> nullIdItems = new HashSet<>();
        List<ChecklistItem> withoutId = new ArrayList<>();
        for (RouteCandidate candidate : candidates) {
            for (ChecklistItem item : candidate.checklistItems()) {
                if (item.getId() == null) {
                    if (nullIdItems.add(item)) {
                        withoutId.add(item);
                    }
                } else {
                    itemsById.putIfAbsent(item.getId(), item);
                }
            }
        }
        List<ChecklistItem> result = new ArrayList<>(itemsById.values());
        result.addAll(withoutId);
        return result;
    }

    private static Comparator<RouteCandidate> selectionOrder() {
        return Comparator.comparingInt((RouteCandidate candidate) ->
                        candidate.checklistItems().size()).reversed()
                .thenComparingInt(RouteCandidate::distanceFromOriginMeters)
                .thenComparing(candidate -> candidate.facilityType().name())
                .thenComparing(candidate -> candidate.poi().id());
    }

    private static final class DisjointSet {

        private final int[] parents;

        private DisjointSet(int size) {
            this.parents = new int[size];
            for (int index = 0; index < size; index++) {
                parents[index] = index;
            }
        }

        private int find(int value) {
            if (parents[value] != value) {
                parents[value] = find(parents[value]);
            }
            return parents[value];
        }

        private void union(int left, int right) {
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot != rightRoot) {
                parents[rightRoot] = leftRoot;
            }
        }
    }
}
