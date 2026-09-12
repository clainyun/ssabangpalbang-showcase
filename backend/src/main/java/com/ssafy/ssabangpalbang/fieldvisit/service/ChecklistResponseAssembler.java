package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistAnswer;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistDetailResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 저장된 체크리스트를 API 응답 DTO로 조립한다.
 */
final class ChecklistResponseAssembler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private ChecklistResponseAssembler() {
    }

    static ChecklistGenerateResponse toGenerateResponse(
            Long studyId,
            Checklist checklist,
            List<ChecklistItem> items,
            Map<Long, ChecklistAnswer> answersByItemId,
            Map<Long, Integer> recordCounts
    ) {
        List<ChecklistGenerateResponse.GenerateCategoryResponse> categories =
                groupGenerateCategories(items, answersByItemId, recordCounts);

        int completedCount = (int) items.stream()
                .filter(item -> isCompleted(answersByItemId.get(item.getId())))
                .count();

        return new ChecklistGenerateResponse(
                studyId,
                checklist.getSessionId(),
                checklist.getId(),
                checklist.isFallback(),
                toOffset(checklist.getGeneratedAt()),
                completedCount,
                items.size(),
                categories
        );
    }

    static ChecklistDetailResponse.ChecklistBody toDetailBody(
            Checklist checklist,
            List<ChecklistItem> items,
            Map<Long, ChecklistAnswer> answersByItemId,
            Map<Long, Integer> recordCounts,
            Map<Long, FieldRecordCountRepository.RecordSummaryCount> summaries
    ) {
        List<ChecklistDetailResponse.DetailCategoryResponse> categories =
                groupDetailCategories(items, answersByItemId, recordCounts, summaries);

        int completedCount = (int) items.stream()
                .filter(item -> isCompleted(answersByItemId.get(item.getId())))
                .count();

        return new ChecklistDetailResponse.ChecklistBody(
                checklist.getId(),
                checklist.isFallback(),
                toOffset(checklist.getGeneratedAt()),
                completedCount,
                items.size(),
                categories
        );
    }

    static Map<Long, ChecklistAnswer> indexAnswers(List<ChecklistAnswer> answers) {
        return answers.stream().collect(Collectors.toMap(
                ChecklistAnswer::getChecklistItemId,
                Function.identity(),
                (left, right) -> left
        ));
    }

    private static List<ChecklistGenerateResponse.GenerateCategoryResponse> groupGenerateCategories(
            List<ChecklistItem> items,
            Map<Long, ChecklistAnswer> answersByItemId,
            Map<Long, Integer> recordCounts
    ) {
        LinkedHashMap<String, List<ChecklistItem>> grouped = groupByCategory(items);
        List<ChecklistGenerateResponse.GenerateCategoryResponse> categories =
                new ArrayList<>();
        for (Map.Entry<String, List<ChecklistItem>> entry : grouped.entrySet()) {
            List<ChecklistGenerateResponse.GenerateItemResponse> itemResponses =
                    entry.getValue().stream()
                            .map(item -> toGenerateItem(
                                    item,
                                    answersByItemId.get(item.getId()),
                                    recordCounts.getOrDefault(item.getId(), 0)
                            ))
                            .toList();
            categories.add(new ChecklistGenerateResponse.GenerateCategoryResponse(
                    entry.getKey(),
                    itemResponses.size(),
                    itemResponses
            ));
        }
        return categories;
    }

    private static List<ChecklistDetailResponse.DetailCategoryResponse> groupDetailCategories(
            List<ChecklistItem> items,
            Map<Long, ChecklistAnswer> answersByItemId,
            Map<Long, Integer> recordCounts,
            Map<Long, FieldRecordCountRepository.RecordSummaryCount> summaries
    ) {
        LinkedHashMap<String, List<ChecklistItem>> grouped = groupByCategory(items);
        List<ChecklistDetailResponse.DetailCategoryResponse> categories = new ArrayList<>();
        for (Map.Entry<String, List<ChecklistItem>> entry : grouped.entrySet()) {
            List<ChecklistDetailResponse.DetailItemResponse> itemResponses =
                    entry.getValue().stream()
                            .map(item -> toDetailItem(
                                    item,
                                    answersByItemId.get(item.getId()),
                                    recordCounts.getOrDefault(item.getId(), 0),
                                    summaries.get(item.getId())
                            ))
                            .toList();
            int completed = (int) itemResponses.stream()
                    .filter(ChecklistDetailResponse.DetailItemResponse::isCompleted)
                    .count();
            categories.add(new ChecklistDetailResponse.DetailCategoryResponse(
                    entry.getKey(),
                    completed,
                    itemResponses.size(),
                    itemResponses
            ));
        }
        return categories;
    }

    private static LinkedHashMap<String, List<ChecklistItem>> groupByCategory(
            List<ChecklistItem> items
    ) {
        LinkedHashMap<String, List<ChecklistItem>> grouped = new LinkedHashMap<>();
        for (ChecklistItem item : items) {
            grouped.computeIfAbsent(item.getCategory(), ignored -> new ArrayList<>())
                    .add(item);
        }
        return grouped;
    }

    private static ChecklistGenerateResponse.GenerateItemResponse toGenerateItem(
            ChecklistItem item,
            ChecklistAnswer answer,
            int recordCount
    ) {
        return new ChecklistGenerateResponse.GenerateItemResponse(
                item.getId(),
                item.getTitle(),
                item.getSubtitle(),
                item.getDisplayOrder(),
                isCompleted(answer),
                answer == null ? null : toOffset(answer.getCompletedAt()),
                recordCount
        );
    }

    private static ChecklistDetailResponse.DetailItemResponse toDetailItem(
            ChecklistItem item,
            ChecklistAnswer answer,
            int recordCount,
            FieldRecordCountRepository.RecordSummaryCount summary
    ) {
        ChecklistDetailResponse.RecordSummaryResponse recordSummary =
                summary == null
                        ? ChecklistDetailResponse.RecordSummaryResponse.empty()
                        : new ChecklistDetailResponse.RecordSummaryResponse(
                                summary.textCount(),
                                summary.photoCount(),
                                summary.sttCount()
                        );
        return new ChecklistDetailResponse.DetailItemResponse(
                item.getId(),
                item.getTitle(),
                item.getSubtitle(),
                item.getExample(),
                item.getDisplayOrder(),
                isCompleted(answer),
                answer == null ? null : toOffset(answer.getCompletedAt()),
                recordCount,
                recordSummary
        );
    }

    private static boolean isCompleted(ChecklistAnswer answer) {
        return answer != null && answer.isCompleted();
    }

    private static OffsetDateTime toOffset(java.time.Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }
}
