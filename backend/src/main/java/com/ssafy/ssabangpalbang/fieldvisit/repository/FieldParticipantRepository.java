package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * FieldParticipant Repository다.
 *
 * <p>BE-014가 참여자 생성·조회·잠금을 담당한다. AI-002·BE-015는 조회만 사용한다.</p>
 */
public interface FieldParticipantRepository extends Repository<FieldParticipant, Long> {

    Optional<FieldParticipant> findBySessionIdAndMemberId(Long sessionId, Long memberId);

    List<FieldParticipant> findBySessionId(Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM FieldParticipant p
            WHERE p.sessionId = :sessionId
              AND p.memberId = :memberId
            """)
    Optional<FieldParticipant> findBySessionIdAndMemberIdForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("memberId") Long memberId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM FieldParticipant p
            WHERE p.sessionId = :sessionId
              AND p.status = com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus.IN_PROGRESS
            ORDER BY p.id ASC
            """)
    List<FieldParticipant> findInProgressBySessionIdForUpdate(
            @Param("sessionId") Long sessionId
    );

    long countBySessionIdAndStatus(
            Long sessionId,
            com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus status
    );

    long countBySessionId(Long sessionId);

    long countByMemberIdAndStatus(
            Long memberId,
            com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus status
    );

    FieldParticipant save(FieldParticipant participant);

    @Query(
            value = """
                    SELECT fs.study_id AS studyId,
                           fs.id AS sessionId
                    FROM field_participant fp
                    JOIN field_session fs ON fs.id = fp.session_id
                    WHERE fp.member_id = :memberId
                      AND fp.status = 'IN_PROGRESS'
                      AND fs.status = 'IN_PROGRESS'
                    ORDER BY fp.id ASC
                    LIMIT 1
                    """,
            nativeQuery = true
    )
    Optional<InProgressParticipation> findInProgressParticipation(
            @Param("memberId") Long memberId
    );

    interface InProgressParticipation {
        Long getStudyId();

        Long getSessionId();
    }
}
