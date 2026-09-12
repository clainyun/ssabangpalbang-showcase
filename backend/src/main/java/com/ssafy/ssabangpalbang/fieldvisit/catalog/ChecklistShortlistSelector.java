package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 점수 순 후보에서 30~50 shortlist와 최종 8~18 fallback 선택을 결정적으로 만든다.
 */
@Component
public class ChecklistShortlistSelector {

    public static final int DEFAULT_MAX_PER_CATEGORY = 6;
    public static final int MAX_ITEMS_PER_PRIORITY = 3;
    public static final String SUMMARY_CATEGORY = "SUM";

    public List<ChecklistCandidateScorer.ScoredCandidate> selectShortlist(
            List<ChecklistCandidateScorer.ScoredCandidate> scored,
            ChecklistSelectionContext context
    ) {
        return selectLimited(
                scored,
                context.shortlistSize(),
                context,
                DEFAULT_MAX_PER_CATEGORY
        );
    }

    public List<ChecklistCandidateScorer.ScoredCandidate> selectFinalFallback(
            List<ChecklistCandidateScorer.ScoredCandidate> scored,
            ChecklistSelectionContext context
    ) {
        int target = Math.max(
                ChecklistSelectResponseValidator.MIN_ITEMS,
                Math.min(ChecklistSelectResponseValidator.MAX_ITEMS, context.targetItemCount())
        );
        List<ChecklistCandidateScorer.ScoredCandidate> selected = new ArrayList<>();
        Set<String> selectedCodes = new HashSet<>();

        addByPredicate(
                scored,
                selected,
                selectedCodes,
                target,
                context,
                scoredCandidate -> scoredCandidate.item().isCommonCoreFlag(),
                4
        );
        addByPredicate(
                scored,
                selected,
                selectedCodes,
                target,
                context,
                scoredCandidate -> SUMMARY_CATEGORY.equals(scoredCandidate.item().categoryCode()),
                3
        );
        addByPredicate(
                scored,
                selected,
                selectedCodes,
                target,
                context,
                scoredCandidate -> matchesSelectedPriority(
                        scoredCandidate.item(),
                        context.selectedCatalogPriorities()
                ),
                3
        );
        addByPredicate(
                scored,
                selected,
                selectedCodes,
                target,
                context,
                scoredCandidate -> true,
                target
        );

        selected.sort(ChecklistCandidateScorer.scoreComparator());
        if (selected.size() > target) {
            return List.copyOf(selected.subList(0, target));
        }
        return List.copyOf(selected);
    }

    private List<ChecklistCandidateScorer.ScoredCandidate> selectLimited(
            List<ChecklistCandidateScorer.ScoredCandidate> scored,
            int limit,
            ChecklistSelectionContext context,
            int maxPerCategory
    ) {
        List<ChecklistCandidateScorer.ScoredCandidate> selected = new ArrayList<>();
        Set<String> selectedCodes = new HashSet<>();
        Map<String, Integer> categoryCounts = new HashMap<>();
        Map<String, Integer> priorityCounts = new HashMap<>();

        // 공통 핵심·SUM을 먼저 확보한 뒤 점수 순으로 채운다.
        for (ChecklistCandidateScorer.ScoredCandidate candidate : scored) {
            if (candidate.item().isCommonCoreFlag()) {
                tryAdd(
                        candidate,
                        selected,
                        selectedCodes,
                        categoryCounts,
                        priorityCounts,
                        context,
                        limit,
                        maxPerCategory
                );
            }
        }
        for (ChecklistCandidateScorer.ScoredCandidate candidate : scored) {
            if (SUMMARY_CATEGORY.equals(candidate.item().categoryCode())) {
                tryAdd(
                        candidate,
                        selected,
                        selectedCodes,
                        categoryCounts,
                        priorityCounts,
                        context,
                        limit,
                        maxPerCategory
                );
            }
        }
        for (ChecklistCandidateScorer.ScoredCandidate candidate : scored) {
            tryAdd(
                    candidate,
                    selected,
                    selectedCodes,
                    categoryCounts,
                    priorityCounts,
                    context,
                    limit,
                    maxPerCategory
            );
            if (selected.size() >= limit) {
                break;
            }
        }
        selected.sort(ChecklistCandidateScorer.scoreComparator());
        return List.copyOf(selected);
    }

