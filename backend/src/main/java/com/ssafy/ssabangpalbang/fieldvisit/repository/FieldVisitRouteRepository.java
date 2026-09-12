package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRoute;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FieldVisitRouteRepository extends JpaRepository<FieldVisitRoute, Long> {

    Optional<FieldVisitRoute> findBySessionId(Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM FieldVisitRoute r WHERE r.id = :routeId")
    Optional<FieldVisitRoute> findByIdForUpdate(@Param("routeId") Long routeId);
}
