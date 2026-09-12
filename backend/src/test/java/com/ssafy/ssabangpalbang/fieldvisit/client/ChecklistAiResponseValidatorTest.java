package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChecklistAiResponseValidatorTest {

    private final ChecklistAiResponseValidator validator = new ChecklistAiResponseValidator();

    @Test
    void 정상_응답은_valid다() {
        var result = validator.validate(new ChecklistAiGenerateResponse(List.of(
                new ChecklistAiGenerateResponse.Item("교통", "역까지 거리", "부제", 1, null),
                new ChecklistAiGenerateResponse.Item("소음", "도로 소음", null, 2, null)
        )));
        assertThat(result.valid()).isTrue();
    }

    @Test
    void 응답이_null이면_AI_EMPTY_RESPONSE다() {
        assertThat(validator.validate(null).reason())
                .isEqualTo(ChecklistFallbackReason.AI_EMPTY_RESPONSE);
    }

    @Test
    void items가_비어있으면_AI_EMPTY_ITEMS다() {
        assertThat(validator.validate(new ChecklistAiGenerateResponse(List.of())).reason())
                .isEqualTo(ChecklistFallbackReason.AI_EMPTY_ITEMS);
    }

    @Test
    void null_item이면_AI_MISSING_FIELD다() {
        assertThat(validator.validate(new ChecklistAiGenerateResponse(
                Arrays.asList((ChecklistAiGenerateResponse.Item) null)
        )).reason()).isEqualTo(ChecklistFallbackReason.AI_MISSING_FIELD);
    }

    @Test
    void blank_category이면_AI_MISSING_FIELD다() {
        var result = validator.validate(new ChecklistAiGenerateResponse(List.of(
                new ChecklistAiGenerateResponse.Item("  ", "제목", null, 1, null)
        )));
        assertThat(result.reason()).isEqualTo(ChecklistFallbackReason.AI_MISSING_FIELD);
    }

    @Test
    void category_30자는_정상이다() {
        String category = "가".repeat(30);
        var result = validator.validate(new ChecklistAiGenerateResponse(List.of(
                new ChecklistAiGenerateResponse.Item(category, "제목", null, 1, null)
        )));
        assertThat(result.valid()).isTrue();
    }

    @Test
    void category_31자면_AI_SCHEMA_VALIDATION_FAILED다() {
        String category = "가".repeat(31);
        var result = validator.validate(new ChecklistAiGenerateResponse(List.of(
                new ChecklistAiGenerateResponse.Item(category, "제목", null, 1, null)
        )));
        assertThat(result.reason())
                .isEqualTo(ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED);
    }

    @Test
    void blank_title이면_AI_BLANK_TITLE이다() {
        var result = validator.validate(new ChecklistAiGenerateResponse(List.of(
                new ChecklistAiGenerateResponse.Item("교통", "   ", null, 1, null)
        )));
        assertThat(result.reason()).isEqualTo(ChecklistFallbackReason.AI_BLANK_TITLE);
    }

    @Test
    void displayOrder_0이면_실패다() {
        var result = validator.validate(new ChecklistAiGenerateResponse(List.of(
                new ChecklistAiGenerateResponse.Item("교통", "제목", null, 0, null)
        )));
        assertThat(result.reason())
                .isEqualTo(ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED);
    }

    @Test
    void displayOrder_음수면_실패다() {
        var result = validator.validate(new ChecklistAiGenerateResponse(List.of(
                new ChecklistAiGenerateResponse.Item("교통", "제목", null, -10, null)
        )));
        assertThat(result.reason())
                .isEqualTo(ChecklistFallbackReason.AI_SCHEMA_VALIDATION_FAILED);
    }

    @Test
    void displayOrder_중복이면_AI_DUPLICATE_DISPLAY_ORDER다() {
        var result = validator.validate(new ChecklistAiGenerateResponse(List.of(
                new ChecklistAiGenerateResponse.Item("교통", "첫 번째", null, 1, null),
                new ChecklistAiGenerateResponse.Item("소음", "두 번째", null, 1, null)
        )));
        assertThat(result.reason())
                .isEqualTo(ChecklistFallbackReason.AI_DUPLICATE_DISPLAY_ORDER);
    }

    @Test
    void subtitle_null은_정상이다() {
        var result = validator.validate(new ChecklistAiGenerateResponse(List.of(
                new ChecklistAiGenerateResponse.Item("교통", "제목", null, 1, null)
        )));
        assertThat(result.valid()).isTrue();
    }
}
