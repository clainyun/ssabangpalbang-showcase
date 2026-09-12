package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 1차 하드 필터다. 날씨·시각 API 없이 안전한 기본 정책만 적용한다.
 */
@Component
public class ChecklistCatalogFilter {

    public static final Set<String> ALLOWED_ACCESS_LEVELS = Set.of(
            "PUBLIC_OUTDOOR",
            "COMPLEX_OUTDOOR"
    );

    public static final String ANY_VISIT_CONDITION = "ANY";

    public List<ChecklistCatalogItem> filter(ChecklistCatalog catalog) {
        List<ChecklistCatalogItem> accepted = new ArrayList<>();
        for (ChecklistCatalogItem item : catalog.items()) {
            if (!item.isActiveFlag()) {
                continue;
            }
            if (!ALLOWED_ACCESS_LEVELS.contains(item.accessLevel())) {
                continue;
            }
            if (!item.visitConditionsOrEmpty().contains(ANY_VISIT_CONDITION)) {
                continue;
            }
            accepted.add(item);
        }
        return List.copyOf(accepted);
    }
}
