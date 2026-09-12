package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * FieldSession Repository다.
 *
 * <p>BE-014가 세션 생성·조회·잠금을 담당한다. AI-002·BE-015는 조회만 사용한다.</p>
 */
public interface FieldSessionRepository extends Repository<FieldSession, Long> {

    Optional<FieldSession> findByStudyId(Long studyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM FieldSession s
            WHERE s.studyId = :studyId
            """)
    Optional<FieldSession> findByStudyIdForUpdate(@Param("studyId") Long studyId);

    FieldSession save(FieldSession session);
}
