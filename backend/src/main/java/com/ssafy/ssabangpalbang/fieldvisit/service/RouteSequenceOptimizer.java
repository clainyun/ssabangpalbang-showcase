package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.geo.GeoDistanceCalculator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 최대 다섯 경유지의 모든 순열을 비교해 복귀 없는 최단 편도 순서를 고른다. */
@Component
public class RouteSequenceOptimizer {

    public List<RouteCandidate> optimize(
            double originLatitude,
            double originLongitude,
            List<RouteCandidate> candidates
    ) {
        List<RouteCandidate> stable = candidates.stream()
                .sorted(Comparator.comparing((RouteCandidate candidate) ->
                                candidate.facilityType().name())
                        .thenComparing(candidate -> candidate.poi().id()))
                .toList();
        if (stable.isEmpty()) {
            return List.of();
        }

        SearchState state = new SearchState();
        permute(originLatitude, originLongitude, stable, new ArrayList<>(), state);
        return state.bestOrder;
    }

    private void permute(
            double originLatitude,
            double originLongitude,
            List<RouteCandidate> remaining,
            List<RouteCandidate> current,
            SearchState state
    ) {
        if (remaining.isEmpty()) {
            int distance = openPathDistance(
                    originLatitude, originLongitude, current
            );
            if (distance < state.bestDistance) {
                state.bestDistance = distance;
                state.bestOrder = List.copyOf(current);
            }
            return;
        }
        for (int index = 0; index < remaining.size(); index++) {
            RouteCandidate next = remaining.get(index);
            List<RouteCandidate> nextRemaining = new ArrayList<>(remaining);
            nextRemaining.remove(index);
            current.add(next);
            permute(originLatitude, originLongitude, nextRemaining, current, state);
            current.remove(current.size() - 1);
        }
    }

    private int openPathDistance(
            double originLatitude,
            double originLongitude,
            List<RouteCandidate> order
    ) {
        double previousLatitude = originLatitude;
        double previousLongitude = originLongitude;
        int distance = 0;
        for (RouteCandidate candidate : order) {
            distance += GeoDistanceCalculator.distanceMeters(
                    previousLatitude, previousLongitude,
                    candidate.poi().latitude(), candidate.poi().longitude()
            );
            previousLatitude = candidate.poi().latitude();
            previousLongitude = candidate.poi().longitude();
        }
        return distance;
    }

    private static final class SearchState {

        private int bestDistance = Integer.MAX_VALUE;
        private List<RouteCandidate> bestOrder = List.of();
    }
}
