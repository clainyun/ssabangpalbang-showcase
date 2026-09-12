package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistAnswer;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationStage;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistDetailResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistGenerationStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistAnswerRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChecklistService {

    private final FieldVisitAccessService fieldVisitAccessService;
    private final ChecklistPersonalizationReader personalizationReader;
    private final ChecklistAiOrchestrator aiOrchestrator;
    private final ChecklistWriter checklistWriter;
    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistAnswerRepository checklistAnswerRepository;
    private final FieldRecordCountRepository fieldRecordCountRepository;
    private final ChecklistGenerationProgressService generationProgressService;

    public GenerateResult generate(
            Long studyId,
            Long memberId,
            String requestedAttemptId
    ) {
        String attemptId = resolveAttemptId(requestedAttemptId);
        FieldVisitParticipation participation =
                fieldVisitAccessService.requireChecklistGenerationParticipation(
                        studyId,
                        memberId,
                        ErrorCode.CHECKLIST_GENERATE_FORBIDDEN
                );

        Long sessionId = participation.session().getId();
        try {
            Optional<Checklist> existing = checklistRepository
                    .findBySessionIdAndMemberId(sessionId, memberId);
            if (existing.isPresent()) {
                GenerateResult result = new GenerateResult(
                        HttpStatus.OK,
                        ChecklistResponseCode.CHECKLIST_ALREADY_EXISTS,
                        loadGenerateResponse(studyId, memberId, existing.get())
                );
                generationProgressService.advance(
                        sessionId,
                        memberId,
                        attemptId,
                        ChecklistGenerationStage.COMPLETED
                );
                return result;
            }

            generationProgressService.restart(sessionId, memberId, attemptId);
            generationProgressService.advance(
                    sessionId,
                    memberId,
                    attemptId,
                    ChecklistGenerationStage.PERSONALIZATION
            );
            ChecklistPersonalizationResult personalization =
                    personalizationReader.read(memberId, studyId);
            if (!personalization.insufficient()) {
                generationProgressService.advance(
                        sessionId,
                        memberId,
                        attemptId,
                        ChecklistGenerationStage.AI_GENERATION
                );
            }
            GeneratedChecklistContent content = aiOrchestrator.resolve(personalization);
            generationProgressService.advance(
                    sessionId,
                    memberId,
                    attemptId,
                    ChecklistGenerationStage.CONTENT_READY
            );
            generationProgressService.advance(
                    sessionId,
                    memberId,
                    attemptId,
                    ChecklistGenerationStage.RESULT_SAVING
            );

            GenerateResult result;
            try {
                ChecklistWriter.SavedChecklist saved = checklistWriter.save(
                        sessionId,
                        memberId,
                        content
                );
                ChecklistResponseCode code = content.fallback()
                        ? ChecklistResponseCode.CHECKLIST_FALLBACK_GENERATE_SUCCESS
                        : ChecklistResponseCode.CHECKLIST_GENERATE_SUCCESS;
                result = new GenerateResult(
                        HttpStatus.CREATED,
                        code,
                        loadGenerateResponse(studyId, memberId, saved.checklist(), saved.items())
                );
            } catch (DataIntegrityViolationException exception) {
                Checklist concurrent = checklistRepository
                        .findBySessionIdAndMemberId(sessionId, memberId)
                        .orElseThrow(() -> exception);
                log.info(
                        "Checklist UNIQUE conflict resolved by reload. sessionId={}, memberId={}",
                        sessionId,
                        memberId
                );
                result = new GenerateResult(
                        HttpStatus.OK,
                        ChecklistResponseCode.CHECKLIST_ALREADY_EXISTS,
                        loadGenerateResponse(studyId, memberId, concurrent)
                );
            }
            generationProgressService.advance(
                    sessionId,
                    memberId,
                    attemptId,
                    ChecklistGenerationStage.COMPLETED
            );
            return result;
        } catch (RuntimeException exception) {
            generationProgressService.fail(sessionId, memberId, attemptId);
            throw exception;
        }
    }

    public ChecklistGenerationStatusResponse getGenerationStatus(
            Long studyId,
            Long memberId,
            String attemptId
    ) {
        return generationProgressService.getStatus(studyId, memberId, attemptId);
    }

    private String resolveAttemptId(String requestedAttemptId) {
        if (requestedAttemptId == null || requestedAttemptId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestedAttemptId.trim();
    }

    @Transactional(readOnly = true)
    public ChecklistDetailResponse getChecklist(Long studyId, Long memberId) {
        FieldVisitReadStatus status = fieldVisitAccessService.readStatus(
                studyId,
                memberId,
                ErrorCode.CHECKLIST_ACCESS_DENIED
        );

        if (!status.isSessionStarted()) {
            return new ChecklistDetailResponse(
                    studyId,
                    null,
                    null,
                    false,
                    null
            );
        }

        Optional<Checklist> checklistOpt = checklistRepository
                .findBySessionIdAndMemberId(status.sessionId(), memberId);
        if (checklistOpt.isEmpty()) {
            return new ChecklistDetailResponse(
                    studyId,
                    status.sessionId(),
                    status.participantStatusName(),
                    status.readOnly(),
                    null
            );
        }

        Checklist checklist = checklistOpt.get();
        List<ChecklistItem> items = checklistItemRepository
                .findByChecklistIdOrderByDisplayOrderAsc(checklist.getId());
        Map<Long, ChecklistAnswer> answers = loadAnswers(items);
        List<Long> itemIds = items.stream().map(ChecklistItem::getId).toList();
        Map<Long, Integer> recordCounts = fieldRecordCountRepository
                .countByAuthorAndItemIds(memberId, itemIds);
        Map<Long, FieldRecordCountRepository.RecordSummaryCount> summaries =
                fieldRecordCountRepository.summarizeByAuthorAndItemIds(memberId, itemIds);

        return new ChecklistDetailResponse(
                studyId,
                status.sessionId(),
                status.participantStatusName(),
                status.readOnly(),
                ChecklistResponseAssembler.toDetailBody(
                        checklist,
                        items,
                        answers,
                        recordCounts,
                        summaries
                )
        );
    }

    private ChecklistGenerateResponse loadGenerateResponse(
            Long studyId,
            Long memberId,
            Checklist checklist
    ) {
        List<ChecklistItem> items = checklistItemRepository
                .findByChecklistIdOrderByDisplayOrderAsc(checklist.getId());
        return loadGenerateResponse(studyId, memberId, checklist, items);
    }

    private ChecklistGenerateResponse loadGenerateResponse(
            Long studyId,
            Long memberId,
            Checklist checklist,
            List<ChecklistItem> items
    ) {
        Map<Long, ChecklistAnswer> answers = loadAnswers(items);
        List<Long> itemIds = items.stream().map(ChecklistItem::getId).toList();
        Map<Long, Integer> recordCounts = fieldRecordCountRepository
                .countByAuthorAndItemIds(memberId, itemIds);
        return ChecklistResponseAssembler.toGenerateResponse(
                studyId,
                checklist,
                items,
                answers,
                recordCounts
        );
    }

    private Map<Long, ChecklistAnswer> loadAnswers(List<ChecklistItem> items) {
        List<Long> itemIds = items.stream().map(ChecklistItem::getId).toList();
        return ChecklistResponseAssembler.indexAnswers(
                checklistAnswerRepository.findByChecklistItemIdIn(itemIds)
        );
    }

    public record GenerateResult(
            HttpStatus httpStatus,
            ChecklistResponseCode responseCode,
            ChecklistGenerateResponse body
    ) {
    }
}
