package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FieldVisitAccessServiceTest {

    private static final Long STUDY_ID = 7L;
    private static final Long MEMBER_ID = 42L;
    private static final Long SESSION_ID = 100L;
    private static final ErrorCode FORBIDDEN_CODE = ErrorCode.CHECKLIST_GENERATE_FORBIDDEN;

    private StudyRepository studyRepository;
    private StudyMemberRepository studyMemberRepository;
    private FieldSessionRepository fieldSessionRepository;
    private FieldParticipantRepository fieldParticipantRepository;
    private ReportRepository reportRepository;
    private FieldVisitAccessService accessService;
    private Study study;

    @BeforeEach
    void setUp() {
        studyRepository = mock(StudyRepository.class);
        studyMemberRepository = mock(StudyMemberRepository.class);
        fieldSessionRepository = mock(FieldSessionRepository.class);
        fieldParticipantRepository = mock(FieldParticipantRepository.class);
        reportRepository = mock(ReportRepository.class);
        accessService = new FieldVisitAccessService(
                studyRepository,
                studyMemberRepository,
                fieldSessionRepository,
                fieldParticipantRepository,
                reportRepository
        );

        study = mock(Study.class);
        when(study.getLeaderId()).thenReturn(999L);
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        StudyMember activeMember = mock(StudyMember.class);
        when(activeMember.getStatus()).thenReturn(StudyMemberStatus.ACTIVE);
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(activeMember));

        when(reportRepository.existsByStudyId(STUDY_ID)).thenReturn(false);
    }

    @Test
    void 스터디가_없으면_STUDY_NOT_FOUND() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.requireStudy(STUDY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_NOT_FOUND);
    }

    @Test
    void ACTIVE_멤버가_아니면_지정한_forbiddenCode를_던진다() {
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.requireActiveStudyMember(
                STUDY_ID, MEMBER_ID, ErrorCode.CHECKLIST_ACCESS_DENIED
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHECKLIST_ACCESS_DENIED);
    }

    @Test
    void 세션이_없으면_FIELD_VISIT_NOT_STARTED() {
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.requireChecklistGenerationParticipation(
                STUDY_ID, MEMBER_ID, FORBIDDEN_CODE
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_NOT_STARTED);
    }

    @Test
    void 세션이_ENDED이면_참여자가_진행중이어도_FIELD_VISIT_ALREADY_ENDED() {
        FieldSession session = mock(FieldSession.class);
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.getStatus()).thenReturn(FieldSessionStatus.ENDED);
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));

        FieldParticipant participant = mock(FieldParticipant.class);
        when(participant.getStatus()).thenReturn(FieldParticipantStatus.IN_PROGRESS);
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> accessService.requireChecklistGenerationParticipation(
                STUDY_ID, MEMBER_ID, FORBIDDEN_CODE
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_ALREADY_ENDED);
    }

    @Test
    void 참여자가_없으면_forbiddenCode를_던진다() {
        FieldSession session = inProgressSession();
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.requireChecklistGenerationParticipation(
                STUDY_ID, MEMBER_ID, FORBIDDEN_CODE
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FORBIDDEN_CODE);
    }

    @Test
    void 참여자가_ENDED이면_체크리스트_전용_message로_FIELD_PARTICIPANT_ALREADY_ENDED() {
        FieldSession session = inProgressSession();
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));

        FieldParticipant participant = mock(FieldParticipant.class);
        when(participant.getStatus()).thenReturn(FieldParticipantStatus.ENDED);
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(participant));

        BusinessException exception = catchThrowableOfType(
                () -> accessService.requireChecklistGenerationParticipation(
                        STUDY_ID, MEMBER_ID, FORBIDDEN_CODE
                ),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED);
        assertThat(exception.getMessage())
                .isEqualTo("임장을 종료한 뒤에는 체크리스트를 생성할 수 없습니다.");
    }

    @Test
    void 생성_참여_검증이_성공하면_세션과_참여자를_반환한다() {
        FieldSession session = inProgressSession();
        FieldParticipant participant = mock(FieldParticipant.class);
        when(participant.getStatus()).thenReturn(FieldParticipantStatus.IN_PROGRESS);
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(participant));

        FieldVisitParticipation result = accessService.requireChecklistGenerationParticipation(
                STUDY_ID, MEMBER_ID, FORBIDDEN_CODE
        );

        assertThat(result.session()).isSameAs(session);
        assertThat(result.participant()).isSameAs(participant);
    }

    @Test
    void 쓰기_검증에서_report가_있으면_FIELD_VISIT_REPORT_LOCKED() {
        FieldSession session = inProgressSession();
        FieldParticipant participant = mock(FieldParticipant.class);
        when(participant.getStatus()).thenReturn(FieldParticipantStatus.IN_PROGRESS);
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(participant));
        when(reportRepository.existsByStudyId(STUDY_ID)).thenReturn(true);

        assertThatThrownBy(() -> accessService.requireWritableParticipation(
                STUDY_ID,
                MEMBER_ID,
                ErrorCode.CHECKLIST_COMPLETION_FORBIDDEN,
                FieldVisitAccessService.CHECKLIST_ANSWER_PARTICIPANT_ENDED_MESSAGE
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_REPORT_LOCKED);
    }

    @Test
    void 쓰기_검증에서_참여자_종료_시_전달한_message를_사용한다() {
        FieldSession session = inProgressSession();
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        FieldParticipant participant = mock(FieldParticipant.class);
        when(participant.getStatus()).thenReturn(FieldParticipantStatus.ENDED);
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(participant));

        BusinessException exception = catchThrowableOfType(
                () -> accessService.requireWritableParticipation(
                        STUDY_ID,
                        MEMBER_ID,
                        ErrorCode.FIELD_RECORD_CREATE_FORBIDDEN,
                        FieldVisitAccessService.FIELD_RECORD_PARTICIPANT_ENDED_MESSAGE
                ),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED);
        assertThat(exception.getMessage())
                .isEqualTo(FieldVisitAccessService.FIELD_RECORD_PARTICIPANT_ENDED_MESSAGE);
    }

    @Test
    void 쓰기_검증이_성공하면_세션과_참여자를_반환한다() {
        FieldSession session = inProgressSession();
        FieldParticipant participant = mock(FieldParticipant.class);
        when(participant.getStatus()).thenReturn(FieldParticipantStatus.IN_PROGRESS);
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(participant));

        FieldVisitParticipation result = accessService.requireWritableParticipation(
                STUDY_ID,
                MEMBER_ID,
                ErrorCode.CHECKLIST_COMPLETION_FORBIDDEN,
                FieldVisitAccessService.CHECKLIST_ANSWER_PARTICIPANT_ENDED_MESSAGE
        );

        assertThat(result.session()).isSameAs(session);
        assertThat(result.participant()).isSameAs(participant);
    }

    @Test
    void readStatus에서_report가_있으면_readOnly_true() {
        FieldSession session = inProgressSession();
        FieldParticipant participant = mock(FieldParticipant.class);
        when(participant.getStatus()).thenReturn(FieldParticipantStatus.IN_PROGRESS);
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(participant));
        when(reportRepository.existsByStudyId(STUDY_ID)).thenReturn(true);

        FieldVisitReadStatus status = accessService.readStatus(
                STUDY_ID, MEMBER_ID, ErrorCode.CHECKLIST_ACCESS_DENIED
        );

        assertThat(status.readOnly()).isTrue();
    }

    @Test
    void 임장_비참여_일반_멤버는_원본_조회가_거부된다() {
        FieldSession session = inProgressSession();
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.readRecordStatus(
                STUDY_ID, MEMBER_ID, ErrorCode.FIELD_RECORD_ACCESS_DENIED
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_ACCESS_DENIED);
    }

    @Test
    void 스터디장은_임장_참여자가_아니어도_원본을_조회할_수_있다() {
        FieldSession session = inProgressSession();
        when(study.getLeaderId()).thenReturn(MEMBER_ID);
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        FieldVisitReadStatus status = accessService.readRecordStatus(
                STUDY_ID, MEMBER_ID, ErrorCode.FIELD_RECORD_ACCESS_DENIED
        );

        assertThat(status.session()).isSameAs(session);
        assertThat(status.participant()).isNull();
        assertThat(status.readOnly()).isFalse();
    }

    @Test
    void 임장_비참여_일반_멤버의_체크리스트_관대한_조회는_유지한다() {
        FieldSession session = inProgressSession();
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        FieldVisitReadStatus status = accessService.readStatus(
                STUDY_ID, MEMBER_ID, ErrorCode.CHECKLIST_ACCESS_DENIED
        );

        assertThat(status.session()).isSameAs(session);
        assertThat(status.participant()).isNull();
        assertThat(status.readOnly()).isFalse();
    }

    private FieldSession inProgressSession() {
        FieldSession session = mock(FieldSession.class);
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.getStatus()).thenReturn(FieldSessionStatus.IN_PROGRESS);
        return session;
    }
}
