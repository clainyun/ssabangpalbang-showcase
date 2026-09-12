package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecord;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecordSourceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FieldRecordRepository extends JpaRepository<FieldRecord, Long> {

    Optional<FieldRecord> findByClientRequestId(String clientRequestId);

    Optional<FieldRecord> findByIdAndSessionId(Long id, Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM FieldRecord r
            WHERE r.id = :id
              AND r.sessionId = :sessionId
            """)
    Optional<FieldRecord> findByIdAndSessionIdForUpdate(
            @Param("id") Long id,
            @Param("sessionId") Long sessionId
    );

    @Query("""
            SELECT r
            FROM FieldRecord r
            WHERE r.sessionId = :sessionId
              AND r.deletedAt IS NULL
              AND (:mineOnly = false OR r.authorId = :memberId)
              AND (:checklistItemId IS NULL OR r.checklistItemId = :checklistItemId)
              AND (:sourceType IS NULL OR r.sourceType = :sourceType)
              AND (:cursor IS NULL OR r.id < :cursor)
            ORDER BY r.id DESC
            """)
    List<FieldRecord> findPage(
            @Param("sessionId") Long sessionId,
            @Param("memberId") Long memberId,
            @Param("mineOnly") boolean mineOnly,
            @Param("checklistItemId") Long checklistItemId,
            @Param("sourceType") FieldRecordSourceType sourceType,
            @Param("cursor") Long cursor,
            org.springframework.data.domain.Pageable pageable
    );
}
