package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * v3 JSON 루트 문서다. 선택에 필요한 필드만 바인딩한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChecklistCatalogDocument(
        String datasetName,
        String version,
        Boolean fieldOnly,
        String description,
        ChecklistSelectionPolicyDto selectionPolicy,
        CatalogEnums enums,
        List<CatalogCategory> categories,
        List<ChecklistCatalogItem> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogEnums(
            List<String> answerType,
            List<String> accessLevel,
            List<String> visitConditions,
            List<String> evidenceTypes,
            List<String> priorityCode
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogCategory(
            String categoryCode,
            String categoryTitle,
            Integer displayOrder,
            Integer itemCount
    ) {
    }
}
