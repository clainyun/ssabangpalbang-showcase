package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.request.SttCreateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.request.SttRetryRequest;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttCreateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttRetryResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.AudioFileView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.ChecklistItemView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.FieldSessionView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttStatusQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttStatusQueryRepository.AudioFileStateView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttStatusQueryRepository.FieldRecordView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.response.SttResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.stt.support.SttIdGenerator;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SttService {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String STT_AUDIO = "STT_AUDIO";
    private static final String COMPLETED = "COMPLETED";
    private static final String STT = "STT";
    private static final String DONE = "DONE";

    private final LoginMemberResolver loginMemberResolver;
    private final StudyMemberRepository studyMemberRepository;
    private final SttJobRepository sttJobRepository;
    private final SttJobAttemptRepository sttJobAttemptRepository;
    private final SttRequestQueryRepository requestQueryRepository;
    private final SttStatusQueryRepository statusQueryRepository;
    private final SttCreationTransaction creationTransaction;
    private final SttRetryTransaction retryTransaction;
    private final SttIdGenerator sttIdGenerator;

    public CreateResult createStt(
            Long studyId,
            SttCreateRequest request
    ) {
        LoginMember loginMember = requireActiveLoginMember();
        UUID clientRequestId = parseClientRequestId(request.clientRequestId());

        Optional<CreateResult> existingByRequest = findByClientRequestId(
                loginMember.memberId(),
                clientRequestId,
                studyId,
                request
        );
        if (existingByRequest.isPresent()) {
            return existingByRequest.get();
        }

        Optional<CreateResult> existingByAudio = findByAudioFileId(
                loginMember.memberId(),
                studyId,
                request
        );
        if (existingByAudio.isPresent()) {
            return existingByAudio.get();
        }

        OffsetDateTime now = OffsetDateTime.now(SEOUL_ZONE_ID);
        Long sessionId = validateRequestContext(
                loginMember.memberId(),
                studyId,
                request
        );
        AudioFileView audioFile = requestQueryRepository
                .findAudioFile(request.audioFileId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEDIA_FILE_NOT_FOUND
                ));
        validateAudioFile(
                audioFile,
                loginMember.memberId(),
                studyId,
                now
        );

        SttCreatePersistenceCommand command = new SttCreatePersistenceCommand(
                sttIdGenerator.generate(),
                loginMember.memberId(),
                studyId,
                sessionId,
                request.audioFileId(),
                request.checklistItemId(),
                clientRequestId,
                now,
                audioFile.objectKey(),
                audioFile.contentType()
        );

        try {
            SttJob created = creationTransaction.create(command);
            return new CreateResult(
                    HttpStatus.ACCEPTED,
                    SttResponseCode.FIELD_STT_ACCEPTED,
                    SttCreateResponse.from(created)
            );
        } catch (DataIntegrityViolationException exception) {
            return resolveConcurrentRequest(
                    loginMember.memberId(),
                    clientRequestId,
                    studyId,
                    request,
                    exception
            );
        }
    }

    public RetryResult retryStt(
            Long studyId,
            String sttId,
            SttRetryRequest request
    ) {
        LoginMember loginMember = requireActiveLoginMember();
        UUID clientRequestId = parseClientRequestId(
                request.clientRequestId()
        );
        SttRetryCommand command = new SttRetryCommand(
                loginMember.memberId(),
                studyId,
                sttId,
                clientRequestId,
                OffsetDateTime.now(SEOUL_ZONE_ID)
        );

        SttRetryTransaction.TransactionResult transactionResult;
        try {
            transactionResult = retryTransaction.retry(command);
        } catch (DataIntegrityViolationException exception) {
            transactionResult = retryTransaction.retry(command);
        }

        if (transactionResult.accepted()) {
            return new RetryResult(
                    HttpStatus.ACCEPTED,
                    SttResponseCode.FIELD_STT_RETRY_ACCEPTED,
                    SttRetryResponse.from(
                            transactionResult.job(),
                            transactionResult.retryRequestedAt()
                    )
            );
        }

        return new RetryResult(
                HttpStatus.OK,
                SttResponseCode.FIELD_STT_RETRY_ALREADY_IN_PROGRESS,
                SttRetryResponse.from(
                        transactionResult.job(),
                        transactionResult.retryRequestedAt()
                )
        );
    }

    @Transactional(readOnly = true)
    public SttStatusResponse getSttStatus(
            Long studyId,
            String sttId
    ) {
        LoginMember loginMember = requireActiveLoginMember();
        SttJob job = sttJobRepository.findBySttId(sttId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_STT_NOT_FOUND
                ));

        if (!Objects.equals(job.getStudyId(), studyId)) {
            throw new BusinessException(ErrorCode.FIELD_STT_STUDY_MISMATCH);
        }
        if (!Objects.equals(job.getMemberId(), loginMember.memberId())) {
            throw new BusinessException(ErrorCode.FIELD_STT_STATUS_FORBIDDEN);
        }

        return switch (job.getStatus()) {
            case PENDING, PROCESSING -> inProgressResponse(job);
            case DONE -> doneResponse(job);
            case FAILED -> failedResponse(job);
        };
    }

    private SttStatusResponse inProgressResponse(SttJob job) {
        return new SttStatusResponse(
                job.getSttId(),
                job.getStudyId(),
                job.getChecklistItemId(),
                job.getStatus(),
                null,
                null,
                null,
                false,
                job.getRequestedAt(),
                null
        );
    }

    private SttStatusResponse doneResponse(SttJob job) {
        if (job.getFieldRecordId() == null || job.getCompletedAt() == null) {
            throw inconsistentDone(job, "완료 연결 정보가 없습니다.");
        }

        FieldRecordView fieldRecord = statusQueryRepository
                .findFieldRecord(job.getFieldRecordId())
                .orElseThrow(() -> inconsistentDone(
                        job,
                        "연결된 현장 기록이 없습니다."
                ));
        if (!Objects.equals(
                fieldRecord.fieldRecordId(),
                job.getFieldRecordId()
        )
                || !Objects.equals(
                fieldRecord.checklistItemId(),
                job.getChecklistItemId()
        )
                || !STT.equals(fieldRecord.sourceType())
                || !DONE.equals(fieldRecord.sttStatus())
                || fieldRecord.textContent() == null
                || fieldRecord.textContent().isBlank()) {
            throw inconsistentDone(job, "현장 기록이 완료 조건과 일치하지 않습니다.");
        }

        return new SttStatusResponse(
                job.getSttId(),
                job.getStudyId(),
                job.getChecklistItemId(),
                SttStatus.DONE,
                fieldRecord.fieldRecordId(),
                fieldRecord.textContent(),
                null,
                false,
                job.getRequestedAt(),
                job.getCompletedAt()
        );
    }

    private SttStatusResponse failedResponse(SttJob job) {
        OffsetDateTime now = OffsetDateTime.now(SEOUL_ZONE_ID);
        boolean retryable = job.isRetryable()
                && statusQueryRepository
                .findAudioFileState(job.getAudioFileId())
                .filter(audioFile -> isRetryableAudio(audioFile, now))
                .isPresent();

        return new SttStatusResponse(
                job.getSttId(),
                job.getStudyId(),
                job.getChecklistItemId(),
                SttStatus.FAILED,
                null,
                null,
                job.getFailReason(),
                retryable,
                job.getRequestedAt(),
                null
        );
    }

    private boolean isRetryableAudio(
            AudioFileStateView audioFile,
            OffsetDateTime now
    ) {
        return STT_AUDIO.equals(audioFile.fileUsage())
                && COMPLETED.equals(audioFile.uploadStatus())
                && audioFile.deletedAt() == null
                && audioFile.expiresAt() != null
                && audioFile.expiresAt().isAfter(now);
    }

    private IllegalStateException inconsistentDone(
            SttJob job,
            String reason
    ) {
        return new IllegalStateException(
                "STT DONE 데이터 정합성 오류: sttId="
                        + job.getSttId()
                        + ", reason="
                        + reason
        );
    }

    private LoginMember requireActiveLoginMember() {
        LoginMember loginMember = loginMemberResolver.resolve();
        if (!loginMember.active()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return loginMember;
    }

    private UUID parseClientRequestId(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field", "clientRequestId",
                            "reason", "clientRequestId는 UUID 형식이어야 합니다."
                    )
            );
        }
    }

    private Optional<CreateResult> findByClientRequestId(
            Long memberId,
            UUID clientRequestId,
            Long studyId,
            SttCreateRequest request
    ) {
        return sttJobAttemptRepository
                .findByMemberIdAndClientRequestId(memberId, clientRequestId)
                .map(attempt -> findJob(attempt))
                .map(job -> existingResult(
                        job,
                        memberId,
                        studyId,
                        request,
                        ErrorCode.FIELD_STT_IDEMPOTENCY_KEY_REUSED
                ));
    }

    private Optional<CreateResult> findByAudioFileId(
            Long memberId,
            Long studyId,
            SttCreateRequest request
    ) {
        return sttJobRepository.findByAudioFileId(request.audioFileId())
                .map(job -> existingResult(
                        job,
                        memberId,
                        studyId,
                        request,
                        ErrorCode.FIELD_STT_AUDIO_INVALID
                ));
    }

    private SttJob findJob(SttJobAttempt attempt) {
        return sttJobRepository.findById(attempt.getSttJobId())
                .orElseThrow(() -> new IllegalStateException(
                        "STT attempt가 참조하는 작업을 찾을 수 없습니다."
                ));
    }

    private CreateResult existingResult(
            SttJob job,
            Long memberId,
            Long studyId,
            SttCreateRequest request,
            ErrorCode mismatchError
    ) {
        if (!job.hasSameFingerprint(
                memberId,
                studyId,
                request.audioFileId(),
                request.checklistItemId()
        )) {
            if (mismatchError == ErrorCode.FIELD_STT_AUDIO_INVALID) {
                if (!Objects.equals(job.getMemberId(), memberId)
                        || !Objects.equals(job.getStudyId(), studyId)) {
                    throw new BusinessException(
                            ErrorCode.FIELD_STT_REQUEST_FORBIDDEN
                    );
                }
                throw invalidAudio(request.audioFileId());
            }
            throw new BusinessException(mismatchError);
        }

        return new CreateResult(
                HttpStatus.OK,
                SttResponseCode.FIELD_STT_ALREADY_REQUESTED,
                SttCreateResponse.from(job)
        );
    }

    private Long validateRequestContext(
            Long memberId,
            Long studyId,
            SttCreateRequest request
    ) {
        boolean activeStudyMember = studyMemberRepository
                .findByStudyIdAndMemberId(studyId, memberId)
                .filter(member -> member.getStatus() == StudyMemberStatus.ACTIVE)
                .isPresent();
        if (!activeStudyMember) {
            throw new BusinessException(ErrorCode.FIELD_STT_REQUEST_FORBIDDEN);
        }

        FieldSessionView session = requestQueryRepository
                .findFieldSessionByStudyId(studyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_STT_REQUEST_FORBIDDEN
                ));
        if (!IN_PROGRESS.equals(session.status())) {
            throw new BusinessException(
                    ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED
            );
        }

        String participantStatus = requestQueryRepository
                .findParticipantStatus(session.sessionId(), memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_STT_REQUEST_FORBIDDEN
                ));
        if (!IN_PROGRESS.equals(participantStatus)) {
            throw new BusinessException(
                    ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED
            );
        }

        ChecklistItemView checklistItem = requestQueryRepository
                .findChecklistItem(request.checklistItemId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHECKLIST_ITEM_NOT_FOUND
                ));
        if (!Objects.equals(checklistItem.memberId(), memberId)
                || !Objects.equals(
                checklistItem.sessionId(),
                session.sessionId()
        )
                || !Objects.equals(checklistItem.studyId(), studyId)) {
            throw new BusinessException(ErrorCode.FIELD_STT_REQUEST_FORBIDDEN);
        }

        return session.sessionId();
    }

    private void validateAudioFile(
            AudioFileView audioFile,
            Long memberId,
            Long studyId,
            OffsetDateTime now
    ) {
        if (!Objects.equals(audioFile.ownerId(), memberId)
                || !Objects.equals(audioFile.studyId(), studyId)) {
            throw new BusinessException(ErrorCode.FIELD_STT_REQUEST_FORBIDDEN);
        }
        if (!STT_AUDIO.equals(audioFile.fileUsage())
                || !COMPLETED.equals(audioFile.uploadStatus())) {
            throw invalidAudio(audioFile.audioFileId());
        }
        if (audioFile.deletedAt() != null) {
            throw invalidAudio(audioFile.audioFileId());
        }
        if (audioFile.expiresAt() == null) {
            throw invalidAudio(audioFile.audioFileId());
        }
        if (!audioFile.expiresAt().isAfter(now)) {
            throw new BusinessException(
                    ErrorCode.FIELD_STT_AUDIO_EXPIRED,
                    Map.of("audioFileId", audioFile.audioFileId())
            );
        }
        if (audioFile.objectKey() == null
                || audioFile.objectKey().isBlank()
                || audioFile.contentType() == null
                || audioFile.contentType().isBlank()) {
            throw invalidAudio(audioFile.audioFileId());
        }
    }

    private BusinessException invalidAudio(Long audioFileId) {
        return new BusinessException(
                ErrorCode.FIELD_STT_AUDIO_INVALID,
                Map.of(
                        "audioFileId", audioFileId,
                        "requiredFileUsage", STT_AUDIO
                )
        );
    }

    private CreateResult resolveConcurrentRequest(
            Long memberId,
            UUID clientRequestId,
            Long studyId,
            SttCreateRequest request,
            DataIntegrityViolationException originalException
    ) {
        Optional<CreateResult> byRequest = findByClientRequestId(
                memberId,
                clientRequestId,
                studyId,
                request
        );
        if (byRequest.isPresent()) {
            return byRequest.get();
        }

        Optional<CreateResult> byAudio = findByAudioFileId(
                memberId,
                studyId,
                request
        );
        if (byAudio.isPresent()) {
            return byAudio.get();
        }

        throw originalException;
    }

    public record CreateResult(
            HttpStatus httpStatus,
            SttResponseCode responseCode,
            SttCreateResponse response
    ) {
    }

    public record RetryResult(
            HttpStatus httpStatus,
            SttResponseCode responseCode,
            SttRetryResponse response
    ) {
    }
}
