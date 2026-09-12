package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.AudioFileView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.ChecklistItemView;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository.FieldSessionView;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttLockedRequestValidatorTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long STUDY_ID = 10L;
    private static final Long SESSION_ID = 100L;
    private static final Long AUDIO_FILE_ID = 90L;
    private static final Long CHECKLIST_ITEM_ID = 501L;
    private static final OffsetDateTime REQUESTED_AT = OffsetDateTime.of(
            2026,
            7,
            25,
            14,
            25,
            0,
            0,
            ZoneOffset.ofHours(9)
    );

    @Mock
    private StudyMemberRepository studyMemberRepository;

    @Mock
    private SttRequestQueryRepository requestQueryRepository;

    @InjectMocks
    private SttLockedRequestValidator validator;

    @Test
    void 관련_행을_잠근_상태로_재검증하고_최신_발행_정보를_반환한다() {
        stubValidLockedContext();

        SttLockedRequestValidator.LockedRequestContext result =
                validator.validate(command());

        assertThat(result.sessionId()).isEqualTo(SESSION_ID);
        assertThat(result.objectKey()).isEqualTo("stt/locked.webm");
        assertThat(result.contentType()).isEqualTo("audio/webm");
        verify(studyMemberRepository).findForUpdateByStudyIdAndMemberId(
                STUDY_ID,
                MEMBER_ID
        );
        verify(requestQueryRepository)
                .findFieldSessionByStudyIdForUpdate(STUDY_ID);
        verify(requestQueryRepository)
                .findParticipantStatusForUpdate(SESSION_ID, MEMBER_ID);
        verify(requestQueryRepository)
                .findChecklistItemForUpdate(CHECKLIST_ITEM_ID);
        verify(requestQueryRepository)
                .findAudioFileForUpdate(AUDIO_FILE_ID);
    }

    @Test
    void 최초_검증_뒤_참여가_종료되면_저장_직전에_거부한다() {
        stubActiveMember();
        when(requestQueryRepository.findFieldSessionByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(new FieldSessionView(
                        SESSION_ID,
                        "IN_PROGRESS"
                )));
        when(requestQueryRepository.findParticipantStatusForUpdate(
                SESSION_ID,
                MEMBER_ID
        )).thenReturn(Optional.of("ENDED"));

        assertThatThrownBy(() -> validator.validate(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED
                        ));
    }

    @Test
    void 최초_검증_뒤_음성이_삭제되면_저장_직전에_거부한다() {
        stubValidLockedContextBeforeAudio();
        when(requestQueryRepository.findAudioFileForUpdate(AUDIO_FILE_ID))
                .thenReturn(Optional.of(new AudioFileView(
                        AUDIO_FILE_ID,
                        MEMBER_ID,
                        STUDY_ID,
                        "STT_AUDIO",
                        "COMPLETED",
                        REQUESTED_AT,
                        REQUESTED_AT.plusHours(1),
                        "stt/locked.webm",
                        "audio/webm"
                )));

        assertThatThrownBy(() -> validator.validate(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ErrorCode.FIELD_STT_AUDIO_INVALID
                        ));
    }

    private void stubValidLockedContext() {
        stubValidLockedContextBeforeAudio();
        when(requestQueryRepository.findAudioFileForUpdate(AUDIO_FILE_ID))
                .thenReturn(Optional.of(new AudioFileView(
                        AUDIO_FILE_ID,
                        MEMBER_ID,
                        STUDY_ID,
                        "STT_AUDIO",
                        "COMPLETED",
                        null,
                        REQUESTED_AT.plusHours(1),
                        "stt/locked.webm",
                        "audio/webm"
                )));
    }

    private void stubValidLockedContextBeforeAudio() {
        stubActiveMember();
        when(requestQueryRepository.findFieldSessionByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(new FieldSessionView(
                        SESSION_ID,
                        "IN_PROGRESS"
                )));
        when(requestQueryRepository.findParticipantStatusForUpdate(
                SESSION_ID,
                MEMBER_ID
        )).thenReturn(Optional.of("IN_PROGRESS"));
        when(requestQueryRepository.findChecklistItemForUpdate(
                CHECKLIST_ITEM_ID
        )).thenReturn(Optional.of(new ChecklistItemView(
                CHECKLIST_ITEM_ID,
                MEMBER_ID,
                SESSION_ID,
                STUDY_ID
        )));
    }

    private void stubActiveMember() {
        StudyMember studyMember = mock(StudyMember.class);
        when(studyMember.getStatus()).thenReturn(StudyMemberStatus.ACTIVE);
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(
                STUDY_ID,
                MEMBER_ID
        )).thenReturn(Optional.of(studyMember));
    }

    private SttCreatePersistenceCommand command() {
        return new SttCreatePersistenceCommand(
                "stt-locked",
                MEMBER_ID,
                STUDY_ID,
                SESSION_ID,
                AUDIO_FILE_ID,
                CHECKLIST_ITEM_ID,
                UUID.randomUUID(),
                REQUESTED_AT,
                "stt/prechecked.webm",
                "audio/webm"
        );
    }
}
