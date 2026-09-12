package com.ssafy.ssabangpalbang.fieldvisit.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/** 임장 세션당 하나만 저장하는 공유 추천 경로다. */
@Entity
@Table(name = "field_visit_route")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldVisitRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    @Column(name = "generated_by_id", nullable = false)
    private Long generatedById;

    @Column(name = "total_distance_meters", nullable = false)
    private int totalDistanceMeters;

    @Column(name = "estimated_duration_minutes", nullable = false)
    private int estimatedDurationMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "geometry", columnDefinition = "jsonb")
    private JsonNode geometry;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    public static FieldVisitRoute create(
            Long sessionId,
            Long generatedById,
            int totalDistanceMeters,
            int estimatedDurationMinutes,
            JsonNode geometry
    ) {
        if (totalDistanceMeters < 0 || estimatedDurationMinutes < 0) {
            throw new IllegalArgumentException("추천 경로 거리와 예상 시간은 음수일 수 없습니다.");
        }
        FieldVisitRoute route = new FieldVisitRoute();
        route.sessionId = Objects.requireNonNull(sessionId);
        route.generatedById = Objects.requireNonNull(generatedById);
        route.totalDistanceMeters = totalDistanceMeters;
        route.estimatedDurationMinutes = estimatedDurationMinutes;
        route.geometry = Objects.requireNonNull(geometry).deepCopy();
        return route;
    }

    public void replaceWalkingRoute(
            int totalDistanceMeters,
            int estimatedDurationMinutes,
            JsonNode geometry
    ) {
        if (totalDistanceMeters < 0 || estimatedDurationMinutes < 0) {
            throw new IllegalArgumentException("추천 경로 거리와 예상 시간은 음수일 수 없습니다.");
        }
        this.totalDistanceMeters = totalDistanceMeters;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.geometry = Objects.requireNonNull(geometry).deepCopy();
    }

    public JsonNode getGeometry() {
        return geometry == null ? null : geometry.deepCopy();
    }
}
