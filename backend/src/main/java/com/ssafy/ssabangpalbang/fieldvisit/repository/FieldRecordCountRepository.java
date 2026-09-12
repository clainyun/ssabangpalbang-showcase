package com.ssafy.ssabangpalbang.fieldvisit.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * AI-002 GET 응답용 field_record 집계다. BE-015 CRUD는 구현하지 않는다.
 *
 * <p>기준: 로그인 회원 본인 기록, 해당 checklist_item에 연결,
 * {@code deleted_at IS NULL}.</p>
 */
@Repository
@RequiredArgsConstructor
public class FieldRecordCountRepository {

    private final JdbcTemplate jdbcTemplate;

    public Map<Long, Integer> countByAuthorAndItemIds(
            Long authorId,
            Collection<Long> checklistItemIds
    ) {
        if (checklistItemIds == null || checklistItemIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(
                ",",
                Collections.nCopies(checklistItemIds.size(), "?")
        );
        String sql = """
                SELECT checklist_item_id, COUNT(*) AS cnt
                FROM field_record
                WHERE author_id = ?
                  AND deleted_at IS NULL
                  AND checklist_item_id IN (%s)
                GROUP BY checklist_item_id
                """.formatted(placeholders);

        Object[] args = new Object[1 + checklistItemIds.size()];
        args[0] = authorId;
        int index = 1;
        for (Long itemId : checklistItemIds) {
            args[index++] = itemId;
        }

        Map<Long, Integer> counts = new HashMap<>();
        jdbcTemplate.query(sql, args, rs -> {
            counts.put(rs.getLong("checklist_item_id"), rs.getInt("cnt"));
        });
        return counts;
    }

    public Map<Long, RecordSummaryCount> summarizeByAuthorAndItemIds(
            Long authorId,
            Collection<Long> checklistItemIds
    ) {
        if (checklistItemIds == null || checklistItemIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(
                ",",
                Collections.nCopies(checklistItemIds.size(), "?")
        );
        String sql = """
                SELECT checklist_item_id, source_type, COUNT(*) AS cnt
                FROM field_record
                WHERE author_id = ?
                  AND deleted_at IS NULL
                  AND checklist_item_id IN (%s)
                GROUP BY checklist_item_id, source_type
                """.formatted(placeholders);

        Object[] args = new Object[1 + checklistItemIds.size()];
        args[0] = authorId;
        int index = 1;
        for (Long itemId : checklistItemIds) {
            args[index++] = itemId;
        }

        Map<Long, RecordSummaryCount> summaries = new HashMap<>();
        jdbcTemplate.query(sql, args, rs -> {
            Long itemId = rs.getLong("checklist_item_id");
            String sourceType = rs.getString("source_type");
            int count = rs.getInt("cnt");
            RecordSummaryCount summary = summaries.computeIfAbsent(
                    itemId,
                    ignored -> new RecordSummaryCount()
            );
            summary.add(sourceType, count);
        });
        return summaries;
    }

    public static final class RecordSummaryCount {
        private int textCount;
        private int photoCount;
        private int sttCount;

        void add(String sourceType, int count) {
            if (sourceType == null) {
                return;
            }
            switch (sourceType) {
                case "TEXT" -> textCount += count;
                case "PHOTO" -> photoCount += count;
                case "STT" -> sttCount += count;
                default -> {
                }
            }
        }

        public int textCount() {
            return textCount;
        }

        public int photoCount() {
            return photoCount;
        }

        public int sttCount() {
            return sttCount;
        }

        public int total() {
            return textCount + photoCount + sttCount;
        }
    }
}
