package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * FastAPI 체크리스트 응답을 저장 전에 Spring에서 한 번 더 검증한다.
 *
 * <p>{@link ChecklistItem#create}와 동일한 불변식을 사용해, Validator 통과 후
 * DB 제약 위반으로 500이 나는 경로를 막는다.</p>
 */
@Component
public class ChecklistAiResponseValidator {

    public ChecklistAiValidationResult validate(ChecklistAiGenerateResponse response) {
        if (response == null) {
            return ChecklistAiValidationResult.failure(
                    ChecklistFallbackReason.AI_EMPTY_RESPONSE
            );
        }
        if (response.items() == null || response.items().isEmpty()) {
            return ChecklistAiValidationResult.failure(
                    ChecklistFallbackReason.AI_EMPTY_ITEMS
            );
        }

        Set<Integer> displayOrders = new HashSet<>();
        for (ChecklistAiGenerateResponse.Item item : response.items()) {
            if (item == null) {
                return ChecklistAiValidationResult.failure(
                        ChecklistFallbackReason.AI_MISSING_FIELD
                );
            }
            if (isBlank(item.category()) || item.displayOrder() == null) {
                return ChecklistAiValidationResult.failure(
                        ChecklistFallbackReason.AI_MISSING_FIELD
                );
            }
            if (item.category().length() > ChecklistItem.CATEGORY_MAX_LENGTH) {
                return ChecklistAiValidationResult.failure(
                        ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED
                );
            }
            if (isBlank(item.title())) {
                return ChecklistAiValidationResult.failure(
                        ChecklistFallbackReason.AI_BLANK_TITLE
                );
            }
            if (item.displayOrder() < 1) {
                return ChecklistAiValidationResult.failure(
                        ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED
                );
            }
            if (!displayOrders.add(item.displayOrder())) {
                return ChecklistAiValidationResult.failure(
                        ChecklistFallbackReason.AI_DUPLICATE_DISPLAY_ORDER
                );
            }
        }

        return ChecklistAiValidationResult.success();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
