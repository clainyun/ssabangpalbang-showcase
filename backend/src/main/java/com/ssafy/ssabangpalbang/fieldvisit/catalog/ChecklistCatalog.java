package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 불변 메모리 카탈로그다. itemCode 기반 조회만 제공한다.
 */
public final class ChecklistCatalog {

    private final String version;
    private final ChecklistSelectionPolicyDto selectionPolicy;
    private final List<ChecklistCatalogItem> items;
    private final Map<String, ChecklistCatalogItem> byItemCode;
    private final CatalogEnumsView enums;
    private final int categoryCount;

    public ChecklistCatalog(
            String version,
            ChecklistSelectionPolicyDto selectionPolicy,
            List<ChecklistCatalogItem> items,
            CatalogEnumsView enums,
            int categoryCount
    ) {
        this.version = Objects.requireNonNull(version);
        this.selectionPolicy = Objects.requireNonNull(selectionPolicy);
        this.items = List.copyOf(items);
        LinkedHashMap<String, ChecklistCatalogItem> map = new LinkedHashMap<>();
        for (ChecklistCatalogItem item : this.items) {
            map.put(item.itemCode(), item);
        }
        this.byItemCode = Collections.unmodifiableMap(map);
        this.enums = Objects.requireNonNull(enums);
        this.categoryCount = categoryCount;
    }

    public String version() {
        return version;
    }

    public ChecklistSelectionPolicyDto selectionPolicy() {
        return selectionPolicy;
    }

    public List<ChecklistCatalogItem> items() {
        return items;
    }

    public Map<String, ChecklistCatalogItem> byItemCode() {
        return byItemCode;
    }

    public Optional<ChecklistCatalogItem> findByItemCode(String itemCode) {
        return Optional.ofNullable(byItemCode.get(itemCode));
    }

    public CatalogEnumsView enums() {
        return enums;
    }

    public int categoryCount() {
        return categoryCount;
    }

    public int size() {
        return items.size();
    }

    public record CatalogEnumsView(
            List<String> answerTypes,
            List<String> accessLevels,
            List<String> visitConditions,
            List<String> evidenceTypes,
            List<String> priorityCodes
    ) {
        public CatalogEnumsView {
            answerTypes = List.copyOf(answerTypes);
            accessLevels = List.copyOf(accessLevels);
            visitConditions = List.copyOf(visitConditions);
            evidenceTypes = List.copyOf(evidenceTypes);
            priorityCodes = List.copyOf(priorityCodes);
        }
    }
}
