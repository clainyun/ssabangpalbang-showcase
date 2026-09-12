package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistPersonalizationInput;

import java.util.List;
import java.util.Objects;

/**
 * 점수화·shortlist에 사용하는 개인화 컨텍스트다.
 */
public record ChecklistSelectionContext(
        ChecklistPersonalizationInput personalization,
        List<String> selectedCatalogPriorities,
        String mappedPurpose,
        int shortlistSize,
        int targetItemCount
) {

    public ChecklistSelectionContext {
        Objects.requireNonNull(personalization);
        selectedCatalogPriorities = List.copyOf(selectedCatalogPriorities);
    }

    public boolean hasVehicle() {
        return Boolean.TRUE.equals(personalization.member().hasVehicle());
    }

    public boolean hasChildren() {
        return Boolean.TRUE.equals(personalization.member().hasChildren());
    }
}
