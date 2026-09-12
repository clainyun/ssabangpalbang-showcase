package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttAudioCleanupJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttAudioCleanupJobRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttResultPersistenceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class SttResultHandler {

    private static final Logger log =
            LoggerFactory.getLogger(SttResultHandler.class);

    private final SttJobRepository sttJobRepository;
    private final SttJobAttemptRepository sttJobAttemptRepository;
    private final SttResultPersistenceRepository persistenceRepository;
    private final SttAudioCleanupJobRepository cleanupJobRepository;
    private final Clock clock;

    @Transactional
    public HandlingOutcome markProcessing(SttProcessingResult result) {
        CurrentAttempt current = currentAttempt(
                result.sttId(),
                result.attemptNo()
        );
        if (current.outcome() != null) {
            return current.outcome();
        }

        SttJob job = current.job();
        SttJobAttempt attempt = current.attempt();
        if (job.getStatus() == SttStatus.PROCESSING) {
            return HandlingOutcome.IGNORED_DUPLICATE;
        }
        if (job.getStatus() != SttStatus.PENDING || attempt.isTerminal()) {
            return HandlingOutcome.IGNORED_DUPLICATE;
        }

        job.start(result.startedAt(), OffsetDateTime.now(clock));
        attempt.start(result.startedAt());
        return HandlingOutcome.APPLIED;
    }

    @Transactional
    public HandlingOutcome handleSuccess(SttSuccessResult result) {
        CurrentAttempt current = currentAttempt(
                result.sttId(),
                result.attemptNo()
        );
        if (current.outcome() != null) {
            return current.outcome();
        }

        SttJob job = current.job();
        SttJobAttempt attempt = current.attempt();
        if (isAlreadyTerminal(job, attempt)) {
            return HandlingOutcome.IGNORED_DUPLICATE;
        }
        if (result.textContent() == null || result.textContent().isBlank()) {
            throw new IllegalArgumentException(
                    "STT 성공 결과의 textContent는 필수입니다."
            );
        }

        Long fieldRecordId = persistenceRepository.createFieldRecord(
                job,
                result.textContent(),
                result.completedAt()
        );
        String objectKey = persistenceRepository.markAudioDeleted(
                job.getAudioFileId(),
                result.completedAt()
        );
        attempt.complete(result.completedAt());
        job.complete(fieldRecordId, result.completedAt());
        cleanupJobRepository.save(SttAudioCleanupJob.pending(
                job.getAudioFileId(),
                objectKey,
                OffsetDateTime.now(clock)
        ));

        return HandlingOutcome.APPLIED;
    }

    @Transactional
    public HandlingOutcome handleFailure(SttFailureResult result) {
        CurrentAttempt current = currentAttempt(
                result.sttId(),
                result.attemptNo()
        );
        if (current.outcome() != null) {
            return current.outcome();
        }

        SttJob job = current.job();
        SttJobAttempt attempt = current.attempt();
        if (isAlreadyTerminal(job, attempt)) {
            return HandlingOutcome.IGNORED_DUPLICATE;
        }

        attempt.fail(
                result.failCode(),
                result.failReason(),
                result.failedAt()
        );
        job.fail(
                result.failCode(),
                result.failReason(),
                result.retryable(),
                result.failedAt()
        );
        return HandlingOutcome.APPLIED;
    }

    private CurrentAttempt currentAttempt(
            String sttId,
            int resultAttemptNo
    ) {
        SttJob job = sttJobRepository.findBySttIdForUpdate(sttId)
                .orElse(null);
        if (job == null) {
            return CurrentAttempt.ignored(HandlingOutcome.IGNORED_UNKNOWN);
        }
        SttJobAttempt attempt = sttJobAttemptRepository
                .findTopBySttJobIdOrderByAttemptNoDesc(job.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "STT 작업의 attempt 이력이 없습니다. sttId=" + sttId
                ));

        if (resultAttemptNo < attempt.getAttemptNo()) {
            return CurrentAttempt.ignored(HandlingOutcome.IGNORED_STALE);
        }
        if (resultAttemptNo > attempt.getAttemptNo()) {
            log.warn(
                    "현재보다 큰 STT attempt 결과를 무시합니다. "
                            + "sttId={}, resultAttemptNo={}, currentAttemptNo={}",
                    sttId,
                    resultAttemptNo,
                    attempt.getAttemptNo()
            );
            return CurrentAttempt.ignored(HandlingOutcome.IGNORED_FUTURE);
        }
        return new CurrentAttempt(job, attempt, null);
    }

    private boolean isAlreadyTerminal(
            SttJob job,
            SttJobAttempt attempt
    ) {
        return job.getStatus() == SttStatus.DONE
                || attempt.isTerminal()
                || job.getStatus() == SttStatus.FAILED;
    }

    public enum HandlingOutcome {
        APPLIED,
        IGNORED_STALE,
        IGNORED_FUTURE,
        IGNORED_DUPLICATE,
        IGNORED_UNKNOWN
    }

    private record CurrentAttempt(
            SttJob job,
            SttJobAttempt attempt,
            HandlingOutcome outcome
    ) {

        private static CurrentAttempt ignored(HandlingOutcome outcome) {
            return new CurrentAttempt(null, null, outcome);
        }
    }
}
