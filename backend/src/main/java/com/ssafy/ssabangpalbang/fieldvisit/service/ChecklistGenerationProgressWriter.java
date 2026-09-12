package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationProgressStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationStage;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistGenerationProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChecklistGenerationProgressWriter {

    private final ChecklistGenerationProgressRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restart(Long sessionId, Long memberId, String attemptId) {
        ChecklistGenerationStage stage = ChecklistGenerationStage.PREPARING;
        repository.restart(
                sessionId,
                memberId,
                attemptId,
                stage.name(),
                stage.progressRate(),
                stage.message()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void advance(
            Long sessionId,
            Long memberId,
            String attemptId,
            ChecklistGenerationStage stage
    ) {
        ChecklistGenerationProgressStatus status =
                stage == ChecklistGenerationStage.COMPLETED
                        ? ChecklistGenerationProgressStatus.DONE
                        : ChecklistGenerationProgressStatus.IN_PROGRESS;
        repository.advance(
                sessionId,
                memberId,
                attemptId,
                status.name(),
                stage.name(),
                stage.progressRate(),
                stage.message()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(
            Long sessionId,
            Long memberId,
            String attemptId,
            String failureMessage
    ) {
        ChecklistGenerationStage stage = ChecklistGenerationStage.PREPARING;
        repository.fail(
                sessionId,
                memberId,
                attemptId,
                stage.name(),
                stage.progressRate(),
                stage.message(),
                failureMessage
        );
    }
}
