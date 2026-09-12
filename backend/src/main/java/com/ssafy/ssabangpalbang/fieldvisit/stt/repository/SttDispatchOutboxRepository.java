package com.ssafy.ssabangpalbang.fieldvisit.stt.repository;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttDispatchOutbox;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttDispatchOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SttDispatchOutboxRepository
        extends JpaRepository<SttDispatchOutbox, Long> {

    @Query("""
            select outbox.id
            from SttDispatchOutbox outbox
            where outbox.status = :status
              and outbox.nextAttemptAt <= :now
            order by outbox.nextAttemptAt, outbox.id
            """)
    List<Long> findReadyIds(
            @Param("status") SttDispatchOutboxStatus status,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select outbox from SttDispatchOutbox outbox where outbox.id = :id")
    Optional<SttDispatchOutbox> findByIdForUpdate(@Param("id") Long id);

    Optional<SttDispatchOutbox> findBySttIdAndAttemptNo(
            String sttId,
            int attemptNo
    );
}
