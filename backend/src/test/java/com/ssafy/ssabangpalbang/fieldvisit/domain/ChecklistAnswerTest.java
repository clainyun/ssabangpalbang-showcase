package com.ssafy.ssabangpalbang.fieldvisit.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ChecklistAnswerTest {

    @Test
    void false에서_true로_바꾸면_completedAt을_설정한다() {
        Instant t1 = Instant.parse("2026-07-30T01:00:00Z");
        ChecklistAnswer answer = ChecklistAnswer.create(1L, false, t1);
        Instant t2 = Instant.parse("2026-07-30T02:00:00Z");

        answer.applyCompletion(true, t2);

        assertThat(answer.isCompleted()).isTrue();
        assertThat(answer.getCompletedAt()).isEqualTo(t2);
    }

    @Test
    void true_재요청은_기존_completedAt을_유지한다() {
        Instant t1 = Instant.parse("2026-07-30T01:00:00Z");
        ChecklistAnswer answer = ChecklistAnswer.create(1L, true, t1);
        Instant t2 = Instant.parse("2026-07-30T02:00:00Z");

        answer.applyCompletion(true, t2);

        assertThat(answer.getCompletedAt()).isEqualTo(t1);
        assertThat(answer.getUpdatedAt()).isEqualTo(t2);
    }

    @Test
    void true에서_false로_바꾸면_completedAt이_null이다() {
        Instant t1 = Instant.parse("2026-07-30T01:00:00Z");
        ChecklistAnswer answer = ChecklistAnswer.create(1L, true, t1);

        answer.applyCompletion(false, Instant.parse("2026-07-30T03:00:00Z"));

        assertThat(answer.isCompleted()).isFalse();
        assertThat(answer.getCompletedAt()).isNull();
    }
}
