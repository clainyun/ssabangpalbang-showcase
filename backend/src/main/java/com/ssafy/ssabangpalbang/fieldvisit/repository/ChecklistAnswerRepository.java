package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * ChecklistAnswer upsert Repository다(BE-015).
 */
public interface ChecklistAnswerRepository extends JpaRepository<ChecklistAnswer, Long> {

    List<ChecklistAnswer> findByChecklistItemIdIn(Collection<Long> checklistItemIds);
}
