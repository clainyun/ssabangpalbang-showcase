package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChecklistGenerationProgressRepository
        extends JpaRepository<ChecklistGenerationProgress, Long> {

    Optional<ChecklistGenerationProgress> findBySessionIdAndMemberIdAndAttemptId(
            Long sessionId,
            Long memberId,
            String attemptId
    );

    Optional<ChecklistGenerationProgress> findFirstBySessionIdAndMemberIdOrderByUpdatedAtDesc(
            Long sessionId,
            Long memberId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO checklist_generation_progress (
                session_id, member_id, attempt_id, status, stage,
                progress_rate, message, failure_message,
                created_at, updated_at
            ) VALUES (
                :sessionId, :memberId, :attemptId, 'IN_PROGRESS', :stage,
                :progressRate, :message, NULL, now(), now()
            )
            ON CONFLICT (session_id, member_id, attempt_id) DO UPDATE SET
                status = 'IN_PROGRESS',
                stage = EXCLUDED.stage,
                progress_rate = EXCLUDED.progress_rate,
                message = EXCLUDED.message,
                failure_message = NULL,
                updated_at = now()
            """, nativeQuery = true)
    void restart(
            @Param("sessionId") Long sessionId,
            @Param("memberId") Long memberId,
            @Param("attemptId") String attemptId,
            @Param("stage") String stage,
            @Param("progressRate") int progressRate,
            @Param("message") String message
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO checklist_generation_progress (
                session_id, member_id, attempt_id, status, stage,
                progress_rate, message, failure_message,
                created_at, updated_at
            ) VALUES (
                :sessionId, :memberId, :attemptId, :status, :stage,
                :progressRate, :message, NULL, now(), now()
            )
            ON CONFLICT (session_id, member_id, attempt_id) DO UPDATE SET
                status = EXCLUDED.status,
                stage = EXCLUDED.stage,
                progress_rate = EXCLUDED.progress_rate,
                message = EXCLUDED.message,
                failure_message = NULL,
                updated_at = now()
            WHERE checklist_generation_progress.status <> 'DONE'
              AND checklist_generation_progress.progress_rate <= EXCLUDED.progress_rate
            """, nativeQuery = true)
    void advance(
            @Param("sessionId") Long sessionId,
            @Param("memberId") Long memberId,
            @Param("attemptId") String attemptId,
            @Param("status") String status,
            @Param("stage") String stage,
            @Param("progressRate") int progressRate,
            @Param("message") String message
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO checklist_generation_progress (
                session_id, member_id, attempt_id, status, stage,
                progress_rate, message, failure_message,
                created_at, updated_at
            ) VALUES (
                :sessionId, :memberId, :attemptId, 'FAILED', :stage,
                :progressRate, :message, :failureMessage, now(), now()
            )
            ON CONFLICT (session_id, member_id, attempt_id) DO UPDATE SET
                status = 'FAILED',
                failure_message = EXCLUDED.failure_message,
                updated_at = now()
            WHERE checklist_generation_progress.status <> 'DONE'
            """, nativeQuery = true)
    void fail(
            @Param("sessionId") Long sessionId,
            @Param("memberId") Long memberId,
            @Param("attemptId") String attemptId,
            @Param("stage") String stage,
            @Param("progressRate") int progressRate,
            @Param("message") String message,
            @Param("failureMessage") String failureMessage
    );
}
