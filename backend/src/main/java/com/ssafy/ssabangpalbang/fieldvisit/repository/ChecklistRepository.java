package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Checklist는 AI-002가 생성·조회하므로 쓰기가 필요해 JpaRepository를 상속한다.
 */
public interface ChecklistRepository extends JpaRepository<Checklist, Long> {

    Optional<Checklist> findBySessionIdAndMemberId(Long sessionId, Long memberId);

    boolean existsBySessionId(Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c
            FROM Checklist c
            WHERE c.sessionId = :sessionId
              AND c.memberId = :memberId
            """)
    Optional<Checklist> findBySessionIdAndMemberIdForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("memberId") Long memberId
    );
}
