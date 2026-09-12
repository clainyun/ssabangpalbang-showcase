package com.ssafy.ssabangpalbang.fieldvisit.stt.repository;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SttJobRepository extends JpaRepository<SttJob, Long> {

    Optional<SttJob> findBySttId(String sttId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from SttJob job where job.sttId = :sttId")
    Optional<SttJob> findBySttIdForUpdate(@Param("sttId") String sttId);

    Optional<SttJob> findByAudioFileId(Long audioFileId);

    Optional<SttJob> findByMemberIdAndInitialClientRequestId(
            Long memberId,
            UUID initialClientRequestId
    );

    @Query("""
            select job.id
            from SttJob job
            where job.status = com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus.PENDING
              and job.lastDispatchedAt is not null
              and job.lastDispatchedAt <= :cutoff
            order by job.lastDispatchedAt, job.id
            """)
    List<Long> findStalePendingIds(
            @Param("cutoff") OffsetDateTime cutoff,
            Pageable pageable
    );

    @Query("""
            select job.id
            from SttJob job
            where job.status = com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus.PROCESSING
              and job.updatedAt <= :cutoff
            order by job.updatedAt, job.id
            """)
    List<Long> findStaleProcessingIds(
            @Param("cutoff") OffsetDateTime cutoff,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from SttJob job where job.id = :id")
    Optional<SttJob> findByIdForUpdate(@Param("id") Long id);
}
