package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttDispatchOutbox;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttRequestType;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchCommand;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttDispatchOutboxRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.AudioFileView;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SttRetryTransaction {

    private static final String DEFAULT_LANGUAGE = "ko-KR";
    private static final String STT_AUDIO = "STT_AUDIO";
    private static final String COMPLETED = "COMPLETED";

    private final SttJobRepository sttJobRepository;
    private final SttJobAttemptRepository sttJobAttemptRepository;
    private final SttRequestQueryRepository requestQueryRepository;
    private final SttDispatchOutboxRepository dispatchOutboxRepository;

    @Transactional
    public TransactionResult retry(SttRetryCommand command) {
        Optional<SttJobAttempt> existingRequest = sttJobAttemptRepository
                .findByMemberIdAndClientRequestId(
                        command.memberId(),
                        command.clientRequestId()
                );
        if (existingRequest.isPresent()) {
            return existingRequestResult(command, existingRequest.get());
        }

        SttJob job = sttJobRepository.findBySttIdForUpdate(command.sttId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_STT_NOT_FOUND
                ));
        validateJobAccess(job, command);

        SttJobAttempt latestAttempt = latestAttempt(job);
        if (job.isInProgress()) {
            return new TransactionResult(
                    job,
                    latestAttempt.getRequestedAt(),
                    false
            );
        }
        if (job.getStatus() != SttStatus.FAILED || !job.isRetryable()) {
            throw new BusinessException(
                    ErrorCode.FIELD_STT_RETRY_NOT_ALLOWED
            );
        }

        AudioFileView audioFile = requestQueryRepository
                .findAudioFileForUpdate(job.getAudioFileId())
                .orElseThrow(() -> expiredAudio(job.getAudioFileId()));
        validateAudio(job, audioFile, command.requestedAt());

        int attemptNo = latestAttempt.getAttemptNo() + 1;
        SttJobAttempt retryAttempt = SttJobAttempt.retry(
                job.getId(),
                job.getMemberId(),
                command.clientRequestId(),
                attemptNo,
                command.requestedAt()
        );
        job.prepareRetry(command.requestedAt());
        sttJobAttemptRepository.saveAndFlush(retryAttempt);

        dispatchOutboxRepository.save(SttDispatchOutbox.pending(
                job.getId(),
                new SttDispatchCommand(
                        job.getSttId(),
                        attemptNo,
                        job.getAudioFileId(),
                        audioFile.objectKey(),
                        audioFile.contentType(),
                        DEFAULT_LANGUAGE
                ),
                command.requestedAt()
        ));

        return new TransactionResult(job, retryAttempt.getRequestedAt(), true);
    }

    private TransactionResult existingRequestResult(
            SttRetryCommand command,
            SttJobAttempt attempt
    ) {
        SttJob existingJob = sttJobRepository.findById(attempt.getSttJobId())
                .orElseThrow(() -> new IllegalStateException(
                        "STT attempt가 참조하는 작업을 찾을 수 없습니다."
                ));
        if (attempt.getRequestType() != SttRequestType.RETRY
                || !Objects.equals(existingJob.getSttId(), command.sttId())) {
            throw reusedClientRequestId();
        }
        validateJobAccess(existingJob, command);

        return new TransactionResult(
                existingJob,
                attempt.getRequestedAt(),
                false
        );
    }

    private void validateJobAccess(
            SttJob job,
            SttRetryCommand command
    ) {
        if (!Objects.equals(job.getStudyId(), command.studyId())) {
            throw new BusinessException(ErrorCode.FIELD_STT_STUDY_MISMATCH);
        }
        if (!Objects.equals(job.getMemberId(), command.memberId())) {
            throw new BusinessException(ErrorCode.FIELD_STT_RETRY_FORBIDDEN);
        }
    }

    private SttJobAttempt latestAttempt(SttJob job) {
        return sttJobAttemptRepository
                .findTopBySttJobIdOrderByAttemptNoDesc(job.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "STT 작업의 attempt 이력이 없습니다. sttId="
                                + job.getSttId()
                ));
    }

    private void validateAudio(
            SttJob job,
            AudioFileView audioFile,
            OffsetDateTime now
    ) {
        if (audioFile.deletedAt() != null
                || audioFile.expiresAt() == null
                || !audioFile.expiresAt().isAfter(now)) {
            throw expiredAudio(audioFile.audioFileId());
        }

        if (!Objects.equals(audioFile.ownerId(), job.getMemberId())
                || !Objects.equals(audioFile.studyId(), job.getStudyId())
                || !STT_AUDIO.equals(audioFile.fileUsage())
                || !COMPLETED.equals(audioFile.uploadStatus())
                || audioFile.objectKey() == null
                || audioFile.objectKey().isBlank()
                || audioFile.contentType() == null
                || audioFile.contentType().isBlank()) {
            throw new BusinessException(
                    ErrorCode.FIELD_STT_RETRY_NOT_ALLOWED
            );
        }
    }

    private BusinessException expiredAudio(Long audioFileId) {
        return new BusinessException(
                ErrorCode.FIELD_STT_AUDIO_EXPIRED,
                Map.of("audioFileId", audioFileId)
        );
    }

    private BusinessException reusedClientRequestId() {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of(
                        "field", "clientRequestId",
                        "reason", "clientRequestId가 다른 STT 요청에 사용되었습니다."
                )
        );
    }

    public record TransactionResult(
            SttJob job,
            OffsetDateTime retryRequestedAt,
            boolean accepted
    ) {
    }
}
