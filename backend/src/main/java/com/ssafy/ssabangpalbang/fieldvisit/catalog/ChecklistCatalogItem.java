package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * v3 체크리스트 후보 한 건이다. Jackson이 JSON 필드를 그대로 바인딩한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChecklistCatalogItem(
        String itemCode,
        String categoryCode,
        String categoryTitle,
        Integer categoryOrder,
        String title,
        String subtitle,
        String answerType,
        String answerUnit,
        String accessLevel,
        List<String> visitConditions,
        List<String> conditionTags,
        List<String> evidenceTypes,
        Integer baseWeight,
        Integer displayOrder,
        Boolean active,
        List<String> options,
        List<String> priorityTags,
        List<PriorityMapping> priorityMappings,
        Boolean isCommonCore,
        String introducedInVersion,
        String addedReason,
        // 현장에서 이 항목을 확인한 뒤 회원이 남길 법한 한 줄 예시 메모(항목별 고정).
        // 카탈로그 JSON에서 로드하며, 조회 시 checklist_item.example 로 흘러 프론트 placeholder에 쓰인다.
        String example
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriorityMapping(
            String priorityCode,
            Integer relevanceWeight
    ) {
    }

    public List<String> visitConditionsOrEmpty() {
        return visitConditions == null ? List.of() : List.copyOf(visitConditions);
    }

    public List<String> conditionTagsOrEmpty() {
        return conditionTags == null ? List.of() : List.copyOf(conditionTags);
    }

    public List<String> evidenceTypesOrEmpty() {
        return evidenceTypes == null ? List.of() : List.copyOf(evidenceTypes);
    }

    public List<String> priorityTagsOrEmpty() {
        return priorityTags == null ? List.of() : List.copyOf(priorityTags);
    }

    public List<PriorityMapping> priorityMappingsOrEmpty() {
        return priorityMappings == null ? List.of() : List.copyOf(priorityMappings);
    }

    public boolean isCommonCoreFlag() {
        return Boolean.TRUE.equals(isCommonCore);
    }

    public boolean isActiveFlag() {
        return Boolean.TRUE.equals(active);
    }
}
