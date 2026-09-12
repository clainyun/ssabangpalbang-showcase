package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitStartRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FieldVisitStartRequestRepository
        extends JpaRepository<FieldVisitStartRequest, Long> {

    Optional<FieldVisitStartRequest> findByMemberIdAndClientRequestId(
            Long memberId,
            String clientRequestId
    );
}
