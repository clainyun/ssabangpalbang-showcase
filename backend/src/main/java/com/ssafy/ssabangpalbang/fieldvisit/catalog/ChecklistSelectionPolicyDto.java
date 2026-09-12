package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * JSON {@code selectionPolicy} 바인딩용 DTO다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChecklistSelectionPolicyDto(
        RecommendedItemCount recommendedItemCount,
        RecommendedComposition recommendedComposition,
        List<String> hardFilters,
        PriorityScoring priorityScoring,
        List<String> softSignals,
        String deduplication,
        String defaultRule,
        Integer rawPoolItemCount,
        String rawPoolNote
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecommendedItemCount(Integer min, Integer max) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecommendedComposition(
            String selectedPriorityItems,
            String profileAndPurposeItems,
            String commonCoreItems,
            String summaryItems
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriorityScoring(
            Integer selectedPriorityBoost,
            String relevanceWeightScale,
            String unselectedPriorityRule,
            Integer maxItemsPerPriority
    ) {
    }
}
