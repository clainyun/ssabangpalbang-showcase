package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRouteWaypoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteWaypointRepository extends JpaRepository<FieldVisitRouteWaypoint, Long> {

    List<FieldVisitRouteWaypoint> findByRouteIdOrderBySequenceAsc(Long routeId);
}
