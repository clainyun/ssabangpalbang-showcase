package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * classpath JSON 파싱 결과를 구조적으로 검증한다.
 *
 * <p>conditionTags는 자유 태그이므로 enum 제한을 두지 않는다.
 * priorityTags와 priorityMappings.priorityCode는 enums.priorityCode에 속해야 한다.</p>
 */
@Component
public class ChecklistCatalogValidator {

    public static final String EXPECTED_VERSION = "3.0.0";
    public static final int EXPECTED_ITEM_COUNT = 300;
    public static final int EXPECTED_CATEGORY_COUNT = 14;
    public static final int MIN_RECOMMENDED = 8;
    public static final int MAX_RECOMMENDED = 18;

    public void validate(ChecklistCatalogDocument document) {
        Objects.requireNonNull(document, "catalog document must not be null");
        if (isBlank(document.datasetName())) {
            throw new ChecklistCatalogException("datasetName is required");
        }
        if (!EXPECTED_VERSION.equals(document.version())) {
            throw new ChecklistCatalogException(
                    "version must be " + EXPECTED_VERSION + " but was " + document.version()
            );
        }
        if (!Boolean.TRUE.equals(document.fieldOnly())) {
            throw new ChecklistCatalogException("fieldOnly must be true");
        }
        if (document.categories() == null
                || document.categories().size() != EXPECTED_CATEGORY_COUNT) {
            throw new ChecklistCatalogException(
                    "categories must contain exactly " + EXPECTED_CATEGORY_COUNT
            );
        }
        if (document.items() == null || document.items().size() != EXPECTED_ITEM_COUNT) {
            throw new ChecklistCatalogException(
                    "items must contain exactly " + EXPECTED_ITEM_COUNT
            );
        }
        if (document.selectionPolicy() == null
                || document.selectionPolicy().rawPoolItemCount() == null
                || document.selectionPolicy().rawPoolItemCount() != EXPECTED_ITEM_COUNT) {
            throw new ChecklistCatalogException("rawPoolItemCount must be 300");
        }
        ChecklistSelectionPolicyDto.RecommendedItemCount recommended =
                document.selectionPolicy().recommendedItemCount();
        if (recommended == null
                || !Integer.valueOf(MIN_RECOMMENDED).equals(recommended.min())
                || !Integer.valueOf(MAX_RECOMMENDED).equals(recommended.max())) {
            throw new ChecklistCatalogException("recommendedItemCount must be min=8 max=18");
        }
        if (document.enums() == null) {
            throw new ChecklistCatalogException("enums is required");
        }

        Set<String> answerTypes = toSet(document.enums().answerType(), "answerType");
        Set<String> accessLevels = toSet(document.enums().accessLevel(), "accessLevel");
        Set<String> visitConditions = toSet(document.enums().visitConditions(), "visitConditions");
        Set<String> evidenceTypes = toSet(document.enums().evidenceTypes(), "evidenceTypes");
        Set<String> priorityCodes = toSet(document.enums().priorityCode(), "priorityCode");

        Map<String, ChecklistCatalogDocument.CatalogCategory> categoriesByCode =
                validateCategories(document.categories());

        Set<String> seenCodes = new HashSet<>();
        Map<String, Integer> actualItemCountByCategory = new HashMap<>();
        Map<String, Set<Integer>> displayOrdersByCategory = new HashMap<>();

        for (ChecklistCatalogItem item : document.items()) {
            validateItem(
                    item,
                    categoriesByCode,
                    answerTypes,
                    accessLevels,
                    visitConditions,
                    evidenceTypes,
                    priorityCodes,
                    seenCodes,
                    displayOrdersByCategory
            );
            actualItemCountByCategory.merge(item.categoryCode(), 1, Integer::sum);
        }

        for (ChecklistCatalogDocument.CatalogCategory category : document.categories()) {
            int actual = actualItemCountByCategory.getOrDefault(category.categoryCode(), 0);
            if (!Objects.equals(category.itemCount(), actual)) {
                throw new ChecklistCatalogException(
                        "category itemCount mismatch for " + category.categoryCode()
                                + ": declared=" + category.itemCount() + " actual=" + actual
                );
            }
        }
    }

    private Map<String, ChecklistCatalogDocument.CatalogCategory> validateCategories(
            List<ChecklistCatalogDocument.CatalogCategory> categories
    ) {
        Map<String, ChecklistCatalogDocument.CatalogCategory> byCode = new HashMap<>();
        Set<Integer> displayOrders = new HashSet<>();
        int itemCountSum = 0;

        for (ChecklistCatalogDocument.CatalogCategory category : categories) {
            if (category == null) {
                throw new ChecklistCatalogException("category must not be null");
            }
            if (isBlank(category.categoryCode())) {
                throw new ChecklistCatalogException("categoryCode must not be blank");
            }
            if (isBlank(category.categoryTitle())) {
                throw new ChecklistCatalogException(
                        "categoryTitle must not be blank for " + category.categoryCode()
                );
            }
            if (category.displayOrder() == null || category.displayOrder() < 1) {
                throw new ChecklistCatalogException(
                        "category displayOrder must be >= 1 for " + category.categoryCode()
                );
            }
            if (!displayOrders.add(category.displayOrder())) {
                throw new ChecklistCatalogException(
                        "duplicate category displayOrder: " + category.displayOrder()
                );
            }
            if (category.itemCount() == null || category.itemCount() <= 0) {
                throw new ChecklistCatalogException(
                        "category itemCount must be positive for " + category.categoryCode()
                );
            }
            if (byCode.put(category.categoryCode(), category) != null) {
                throw new ChecklistCatalogException(
                        "duplicate categoryCode: " + category.categoryCode()
                );
            }
            itemCountSum += category.itemCount();
        }
        if (itemCountSum != EXPECTED_ITEM_COUNT) {
            throw new ChecklistCatalogException(
                    "sum of category itemCount must be 300 but was " + itemCountSum
            );
        }
        return byCode;
    }

