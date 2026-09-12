package com.ssafy.ssabangpalbang.fieldvisit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** 추천 경로의 실제 방문 경유지다. 출발 아파트는 저장하지 않는다. */
@Entity
@Table(name = "field_visit_route_waypoint")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldVisitRouteWaypoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "facility_type", nullable = false, length = 30)
    private FacilityType facilityType;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "kakao_place_id", length = 50)
    private String kakaoPlaceId;

    @Column(name = "distance_from_origin_m", nullable = false)
    private int distanceFromOriginM;

    @Column(name = "distance_from_prev_m", nullable = false)
    private int distanceFromPrevM;

    @Column(name = "walk_minutes_from_prev", nullable = false)
    private int walkMinutesFromPrev;

    @Column(name = "stay_minutes", nullable = false)
    private int stayMinutes;

    @Column(columnDefinition = "TEXT")
    private String guide;

    public static FieldVisitRouteWaypoint create(
            Long routeId,
            int sequence,
            FacilityType facilityType,
            String name,
            String address,
            double latitude,
            double longitude,
            String kakaoPlaceId,
            int distanceFromOriginM,
            int distanceFromPrevM,
            int walkMinutesFromPrev,
            int stayMinutes,
            String guide
    ) {
        if (sequence < 1 || name == null || name.isBlank()) {
            throw new IllegalArgumentException("경유지 순서와 이름은 유효해야 합니다.");
        }
        if (distanceFromOriginM < 0 || distanceFromPrevM < 0
                || walkMinutesFromPrev < 0 || stayMinutes < 0) {
            throw new IllegalArgumentException("경유지 거리와 시간은 음수일 수 없습니다.");
        }
        FieldVisitRouteWaypoint waypoint = new FieldVisitRouteWaypoint();
        waypoint.routeId = Objects.requireNonNull(routeId);
        waypoint.sequence = sequence;
        waypoint.facilityType = Objects.requireNonNull(facilityType);
        waypoint.name = name;
        waypoint.address = address;
        waypoint.latitude = latitude;
        waypoint.longitude = longitude;
        waypoint.kakaoPlaceId = kakaoPlaceId;
        waypoint.distanceFromOriginM = distanceFromOriginM;
        waypoint.distanceFromPrevM = distanceFromPrevM;
        waypoint.walkMinutesFromPrev = walkMinutesFromPrev;
        waypoint.stayMinutes = stayMinutes;
        waypoint.guide = guide;
        return waypoint;
    }

}
