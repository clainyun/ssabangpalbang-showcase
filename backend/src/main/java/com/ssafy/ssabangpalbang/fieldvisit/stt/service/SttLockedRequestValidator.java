package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.AudioFileView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.ChecklistItemView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.FieldSessionView;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class SttLockedRequestValidator {

    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String STT_AUDIO = "STT_AUDIO";
    private static final String COMPLETED = "COMPLETED";

    private final StudyMemberRepository studyMemberRepository;
    private final SttRequestQueryRepository requestQueryRepository;

    public LockedRequestContext validate(
            SttCreatePersistenceCommand command
    ) {
        boolean activeStudyMember = studyMemberRepository
                .findForUpdateByStudyIdAndMemberId(
                        command.studyId(),
                        command.memberId()
                )
                .filter(member -> member.getStatus() == StudyMemberStatus.ACTIVE)
                .isPresent();
        if (!activeStudyMember) {
            throw new BusinessException(ErrorCode.FIELD_STT_REQUEST_FORBIDDEN);
        }

        FieldSessionView session = requestQueryRepository
                .findFieldSessionByStudyIdForUpdate(command.studyId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_STT_REQUEST_FORBIDDEN
                ));
        if (!IN_PROGRESS.equals(session.status())) {
            throw endedParticipant();
        }

        String participantStatus = requestQueryRepository
                .findParticipantStatusForUpdate(
                        session.sessionId(),
                        command.memberId()
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_STT_REQUEST_FORBIDDEN
                ));
        if (!IN_PROGRESS.equals(participantStatus)) {
            throw endedParticipant();
        }

        ChecklistItemView checklistItem = requestQueryRepository
                .findChecklistItemForUpdate(command.checklistItemId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHECKLIST_ITEM_NOT_FOUND
                ));
        if (!Objects.equals(checklistItem.memberId(), command.memberId())
                || !Objects.equals(
                checklistItem.sessionId(),
                session.sessionId()
        )
                || !Objects.equals(checklistItem.studyId(), command.studyId())) {
            throw new BusinessException(ErrorCode.FIELD_STT_REQUEST_FORBIDDEN);
        }

        AudioFileView audioFile = requestQueryRepository
                .findAudioFileForUpdate(command.audioFileId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEDIA_FILE_NOT_FOUND
                ));
        validateAudioFile(audioFile, command);

        return new LockedRequestContext(
                session.sessionId(),
                audioFile.objectKey(),
                audioFile.contentType()
        );
    }

    private void validateAudioFile(
            AudioFileView audioFile,
            SttCreatePersistenceCommand command
    ) {
        if (!Objects.equals(audioFile.ownerId(), command.memberId())
                || !Objects.equals(audioFile.studyId(), command.studyId())) {
            throw new BusinessException(ErrorCode.FIELD_STT_REQUEST_FORBIDDEN);
        }
        if (!STT_AUDIO.equals(audioFile.fileUsage())
                || !COMPLETED.equals(audioFile.uploadStatus())
                || audioFile.deletedAt() != null
                || audioFile.expiresAt() == null
                || audioFile.objectKey() == null
                || audioFile.objectKey().isBlank()
                || audioFile.contentType() == null
                || audioFile.contentType().isBlank()) {
            throw invalidAudio(audioFile.audioFileId());
        }
        if (!audioFile.expiresAt().isAfter(command.requestedAt())) {
            throw new BusinessException(
                    ErrorCode.FIELD_STT_AUDIO_EXPIRED,
                    Map.of("audioFileId", audioFile.audioFileId())
            );
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

    private BusinessException endedParticipant() {
        return new BusinessException(
                ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED
        );
    }

    public record LockedRequestContext(
            Long sessionId,
            String objectKey,
            String contentType
    ) {
    }
}
