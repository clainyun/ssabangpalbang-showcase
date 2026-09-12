package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 카탈로그 후보 점수 계산기다. 동일 입력에 대해 결정적이다.
 */
@Component
public class ChecklistCandidateScorer {

    public static final int SELECTED_PRIORITY_BOOST = 35;
    public static final int VEHICLE_BOOST = 10;
    public static final int CHILDREN_BOOST = 10;
    public static final int PURPOSE_BOOST = 10;
    public static final int COMMON_CORE_BOOST = 15;

    public static final int RELEVANCE_MIN = 45;
    public static final int RELEVANCE_MAX = 100;
    public static final int RELEVANCE_NORMALIZED_MAX = 20;

    public record ScoredCandidate(
            ChecklistCatalogItem item,
            int score
    ) {
    }

    public List<ScoredCandidate> score(
            List<ChecklistCatalogItem> candidates,
            ChecklistSelectionContext context
    ) {
        Objects.requireNonNull(candidates);
        Objects.requireNonNull(context);
        Set<String> selected = new HashSet<>(context.selectedCatalogPriorities());
        return candidates.stream()
                .map(item -> new ScoredCandidate(item, scoreItem(item, selected, context)))
                .sorted(scoreComparator())
                .toList();
    }

    public int scoreItem(
            ChecklistCatalogItem item,
            Set<String> selectedPriorities,
            ChecklistSelectionContext context
    ) {
        int score = item.baseWeight() == null ? 0 : item.baseWeight();

        int bestRelevance = 0;
        boolean matchedPriority = false;
        for (ChecklistCatalogItem.PriorityMapping mapping : item.priorityMappingsOrEmpty()) {
            if (selectedPriorities.contains(mapping.priorityCode())) {
                matchedPriority = true;
                if (mapping.relevanceWeight() != null) {
                    bestRelevance = Math.max(bestRelevance, mapping.relevanceWeight());
                }
            }
        }
        if (matchedPriority) {
            score += SELECTED_PRIORITY_BOOST;
            score += normalizeRelevanceWeight(bestRelevance);
        }

        Set<String> tags = new HashSet<>(item.conditionTagsOrEmpty());
        if (context.hasVehicle()
                && (tags.contains("CAR") || tags.contains("PARKING"))) {
            score += VEHICLE_BOOST;
        }
        if (context.hasChildren()
                && (tags.contains("CHILD")
                || tags.contains("INFANT")
                || tags.contains("TEEN"))) {
            score += CHILDREN_BOOST;
        }
        if (context.mappedPurpose() != null
                && tags.contains(context.mappedPurpose())) {
            score += PURPOSE_BOOST;
        }
        if (item.isCommonCoreFlag()) {
            score += COMMON_CORE_BOOST;
        }
        return score;
    }

    /**
     * relevanceWeight 45~100 → 0~20.
     * normalized = round(clamp((weight - 45) / 55, 0, 1) * 20)
     */
    public static int normalizeRelevanceWeight(int relevanceWeight) {
        double clamped = Math.max(
                0.0d,
                Math.min(
                        1.0d,
                        (relevanceWeight - (double) RELEVANCE_MIN)
                                / (double) (RELEVANCE_MAX - RELEVANCE_MIN)
                )
        );
        return (int) Math.round(clamped * RELEVANCE_NORMALIZED_MAX);
    }

    public static Comparator<ScoredCandidate> scoreComparator() {
        return Comparator
                .comparingInt(ScoredCandidate::score).reversed()
                .thenComparing(
                        (ScoredCandidate scored) -> scored.item().baseWeight() == null
                                ? 0
                                : scored.item().baseWeight(),
                        Comparator.reverseOrder()
                )
                .thenComparing(
                        scored -> scored.item().categoryOrder() == null
                                ? Integer.MAX_VALUE
                                : scored.item().categoryOrder()
                )
                .thenComparing(
                        scored -> scored.item().displayOrder() == null
                                ? Integer.MAX_VALUE
                                : scored.item().displayOrder()
                )
                .thenComparing(scored -> scored.item().itemCode());
    }
}
