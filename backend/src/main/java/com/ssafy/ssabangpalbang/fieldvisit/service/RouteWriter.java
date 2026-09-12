package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRoute;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRouteWaypoint;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRouteWaypointItem;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitRouteRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.RouteWaypointItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.RouteWaypointRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 추천 경로·경유지·N:M 연결을 별도 트랜잭션으로 원자 저장한다. */
@Component
@RequiredArgsConstructor
public class RouteWriter {

    private final FieldVisitRouteRepository routeRepository;
    private final RouteWaypointRepository waypointRepository;
    private final RouteWaypointItemRepository waypointItemRepository;
    private final FieldSessionRepository fieldSessionRepository;
    private final FieldParticipantRepository fieldParticipantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FieldVisitRoute save(
            Long studyId,
            Long sessionId,
            Long memberId,
            WalkingRoutePlan plan
    ) {
        requireStillWritable(studyId, sessionId, memberId);
        FieldVisitRoute route = routeRepository.saveAndFlush(FieldVisitRoute.create(
                sessionId,
                memberId,
                plan.totalDistanceMeters(),
                plan.estimatedDurationMinutes(),
                plan.geometry()
        ));

        saveWaypoints(route.getId(), plan);
        return route;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FieldVisitRoute replaceLegacy(
            Long routeId,
            WalkingRoutePlan plan
    ) {
        FieldVisitRoute route = routeRepository.findByIdForUpdate(routeId)
                .orElseThrow(() -> new IllegalStateException("추천 경로를 찾을 수 없습니다."));
        if (route.getGeometry() != null) {
            return route;
        }
        List<FieldVisitRouteWaypoint> persisted = waypointRepository
                .findByRouteIdOrderBySequenceAsc(routeId);
        waypointRepository.deleteAllInBatch(persisted);
        waypointRepository.flush();
        saveWaypoints(routeId, plan);
        route.replaceWalkingRoute(
                plan.totalDistanceMeters(),
                plan.estimatedDurationMinutes(),
                plan.geometry()
        );
        routeRepository.flush();
        return route;
    }

    private void saveWaypoints(Long routeId, WalkingRoutePlan plan) {
        List<FieldVisitRouteWaypoint> waypoints = new ArrayList<>();
        for (int index = 0; index < plan.waypoints().size(); index++) {
            WalkingRoutePlan.Waypoint planned = plan.waypoints().get(index);
            RouteCandidate candidate = planned.candidate();
            waypoints.add(FieldVisitRouteWaypoint.create(
                    routeId,
                    index + 1,
                    candidate.facilityType(),
                    truncate(candidate.poi().name(), 100),
                    truncate(candidate.poi().address(), 300),
                    candidate.poi().latitude(),
                    candidate.poi().longitude(),
                    truncate(candidate.poi().id(), 50),
                    planned.distanceFromOriginMeters(),
                    planned.distanceFromPreviousMeters(),
                    planned.walkingMinutesFromPrevious(),
                    candidate.facilityType().stayMinutes(),
                    candidate.facilityType().guide()
            ));
        }
        List<FieldVisitRouteWaypoint> savedWaypoints = waypointRepository.saveAll(waypoints);
        waypointRepository.flush();

        List<FieldVisitRouteWaypointItem> links = new ArrayList<>();
        for (int index = 0; index < savedWaypoints.size(); index++) {
            Set<Long> uniqueItemIds = new HashSet<>();
            for (var checklistItem : plan.waypoints().get(index).candidate().checklistItems()) {
                if (checklistItem.getId() != null && uniqueItemIds.add(checklistItem.getId())) {
                    links.add(FieldVisitRouteWaypointItem.create(
                            savedWaypoints.get(index).getId(), checklistItem.getId()
                    ));
                }
            }
        }
        waypointItemRepository.saveAll(links);
        waypointItemRepository.flush();
    }

    private void requireStillWritable(Long studyId, Long sessionId, Long memberId) {
        FieldSession session = fieldSessionRepository.findByStudyIdForUpdate(studyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ROUTE_FIELD_VISIT_NOT_STARTED
                ));
        if (!sessionId.equals(session.getId())
                || session.getStatus() != FieldSessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_ALREADY_ENDED);
        }
        FieldParticipant participant = fieldParticipantRepository
                .findBySessionIdAndMemberIdForUpdate(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTE_GENERATE_FORBIDDEN));
        if (participant.getStatus() != FieldParticipantStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED);
        }
    }

    private static String truncate(String value, int maximumLength) {
        if (value == null || value.codePointCount(0, value.length()) <= maximumLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximumLength));
    }
}