    private void addByPredicate(
            List<ChecklistCandidateScorer.ScoredCandidate> scored,
            List<ChecklistCandidateScorer.ScoredCandidate> selected,
            Set<String> selectedCodes,
            int target,
            ChecklistSelectionContext context,
            java.util.function.Predicate<ChecklistCandidateScorer.ScoredCandidate> predicate,
            int maxAdd
    ) {
        int added = 0;
        Map<String, Integer> categoryCounts = countCategories(selected);
        Map<String, Integer> priorityCounts = countPriorities(selected, context);
        for (ChecklistCandidateScorer.ScoredCandidate candidate : scored) {
            if (selected.size() >= target || added >= maxAdd) {
                return;
            }
            if (!predicate.test(candidate)) {
                continue;
            }
            if (tryAdd(
                    candidate,
                    selected,
                    selectedCodes,
                    categoryCounts,
                    priorityCounts,
                    context,
                    target,
                    DEFAULT_MAX_PER_CATEGORY
            )) {
                added++;
            }
        }
    }

    private boolean tryAdd(
            ChecklistCandidateScorer.ScoredCandidate candidate,
            List<ChecklistCandidateScorer.ScoredCandidate> selected,
            Set<String> selectedCodes,
            Map<String, Integer> categoryCounts,
            Map<String, Integer> priorityCounts,
            ChecklistSelectionContext context,
            int limit,
            int maxPerCategory
    ) {
        if (selected.size() >= limit) {
            return false;
        }
        ChecklistCatalogItem item = candidate.item();
        if (!selectedCodes.add(item.itemCode())) {
            return false;
        }
        int categoryCount = categoryCounts.getOrDefault(item.categoryCode(), 0);
        if (categoryCount >= maxPerCategory) {
            selectedCodes.remove(item.itemCode());
            return false;
        }
        for (String priority : item.priorityTagsOrEmpty()) {
            if (context.selectedCatalogPriorities().contains(priority)
                    && priorityCounts.getOrDefault(priority, 0) >= MAX_ITEMS_PER_PRIORITY) {
                selectedCodes.remove(item.itemCode());
                return false;
            }
        }
        selected.add(candidate);
        categoryCounts.merge(item.categoryCode(), 1, Integer::sum);
        for (String priority : item.priorityTagsOrEmpty()) {
            if (context.selectedCatalogPriorities().contains(priority)) {
                priorityCounts.merge(priority, 1, Integer::sum);
            }
        }
        return true;
    }

    private boolean matchesSelectedPriority(
            ChecklistCatalogItem item,
            List<String> selectedPriorities
    ) {
        Set<String> selected = new HashSet<>(selectedPriorities);
        for (String tag : item.priorityTagsOrEmpty()) {
            if (selected.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Integer> countCategories(
            List<ChecklistCandidateScorer.ScoredCandidate> selected
    ) {
        Map<String, Integer> counts = new HashMap<>();
        for (ChecklistCandidateScorer.ScoredCandidate candidate : selected) {
            counts.merge(candidate.item().categoryCode(), 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Integer> countPriorities(
            List<ChecklistCandidateScorer.ScoredCandidate> selected,
            ChecklistSelectionContext context
    ) {
        Map<String, Integer> counts = new HashMap<>();
        Set<String> selectedPriorities = new HashSet<>(context.selectedCatalogPriorities());
        for (ChecklistCandidateScorer.ScoredCandidate candidate : selected) {
            for (String tag : candidate.item().priorityTagsOrEmpty()) {
                if (selectedPriorities.contains(tag)) {
                    counts.merge(tag, 1, Integer::sum);
                }
            }
        }
        return counts;
    }
}