    private void validateItem(
            ChecklistCatalogItem item,
            Map<String, ChecklistCatalogDocument.CatalogCategory> categoriesByCode,
            Set<String> answerTypes,
            Set<String> accessLevels,
            Set<String> visitConditions,
            Set<String> evidenceTypes,
            Set<String> priorityCodes,
            Set<String> seenCodes,
            Map<String, Set<Integer>> displayOrdersByCategory
    ) {
        if (item == null) {
            throw new ChecklistCatalogException("catalog item must not be null");
        }
        if (isBlank(item.itemCode())) {
            throw new ChecklistCatalogException("itemCode must not be blank");
        }
        if (!seenCodes.add(item.itemCode())) {
            throw new ChecklistCatalogException("duplicate itemCode: " + item.itemCode());
        }
        if (isBlank(item.categoryCode())
                || isBlank(item.categoryTitle())
                || isBlank(item.title())
                || isBlank(item.answerType())
                || isBlank(item.accessLevel())) {
            throw new ChecklistCatalogException(
                    "required fields missing for itemCode=" + item.itemCode()
            );
        }

        ChecklistCatalogDocument.CatalogCategory category =
                categoriesByCode.get(item.categoryCode());
        if (category == null) {
            throw new ChecklistCatalogException(
                    "unknown categoryCode for " + item.itemCode() + ": " + item.categoryCode()
            );
        }
        if (!Objects.equals(category.categoryTitle(), item.categoryTitle())) {
            throw new ChecklistCatalogException(
                    "categoryTitle mismatch for " + item.itemCode()
            );
        }
        if (!Objects.equals(category.displayOrder(), item.categoryOrder())) {
            throw new ChecklistCatalogException(
                    "categoryOrder mismatch for " + item.itemCode()
            );
        }

        if (item.active() == null) {
            throw new ChecklistCatalogException("active is required for " + item.itemCode());
        }
        if (item.baseWeight() == null
                || item.baseWeight() < 0
                || item.baseWeight() > 100) {
            throw new ChecklistCatalogException(
                    "baseWeight out of range for " + item.itemCode()
            );
        }
        if (item.displayOrder() == null || item.displayOrder() < 1) {
            throw new ChecklistCatalogException(
                    "displayOrder must be >= 1 for " + item.itemCode()
            );
        }
        if (item.categoryOrder() == null || item.categoryOrder() < 1) {
            throw new ChecklistCatalogException(
                    "categoryOrder must be >= 1 for " + item.itemCode()
            );
        }
        Set<Integer> orders = displayOrdersByCategory.computeIfAbsent(
                item.categoryCode(),
                ignored -> new HashSet<>()
        );
        if (!orders.add(item.displayOrder())) {
            throw new ChecklistCatalogException(
                    "duplicate displayOrder within category " + item.categoryCode()
                            + ": " + item.displayOrder()
            );
        }

        if (!answerTypes.contains(item.answerType())) {
            throw new ChecklistCatalogException(
                    "unknown answerType for " + item.itemCode() + ": " + item.answerType()
            );
        }
        if (!accessLevels.contains(item.accessLevel())) {
            throw new ChecklistCatalogException(
                    "unknown accessLevel for " + item.itemCode() + ": " + item.accessLevel()
            );
        }
        for (String condition : item.visitConditionsOrEmpty()) {
            if (!visitConditions.contains(condition)) {
                throw new ChecklistCatalogException(
                        "unknown visitCondition for " + item.itemCode() + ": " + condition
                );
            }
        }
        for (String evidence : item.evidenceTypesOrEmpty()) {
            if (!evidenceTypes.contains(evidence)) {
                throw new ChecklistCatalogException(
                        "unknown evidenceType for " + item.itemCode() + ": " + evidence
                );
            }
        }
        for (String priorityTag : item.priorityTagsOrEmpty()) {
            if (isBlank(priorityTag) || !priorityCodes.contains(priorityTag)) {
                throw new ChecklistCatalogException(
                        "unknown priorityTag for " + item.itemCode() + ": " + priorityTag
                );
            }
        }
        // conditionTags are free-form tags and are intentionally not enum-validated.

        Set<String> seenPriorityMappings = new HashSet<>();
        for (ChecklistCatalogItem.PriorityMapping mapping : item.priorityMappingsOrEmpty()) {
            if (mapping == null || isBlank(mapping.priorityCode())) {
                throw new ChecklistCatalogException(
                        "priorityMappings entry invalid for " + item.itemCode()
                );
            }
            if (!priorityCodes.contains(mapping.priorityCode())) {
                throw new ChecklistCatalogException(
                        "unknown priorityCode for " + item.itemCode()
                                + ": " + mapping.priorityCode()
                );
            }
            if (!seenPriorityMappings.add(mapping.priorityCode())) {
                throw new ChecklistCatalogException(
                        "duplicate priorityMappings.priorityCode for " + item.itemCode()
                                + ": " + mapping.priorityCode()
                );
            }
            if (mapping.relevanceWeight() == null
                    || mapping.relevanceWeight() < 0
                    || mapping.relevanceWeight() > 100) {
                throw new ChecklistCatalogException(
                        "relevanceWeight out of range for " + item.itemCode()
                );
            }
        }
    }

    private Set<String> toSet(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new ChecklistCatalogException("enums." + fieldName + " is required");
        }
        return Set.copyOf(values);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
