package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistAnswer;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistAnswerResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.ChecklistAnswerSaveRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistAnswerSaveResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistAnswerRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChecklistAnswerService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final FieldVisitAccessService fieldVisitAccessService;
    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistAnswerRepository checklistAnswerRepository;

    @Transactional
    public SaveResult saveAnswers(
            Long studyId,
            Long memberId,
            ChecklistAnswerSaveRequest request
    ) {
        FieldVisitParticipation participation =
                fieldVisitAccessService.requireWritableParticipation(
                        studyId,
                        memberId,
                        ErrorCode.CHECKLIST_COMPLETION_FORBIDDEN,
                        FieldVisitAccessService.CHECKLIST_ANSWER_PARTICIPANT_ENDED_MESSAGE
                );

        List<ChecklistAnswerSaveRequest.AnswerItem> answers = request.answers();
        Set<Long> uniqueIds = new HashSet<>();
        for (ChecklistAnswerSaveRequest.AnswerItem answer : answers) {
            if (!uniqueIds.add(answer.checklistItemId())) {
                throw new BusinessException(ErrorCode.CHECKLIST_ITEM_DUPLICATED);
            }
        }

        Checklist checklist = checklistRepository
                .findBySessionIdAndMemberIdForUpdate(
                        participation.session().getId(),
                        memberId
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_NOT_FOUND));

        List<Long> itemIds = answers.stream()
                .map(ChecklistAnswerSaveRequest.AnswerItem::checklistItemId)
                .toList();
        List<ChecklistItem> ownedItems = checklistItemRepository
                .findByIdInAndChecklistId(itemIds, checklist.getId());
        if (ownedItems.size() != itemIds.size()) {
            throw new BusinessException(ErrorCode.CHECKLIST_ITEM_ACCESS_DENIED);
        }

        Instant now = Instant.now();
        Map<Long, ChecklistAnswer> existing = checklistAnswerRepository
                .findByChecklistItemIdIn(itemIds)
                .stream()
                .collect(Collectors.toMap(
                        ChecklistAnswer::getChecklistItemId,
                        Function.identity()
                ));

        List<ChecklistAnswer> toSave = new ArrayList<>();
        for (ChecklistAnswerSaveRequest.AnswerItem item : answers) {
            ChecklistAnswer answer = existing.get(item.checklistItemId());
            if (answer == null) {
                answer = ChecklistAnswer.create(
                        item.checklistItemId(),
                        Boolean.TRUE.equals(item.isCompleted()),
                        now
                );
            } else {
                answer.applyCompletion(
                        Boolean.TRUE.equals(item.isCompleted()),
                        now
                );
            }
            toSave.add(answer);
        }
        checklistAnswerRepository.saveAll(toSave);

        List<ChecklistItem> allItems = checklistItemRepository
                .findByChecklistIdOrderByDisplayOrderAsc(checklist.getId());
        Map<Long, ChecklistAnswer> allAnswers = checklistAnswerRepository
                .findByChecklistItemIdIn(
                        allItems.stream().map(ChecklistItem::getId).toList()
                )
                .stream()
                .collect(Collectors.toMap(
                        ChecklistAnswer::getChecklistItemId,
                        Function.identity()
                ));

        int completedCount = 0;
        Map<String, int[]> categoryCounts = new LinkedHashMap<>();
        for (ChecklistItem item : allItems) {
            ChecklistAnswer answer = allAnswers.get(item.getId());
            boolean completed = answer != null && answer.isCompleted();
            if (completed) {
                completedCount++;
            }
            int[] counts = categoryCounts.computeIfAbsent(
                    item.getCategory(),
                    ignored -> new int[2]
            );
            counts[1]++;
            if (completed) {
                counts[0]++;
            }
        }

        List<ChecklistAnswerSaveResponse.AnswerStatus> answerStatuses = answers.stream()
                .map(item -> {
                    ChecklistAnswer answer = toSave.stream()
                            .filter(a -> a.getChecklistItemId().equals(item.checklistItemId()))
                            .findFirst()
                            .orElseThrow();
                    return new ChecklistAnswerSaveResponse.AnswerStatus(
                            answer.getChecklistItemId(),
                            answer.isCompleted(),
                            toOffset(answer.getCompletedAt())
                    );
                })
                .toList();

        List<ChecklistAnswerSaveResponse.CategoryProgress> categoryProgress =
                categoryCounts.entrySet().stream()
                        .map(entry -> new ChecklistAnswerSaveResponse.CategoryProgress(
                                entry.getKey(),
                                entry.getValue()[0],
                                entry.getValue()[1]
                        ))
                        .toList();

        ChecklistAnswerSaveResponse body = new ChecklistAnswerSaveResponse(
                studyId,
                checklist.getId(),
                answers.size(),
                completedCount,
                allItems.size(),
                answerStatuses,
                categoryProgress
        );
        return new SaveResult(
                HttpStatus.OK,
                ChecklistAnswerResponseCode.CHECKLIST_COMPLETION_SAVE_SUCCESS,
                body
        );
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }

    public record SaveResult(
            HttpStatus httpStatus,
            ChecklistAnswerResponseCode responseCode,
            ChecklistAnswerSaveResponse body
    ) {
    }
}
