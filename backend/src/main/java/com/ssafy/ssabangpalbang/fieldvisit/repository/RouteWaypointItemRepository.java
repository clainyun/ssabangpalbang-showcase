package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRouteWaypointItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RouteWaypointItemRepository
        extends JpaRepository<FieldVisitRouteWaypointItem, Long> {

    List<FieldVisitRouteWaypointItem> findByWaypointIdIn(Collection<Long> waypointIds);
}
