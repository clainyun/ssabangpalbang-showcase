package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FieldVisitCandidateRepository
        extends JpaRepository<FieldVisitCandidate, Long> {

    boolean existsBySessionIdAndMemberId(Long sessionId, Long memberId);

    List<FieldVisitCandidate> findBySessionId(Long sessionId);
}
