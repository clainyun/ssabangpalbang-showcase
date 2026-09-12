package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistAnswer;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistDetailResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-015 쓰기 결과가 AI-002 GET checklist 집계에 반영되는지 검증한다.
 */
class Be015Ai002AggregationRegressionTest {

    @Test
    void answers_완료_후_completedCount가_증가한다() {
        Checklist checklist = Checklist.create(100L, 42L, false);
        ReflectionTestUtils.setField(checklist, "id", 55L);
        ChecklistItem item1 = item(501L, "교통", "역", 1);
        ChecklistItem item2 = item(502L, "교통", "버스", 2);

        Map<Long, ChecklistAnswer> before = Map.of();
        var beforeBody = ChecklistResponseAssembler.toDetailBody(
                checklist,
                List.of(item1, item2),
                before,
                Map.of(501L, 0, 502L, 0),
                Map.of()
        );
        assertThat(beforeBody.completedCount()).isZero();

        Instant now = Instant.now();
        Map<Long, ChecklistAnswer> after = Map.of(
                501L, ChecklistAnswer.create(501L, true, now)
        );
        var afterBody = ChecklistResponseAssembler.toDetailBody(
                checklist,
                List.of(item1, item2),
                after,
                Map.of(501L, 0, 502L, 0),
                Map.of()
        );
        assertThat(afterBody.completedCount()).isEqualTo(1);
        assertThat(afterBody.categories().get(0).completedCount()).isEqualTo(1);
    }

    @Test
    void TEXT_PHOTO_STT_기록_수가_recordSummary에_반영된다() {
        Checklist checklist = Checklist.create(100L, 42L, false);
        ReflectionTestUtils.setField(checklist, "id", 55L);
        ChecklistItem item = item(501L, "교통", "역", 1);

        FieldRecordCountRepository.RecordSummaryCount summary =
                new FieldRecordCountRepository.RecordSummaryCount();
        ReflectionTestUtils.setField(summary, "textCount", 2);
        ReflectionTestUtils.setField(summary, "photoCount", 1);
        ReflectionTestUtils.setField(summary, "sttCount", 1);
        Map<Long, FieldRecordCountRepository.RecordSummaryCount> summaries = Map.of(501L, summary);

        ChecklistDetailResponse.ChecklistBody body = ChecklistResponseAssembler.toDetailBody(
                checklist,
                List.of(item),
                Map.of(),
                Map.of(501L, 4),
                summaries
        );

        var recordItem = body.categories().get(0).items().get(0);
        assertThat(recordItem.recordCount()).isEqualTo(4);
        assertThat(recordItem.recordSummary().textCount()).isEqualTo(2);
        assertThat(recordItem.recordSummary().photoCount()).isEqualTo(1);
        assertThat(recordItem.recordSummary().sttCount()).isEqualTo(1);
    }

    @Test
    void item_이동_후_이전_항목_count는_감소하고_새_항목은_증가한다() {
        Checklist checklist = Checklist.create(100L, 42L, false);
        ReflectionTestUtils.setField(checklist, "id", 55L);
        ChecklistItem from = item(501L, "교통", "역", 1);
        ChecklistItem to = item(502L, "교통", "버스", 2);

        var before = ChecklistResponseAssembler.toDetailBody(
                checklist,
                List.of(from, to),
                Map.of(),
                Map.of(501L, 2, 502L, 0),
                Map.of()
        );
        assertThat(before.categories().get(0).items().get(0).recordCount()).isEqualTo(2);
        assertThat(before.categories().get(0).items().get(1).recordCount()).isZero();

        var after = ChecklistResponseAssembler.toDetailBody(
                checklist,
                List.of(from, to),
                Map.of(),
                Map.of(501L, 1, 502L, 1),
                Map.of()
        );
        assertThat(after.categories().get(0).items().get(0).recordCount()).isEqualTo(1);
        assertThat(after.categories().get(0).items().get(1).recordCount()).isEqualTo(1);
    }

    private static ChecklistItem item(Long id, String category, String title, int order) {
        ChecklistItem item = ChecklistItem.create(55L, category, title, null, order, null);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
