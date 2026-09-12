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
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 임장 체크리스트·현장 기록 API의 스터디·세션·참여자 권한·상태 검증이다.
 *
 * <p>{@link FieldSession}·{@link FieldParticipant}는 조회만 한다. 세션·참여자
 * 생성·GPS·시작·종료는 BE-014 책임이다.</p>
 *
 * <p>{@link #requireChecklistGenerationParticipation}은 POST checklist/generate
 * 전용이다. BE-015 쓰기 API는 {@link #requireWritableParticipation}을 사용한다.</p>
 */
@Component
@RequiredArgsConstructor
public class FieldVisitAccessService {

    /**
     * {@link ErrorCode#FIELD_PARTICIPANT_ALREADY_ENDED}는 BE-016(STT)과 공유한다.
     * code·HTTP 409는 동일하고, 체크리스트 생성 문맥 message만 여기서 오버라이드한다.
     */
    private static final String CHECKLIST_GENERATE_PARTICIPANT_ENDED_MESSAGE =
            "임장을 종료한 뒤에는 체크리스트를 생성할 수 없습니다.";

    public static final String CHECKLIST_ANSWER_PARTICIPANT_ENDED_MESSAGE =
            "임장을 종료한 뒤에는 체크리스트 완료 상태를 변경할 수 없습니다.";

    public static final String FIELD_RECORD_PARTICIPANT_ENDED_MESSAGE =
            "임장을 종료한 뒤에는 현장 기록을 변경할 수 없습니다.";

    private final StudyRepository studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final FieldSessionRepository fieldSessionRepository;
    private final FieldParticipantRepository fieldParticipantRepository;
    private final ReportRepository reportRepository;

    public Study requireStudy(Long studyId) {
        return studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
    }

    public void requireActiveStudyMember(
            Long studyId,
            Long memberId,
            ErrorCode forbiddenCode
    ) {
        boolean isActiveMember = studyMemberRepository
                .findByStudyIdAndMemberId(studyId, memberId)
                .filter(studyMember -> studyMember.getStatus() == StudyMemberStatus.ACTIVE)
                .isPresent();
        if (!isActiveMember) {
            throw new BusinessException(forbiddenCode);
        }
    }

    /**
     * POST checklist/generate 전용 엄격 검증이다.
     *
     * <p>반환된 {@link FieldVisitParticipation}은 세션·참여자가 모두
     * {@code IN_PROGRESS}임을 보장한다.</p>
     */
    public FieldVisitParticipation requireChecklistGenerationParticipation(
            Long studyId,
            Long memberId,
            ErrorCode forbiddenCode
    ) {
        requireStudy(studyId);
        requireActiveStudyMember(studyId, memberId, forbiddenCode);

        FieldSession session = fieldSessionRepository.findByStudyId(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FIELD_VISIT_NOT_STARTED));

        if (session.getStatus() != FieldSessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_ALREADY_ENDED);
        }

        FieldParticipant participant = fieldParticipantRepository
                .findBySessionIdAndMemberId(session.getId(), memberId)
                .orElseThrow(() -> new BusinessException(forbiddenCode));

        if (participant.getStatus() != FieldParticipantStatus.IN_PROGRESS) {
            throw new BusinessException(
                    ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED,
                    CHECKLIST_GENERATE_PARTICIPANT_ENDED_MESSAGE
            );
        }

        return new FieldVisitParticipation(session, participant);
    }

    /**
     * BE-015 쓰기 API(answers/records)용 엄격 검증이다.
     *
     * <p>세션·참여자 IN_PROGRESS에 더해, 해당 스터디에 report 행이 하나라도
     * 있으면(PENDING/IN_PROGRESS/DONE/FAILED 포함) 쓰기를 차단한다.</p>
     */
    public FieldVisitParticipation requireWritableParticipation(
            Long studyId,
            Long memberId,
            ErrorCode forbiddenCode,
            String participantEndedMessage
    ) {
        requireStudy(studyId);
        requireActiveStudyMember(studyId, memberId, forbiddenCode);

        FieldSession session = fieldSessionRepository.findByStudyId(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FIELD_VISIT_NOT_STARTED));

        if (session.getStatus() != FieldSessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_ALREADY_ENDED);
        }

        FieldParticipant participant = fieldParticipantRepository
                .findBySessionIdAndMemberId(session.getId(), memberId)
                .orElseThrow(() -> new BusinessException(forbiddenCode));

        if (participant.getStatus() != FieldParticipantStatus.IN_PROGRESS) {
            throw new BusinessException(
                    ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED,
                    participantEndedMessage
            );
        }

        if (reportRepository.existsByStudyId(studyId)) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_REPORT_LOCKED);
        }

        return new FieldVisitParticipation(session, participant);
    }

    /**
     * GET checklist용 관대한 조회다. 세션·참여 미시작은 예외가 아니라
     * {@link FieldVisitReadStatus}로 담아 반환한다.
     */
    public FieldVisitReadStatus readStatus(
            Long studyId,
            Long memberId,
            ErrorCode forbiddenCode
    ) {
        return readStatus(studyId, memberId, forbiddenCode, false);
    }

    /**
     * 원본 현장 기록 조회용 상태다. 세션이 시작된 뒤에는 현재 참여자 또는
     * 스터디장만 다른 참여자의 원본까지 조회할 수 있다.
     */
    public FieldVisitReadStatus readRecordStatus(
            Long studyId,
            Long memberId,
            ErrorCode forbiddenCode
    ) {
        return readStatus(studyId, memberId, forbiddenCode, true);
    }

    private FieldVisitReadStatus readStatus(
            Long studyId,
            Long memberId,
            ErrorCode forbiddenCode,
            boolean requireParticipantOrLeader
    ) {
        Study study = requireStudy(studyId);
        requireActiveStudyMember(studyId, memberId, forbiddenCode);

        Optional<FieldSession> sessionOpt = fieldSessionRepository.findByStudyId(studyId);
        if (sessionOpt.isEmpty()) {
            return new FieldVisitReadStatus(null, null, false);
        }

        FieldSession session = sessionOpt.get();
        FieldParticipant participant = fieldParticipantRepository
                .findBySessionIdAndMemberId(session.getId(), memberId)
                .orElse(null);
        if (requireParticipantOrLeader
                && participant == null
                && !memberId.equals(study.getLeaderId())) {
            throw new BusinessException(forbiddenCode);
        }

        boolean readOnly = session.getStatus() == FieldSessionStatus.ENDED
                || (participant != null
                        && participant.getStatus() == FieldParticipantStatus.ENDED)
                || reportRepository.existsByStudyId(studyId);

        return new FieldVisitReadStatus(session, participant, readOnly);
    }
}
