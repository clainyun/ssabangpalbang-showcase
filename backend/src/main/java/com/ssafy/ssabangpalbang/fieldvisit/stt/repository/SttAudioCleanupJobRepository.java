package com.ssafy.ssabangpalbang.fieldvisit.stt.repository;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttAudioCleanupJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttAudioCleanupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SttAudioCleanupJobRepository
        extends JpaRepository<SttAudioCleanupJob, Long> {

    @Query("""
            select cleanup.id
            from SttAudioCleanupJob cleanup
            where cleanup.status = :status
              and cleanup.nextAttemptAt <= :now
            order by cleanup.nextAttemptAt, cleanup.id
            """)
    List<Long> findReadyIds(
            @Param("status") SttAudioCleanupStatus status,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cleanup from SttAudioCleanupJob cleanup where cleanup.id = :id")
    Optional<SttAudioCleanupJob> findByIdForUpdate(@Param("id") Long id);

    Optional<SttAudioCleanupJob> findByAudioFileId(Long audioFileId);
}
