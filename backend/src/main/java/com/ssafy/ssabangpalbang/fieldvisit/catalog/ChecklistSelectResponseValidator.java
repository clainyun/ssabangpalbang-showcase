package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiSelectResponse;
import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FastAPI select 응답을 Spring에서 한 번 더 검증한다.
 */
@Component
public class ChecklistSelectResponseValidator {

    public static final int MIN_ITEMS = 20;
    public static final int MAX_ITEMS = 30;
    public static final int MAX_SAME_CATEGORY_RATIO_DENOMINATOR = 2;

    public record ValidationResult(
            boolean valid,
            ChecklistFallbackReason reason
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(ChecklistFallbackReason reason) {
            return new ValidationResult(false, reason);
        }
    }

    public ValidationResult validate(
            ChecklistAiSelectResponse response,
            List<ChecklistCandidateScorer.ScoredCandidate> shortlist,
            ChecklistCatalog catalog,
            int targetItemCount
    ) {
        if (response == null || response.itemCodes() == null || response.itemCodes().isEmpty()) {
            return ValidationResult.failure(ChecklistFallbackReason.AI_EMPTY_ITEMS);
        }
        List<String> itemCodes = response.itemCodes();
        if (itemCodes.size() < MIN_ITEMS || itemCodes.size() > MAX_ITEMS) {
            return ValidationResult.failure(ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED);
        }

        Set<String> shortlistCodes = new HashSet<>();
        boolean shortlistHasCommonCore = false;
        boolean shortlistHasSummary = false;
        for (ChecklistCandidateScorer.ScoredCandidate candidate : shortlist) {
            shortlistCodes.add(candidate.item().itemCode());
            if (candidate.item().isCommonCoreFlag()) {
                shortlistHasCommonCore = true;
            }
            if (ChecklistShortlistSelector.SUMMARY_CATEGORY.equals(
                    candidate.item().categoryCode())) {
                shortlistHasSummary = true;
            }
        }

        Set<String> seen = new HashSet<>();
        Map<String, Integer> categoryCounts = new HashMap<>();
        boolean hasCommonCore = false;
        boolean hasSummary = false;

        for (String itemCode : itemCodes) {
            if (itemCode == null || itemCode.isBlank()) {
                return ValidationResult.failure(ChecklistFallbackReason.AI_MISSING_FIELD);
            }
            if (!seen.add(itemCode)) {
                return ValidationResult.failure(ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED);
            }
            if (!shortlistCodes.contains(itemCode)) {
                return ValidationResult.failure(ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED);
            }
            ChecklistCatalogItem item = catalog.findByItemCode(itemCode).orElse(null);
            if (item == null || !item.isActiveFlag()) {
                return ValidationResult.failure(ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED);
            }
            if (!ChecklistCatalogFilter.ALLOWED_ACCESS_LEVELS.contains(item.accessLevel())) {
                return ValidationResult.failure(ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED);
            }
            if (!item.visitConditionsOrEmpty().contains(ChecklistCatalogFilter.ANY_VISIT_CONDITION)) {
                return ValidationResult.failure(ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED);
            }
            categoryCounts.merge(item.categoryCode(), 1, Integer::sum);
            if (item.isCommonCoreFlag()) {
                hasCommonCore = true;
            }
            if (ChecklistShortlistSelector.SUMMARY_CATEGORY.equals(item.categoryCode())) {
                hasSummary = true;
            }
        }

        int maxAllowedSameCategory = Math.max(3, itemCodes.size() / MAX_SAME_CATEGORY_RATIO_DENOMINATOR);
        for (int count : categoryCounts.values()) {
            if (count > maxAllowedSameCategory) {
                return ValidationResult.failure(ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED);
            }
        }

        // target 기본 10이고 shortlist에 후보가 있으면 둘 다 최소 1개 이상 필요.
        // shortlist에 해당 후보 자체가 없으면 CATALOG_INSUFFICIENT_CANDIDATES로 실패한다.
        if (!shortlistHasCommonCore || !shortlistHasSummary) {
            return ValidationResult.failure(
                    ChecklistFallbackReason.CATALOG_INSUFFICIENT_CANDIDATES
            );
        }
        if (!hasCommonCore || !hasSummary) {
            return ValidationResult.failure(ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED);
        }
        return ValidationResult.success();
    }
}
