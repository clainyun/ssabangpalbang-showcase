package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findByChecklistIdOrderByDisplayOrderAsc(Long checklistId);

    @Query("""
            SELECT i
            FROM ChecklistItem i, Checklist c
            WHERE i.checklistId = c.id
              AND c.sessionId = :sessionId
            ORDER BY c.id ASC, i.displayOrder ASC, i.id ASC
            """)
    List<ChecklistItem> findBySessionIdOrderByChecklistIdAndDisplayOrder(
            @Param("sessionId") Long sessionId
    );

    List<ChecklistItem> findByIdInAndChecklistId(Collection<Long> ids, Long checklistId);

    @Query("""
            SELECT i
            FROM ChecklistItem i, Checklist c
            WHERE i.checklistId = c.id
              AND i.id = :itemId
              AND c.sessionId = :sessionId
              AND c.memberId = :memberId
            """)
    Optional<ChecklistItem> findOwnedItem(
            @Param("itemId") Long itemId,
            @Param("sessionId") Long sessionId,
            @Param("memberId") Long memberId
    );

    @Query("""
            SELECT i
            FROM ChecklistItem i, Checklist c
            WHERE i.checklistId = c.id
              AND i.id = :itemId
              AND c.sessionId = :sessionId
            """)
    Optional<ChecklistItem> findByIdAndSessionId(
            @Param("itemId") Long itemId,
            @Param("sessionId") Long sessionId
    );
}
