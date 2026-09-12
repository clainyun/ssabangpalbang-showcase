package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationStage;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistGenerationStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistGenerationProgressRepository;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChecklistGenerationProgressService {

    private static final String FAILURE_MESSAGE = "체크리스트 생성에 실패했어요.";

    private final ChecklistGenerationProgressWriter writer;
    private final ChecklistGenerationProgressRepository repository;
    private final FieldVisitAccessService fieldVisitAccessService;

    public void restart(Long sessionId, Long memberId, String attemptId) {
        safely(
                "restart",
                sessionId,
                memberId,
                () -> writer.restart(sessionId, memberId, attemptId)
        );
    }

    public void advance(
            Long sessionId,
            Long memberId,
            String attemptId,
            ChecklistGenerationStage stage
    ) {
        safely(
                stage.name(),
                sessionId,
                memberId,
                () -> writer.advance(sessionId, memberId, attemptId, stage)
        );
    }

    public void fail(Long sessionId, Long memberId, String attemptId) {
        safely(
                "fail",
                sessionId,
                memberId,
                () -> writer.fail(sessionId, memberId, attemptId, FAILURE_MESSAGE)
        );
    }

    @Transactional(readOnly = true)
    public ChecklistGenerationStatusResponse getStatus(
            Long studyId,
            Long memberId,
            String attemptId
    ) {
        FieldVisitReadStatus readStatus = fieldVisitAccessService.readStatus(
                studyId,
                memberId,
                ErrorCode.CHECKLIST_ACCESS_DENIED
        );
        if (!readStatus.isSessionStarted()) {
            return ChecklistGenerationStatusResponse.pending(attemptId);
        }
        var progress = attemptId == null || attemptId.isBlank()
                ? repository.findFirstBySessionIdAndMemberIdOrderByUpdatedAtDesc(
                        readStatus.sessionId(),
                        memberId
                )
                : repository.findBySessionIdAndMemberIdAndAttemptId(
                        readStatus.sessionId(),
                        memberId,
                        attemptId
                );
        return progress
                .map(ChecklistGenerationStatusResponse::from)
                .orElseGet(() -> ChecklistGenerationStatusResponse.pending(attemptId));
    }

    private void safely(
            String action,
            Long sessionId,
            Long memberId,
            Runnable operation
    ) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            log.error(
                    "Checklist progress update failed. action={}, sessionId={}, memberId={}",
                    action,
                    sessionId,
                    memberId,
                    exception
            );
        }
    }
}
