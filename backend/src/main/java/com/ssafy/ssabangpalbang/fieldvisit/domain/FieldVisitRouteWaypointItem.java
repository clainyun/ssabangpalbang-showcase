package com.ssafy.ssabangpalbang.fieldvisit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** 경유지와 체크리스트 항목의 N:M 연결이다. */
@Entity
@Table(name = "field_visit_route_waypoint_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldVisitRouteWaypointItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "waypoint_id", nullable = false)
    private Long waypointId;

    @Column(name = "checklist_item_id", nullable = false)
    private Long checklistItemId;

    public static FieldVisitRouteWaypointItem create(
            Long waypointId,
            Long checklistItemId
    ) {
        FieldVisitRouteWaypointItem item = new FieldVisitRouteWaypointItem();
        item.waypointId = Objects.requireNonNull(waypointId);
        item.checklistItemId = Objects.requireNonNull(checklistItemId);
        return item;
    }
}
