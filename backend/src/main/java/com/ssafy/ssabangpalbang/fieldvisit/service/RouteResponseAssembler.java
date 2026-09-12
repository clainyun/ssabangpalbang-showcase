package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistAnswer;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRoute;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRouteWaypoint;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRouteWaypointItem;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitRouteDetailResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitRouteGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.RouteWaypointResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistAnswerRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.RouteWaypointItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.RouteWaypointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 저장된 공유 경로를 현재 호출자 기준으로 재조립한다. */
@Component
@RequiredArgsConstructor
public class RouteResponseAssembler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RouteWaypointRepository waypointRepository;
    private final RouteWaypointItemRepository waypointItemRepository;
    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistAnswerRepository checklistAnswerRepository;
    private final FieldRecordCountRepository fieldRecordCountRepository;

    public FieldVisitRouteGenerateResponse toGenerateResponse(
            Long studyId,
            FieldVisitRoute route,
            Apartment apartment,
            Long memberId
    ) {
        AssembledRoute assembled = assemble(route, apartment, memberId);
        return new FieldVisitRouteGenerateResponse(
                studyId,
                route.getSessionId(),
                route.getId(),
                toSeoul(route),
                route.getTotalDistanceMeters(),
                route.getEstimatedDurationMinutes(),
                assembled.origin(),
                route.getGeometry(),
                assembled.progress(),
                assembled.waypoints()
        );
    }

    public FieldVisitRouteDetailResponse.RouteResponse toDetailRoute(
            FieldVisitRoute route,
            Apartment apartment,
            Long memberId
    ) {
        AssembledRoute assembled = assemble(route, apartment, memberId);
        return new FieldVisitRouteDetailResponse.RouteResponse(
                route.getSessionId(),
                route.getId(),
                toSeoul(route),
                route.getTotalDistanceMeters(),
                route.getEstimatedDurationMinutes(),
                assembled.origin(),
                route.getGeometry(),
                assembled.progress(),
                assembled.waypoints()
        );
    }

    private AssembledRoute assemble(
            FieldVisitRoute route,
            Apartment apartment,
            Long memberId
    ) {
        List<FieldVisitRouteWaypoint> waypoints = waypointRepository
                .findByRouteIdOrderBySequenceAsc(route.getId());
        List<Long> waypointIds = waypoints.stream().map(FieldVisitRouteWaypoint::getId).toList();
        Map<Long, List<FieldVisitRouteWaypointItem>> linksByWaypoint =
                waypointItemRepository.findByWaypointIdIn(waypointIds).stream()
                        .collect(Collectors.groupingBy(
                                FieldVisitRouteWaypointItem::getWaypointId
                        ));
        Set<Long> routeItemIds = linksByWaypoint.values().stream()
                .flatMap(Collection::stream)
                .map(FieldVisitRouteWaypointItem::getChecklistItemId)
                .collect(Collectors.toSet());
        Map<Long, ChecklistItem> itemsById = checklistItemRepository
                .findAllById(routeItemIds)
                .stream()
                .collect(Collectors.toMap(ChecklistItem::getId, Function.identity()));
        Checklist ownChecklist = checklistRepository
                .findBySessionIdAndMemberId(route.getSessionId(), memberId)
                .orElse(null);
        Long ownChecklistId = ownChecklist == null ? null : ownChecklist.getId();
        List<Long> ownItemIds = itemsById.values().stream()
                .filter(item -> ownChecklistId != null
                        && ownChecklistId.equals(item.getChecklistId()))
                .map(ChecklistItem::getId)
                .distinct()
                .toList();
        Map<Long, ChecklistAnswer> answers = checklistAnswerRepository
                .findByChecklistItemIdIn(ownItemIds).stream()
                .collect(Collectors.toMap(
                        ChecklistAnswer::getChecklistItemId, Function.identity()
                ));
        Map<Long, Integer> recordCounts = fieldRecordCountRepository
                .countByAuthorAndItemIds(memberId, ownItemIds);

        List<RouteWaypointResponse> responses = new ArrayList<>();
        int completedWaypointCount = 0;
        for (FieldVisitRouteWaypoint waypoint : waypoints) {
            List<ChecklistItem> ownItems = linksByWaypoint
                    .getOrDefault(waypoint.getId(), List.of())
                    .stream()
                    .map(FieldVisitRouteWaypointItem::getChecklistItemId)
                    .map(itemsById::get)
                    .filter(item -> item != null && ownChecklistId != null
                            && ownChecklistId.equals(item.getChecklistId()))
                    .sorted(Comparator.comparing(ChecklistItem::getDisplayOrder)
                            .thenComparing(ChecklistItem::getId))
                    .toList();
            List<RouteWaypointResponse.RouteChecklistItemResponse> myItems = ownItems.stream()
                    .map(item -> toItemResponse(item, answers, recordCounts))
                    .toList();
            int completedForWaypoint = (int) myItems.stream()
                    .filter(RouteWaypointResponse.RouteChecklistItemResponse::isCompleted)
                    .count();
            if (completedForWaypoint == myItems.size()) {
                completedWaypointCount++;
            }
            responses.add(new RouteWaypointResponse(
                    waypoint.getId(),
                    waypoint.getSequence(),
                    waypoint.getFacilityType().name(),
                    waypoint.getName(),
                    waypoint.getAddress(),
                    waypoint.getLatitude(),
                    waypoint.getLongitude(),
                    waypoint.getKakaoPlaceId(),
                    waypoint.getDistanceFromOriginM(),
                    waypoint.getDistanceFromPrevM(),
                    waypoint.getWalkMinutesFromPrev(),
                    waypoint.getStayMinutes(),
                    waypoint.getGuide(),
                    myItems,
                    myItems.size(),
                    linksByWaypoint.getOrDefault(waypoint.getId(), List.of()).size()
            ));
        }
        return new AssembledRoute(
                new FieldVisitRouteGenerateResponse.OriginResponse(
                        apartment.getId(), apartment.getName(), apartment.getLatitude(),
                        apartment.getLongitude()
                ),
                new FieldVisitRouteGenerateResponse.MyProgressResponse(
                        completedWaypointCount,
                        waypoints.size(),
                        (int) ownItemIds.stream()
                                .filter(itemId -> isCompleted(answers.get(itemId)))
                                .count(),
                        ownItemIds.size()
                ),
                List.copyOf(responses)
        );
    }

    private static RouteWaypointResponse.RouteChecklistItemResponse toItemResponse(
            ChecklistItem item,
            Map<Long, ChecklistAnswer> answers,
            Map<Long, Integer> recordCounts
    ) {
        ChecklistAnswer answer = answers.get(item.getId());
        return new RouteWaypointResponse.RouteChecklistItemResponse(
                item.getId(),
                item.getCategory(),
                item.getTitle(),
                item.getSubtitle(),
                isCompleted(answer),
                recordCounts.getOrDefault(item.getId(), 0)
        );
    }

    private static boolean isCompleted(ChecklistAnswer answer) {
        return answer != null && answer.isCompleted();
    }

    private static OffsetDateTime toSeoul(FieldVisitRoute route) {
        return route.getGeneratedAt().atZone(SEOUL).toOffsetDateTime();
    }

    private record AssembledRoute(
            FieldVisitRouteGenerateResponse.OriginResponse origin,
            FieldVisitRouteGenerateResponse.MyProgressResponse progress,
            List<RouteWaypointResponse> waypoints
    ) {
    }
}
