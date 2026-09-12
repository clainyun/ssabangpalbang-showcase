package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistAnswer;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistAnswerRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChecklistProgressCounter {

    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistAnswerRepository checklistAnswerRepository;

    public Progress count(Long sessionId, Long memberId) {
        Checklist checklist = checklistRepository
                .findBySessionIdAndMemberId(sessionId, memberId)
                .orElse(null);
        if (checklist == null) {
            return Progress.empty();
        }

        List<ChecklistItem> items =
                checklistItemRepository.findByChecklistIdOrderByDisplayOrderAsc(
                        checklist.getId()
                );
        if (items.isEmpty()) {
            return Progress.empty();
        }

        List<Long> itemIds = items.stream().map(ChecklistItem::getId).toList();
        int completedCount = (int) checklistAnswerRepository
                .findByChecklistItemIdIn(itemIds)
                .stream()
                .filter(ChecklistAnswer::isCompleted)
                .count();
        return new Progress(
                completedCount,
                items.size(),
                items.size() - completedCount
        );
    }

    public record Progress(
            int completedCount,
            int totalCount,
            int incompleteCount
    ) {
        public static Progress empty() {
            return new Progress(0, 0, 0);
        }
    }
}
