package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitFinishRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitFinishResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class FieldVisitFinishService {

    private static final Logger log =
            LoggerFactory.getLogger(FieldVisitFinishService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final FieldVisitAccessService accessService;
    private final FieldSessionRepository fieldSessionRepository;
    private final FieldParticipantRepository fieldParticipantRepository;
    private final ChecklistProgressCounter checklistProgressCounter;
    private final FieldVisitSessionCloser sessionCloser;
    private final ReportRepository reportRepository;
    private final Clock clock;

    @Transactional
    public FinishResult finish(
            Long studyId,
            Long memberId,
            FieldVisitFinishRequestBody request
    ) {
        if (!Boolean.TRUE.equals(request.finishConfirmed())) {
            throw new BusinessException(
                    ErrorCode.FIELD_VISIT_FINISH_CONFIRMATION_REQUIRED
            );
        }

        Study study = accessService.requireStudy(studyId);
        accessService.requireActiveStudyMember(
                studyId,
                memberId,
                ErrorCode.FIELD_VISIT_FINISH_FORBIDDEN
        );

        FieldSession session = fieldSessionRepository
                .findByStudyIdForUpdate(studyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_VISIT_NOT_FOUND
                ));
        FieldParticipant participant = fieldParticipantRepository
                .findBySessionIdAndMemberIdForUpdate(session.getId(), memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_VISIT_FINISH_FORBIDDEN
                ));

        if (participant.getStatus() == FieldParticipantStatus.ENDED) {
            return alreadyEnded(studyId, session, participant);
        }
        if (session.getStatus() == FieldSessionStatus.ENDED) {
            alignParticipantWithEndedSession(session, participant);
            return alreadyEnded(studyId, session, participant);
        }

        Instant endedAt = clock.instant();
        participant.endBySelf(
                endedAt,
                calculateStayDuration(participant.getStartedAt(), endedAt)
        );

        long remaining = fieldParticipantRepository.countBySessionIdAndStatus(
                session.getId(),
                FieldParticipantStatus.IN_PROGRESS
        );
        boolean sessionEnded = remaining == 0
                && sessionCloser.endByAllParticipants(session, study, endedAt);

        FieldVisitResponseCode responseCode = sessionEnded
                ? FieldVisitResponseCode.FIELD_VISIT_FINISH_AND_SESSION_END_SUCCESS
                : FieldVisitResponseCode.FIELD_VISIT_FINISH_SUCCESS;
        return new FinishResult(
                responseCode,
                toResponse(
                        studyId,
                        session,
                        participant,
                        sessionEnded,
                        sessionEnded,
                        findReportId(studyId)
                )
        );
    }

    private FinishResult alreadyEnded(
            Long studyId,
            FieldSession session,
            FieldParticipant participant
    ) {
        return new FinishResult(
                FieldVisitResponseCode.FIELD_PARTICIPANT_ALREADY_ENDED,
                toResponse(
                        studyId,
                        session,
                        participant,
                        false,
                        false,
                        findReportId(studyId)
                )
        );
    }

    private void alignParticipantWithEndedSession(
            FieldSession session,
            FieldParticipant participant
    ) {
        Instant endedAt = session.getEndedAt() == null
                ? clock.instant()
                : session.getEndedAt();
        int stayDurationSec =
                calculateStayDuration(participant.getStartedAt(), endedAt);
        // 정상 close 경로는 모든 참여자를 먼저 종료하므로 이 조합은 데이터 불일치 방어용이다.
        // 본인이 종료한 것이 아니므로 세션 종료 사유와 무관하게 강제 종료로 정합성을 맞춘다.
        participant.endByLeader(endedAt, stayDurationSec);
    }

    private FieldVisitFinishResponse toResponse(
            Long studyId,
            FieldSession session,
            FieldParticipant participant,
            boolean sessionEnded,
            boolean reportTriggered,
            Long reportId
    ) {
        ChecklistProgressCounter.Progress progress =
                checklistProgressCounter.count(
                        session.getId(),
                        participant.getMemberId()
                );
        return new FieldVisitFinishResponse(
                studyId,
                session.getId(),
                new FieldVisitFinishResponse.ParticipantBody(
                        participant.getId(),
                        participant.getStatus().name(),
                        toSeoul(participant.getStartedAt()),
                        toSeoul(participant.getEndedAt()),
                        participant.getEndReason(),
                        participant.getStayDurationSec()
                ),
                new FieldVisitFinishResponse.ChecklistBody(
                        progress.completedCount(),
                        progress.totalCount(),
                        progress.incompleteCount()
                ),
                sessionEnded,
                sessionEnded ? session.getEndReason() : null,
                reportTriggered,
                reportId
        );
    }

    private Long findReportId(Long studyId) {
        return reportRepository.findByStudyId(studyId)
                .map(Report::getId)
                .orElse(null);
    }

    private int calculateStayDuration(Instant startedAt, Instant endedAt) {
        long seconds = Duration.between(startedAt, endedAt).getSeconds();
        if (seconds < 0) {
            log.warn(
                    "임장 체류 시간이 음수여서 0으로 보정합니다. startedAt={}, endedAt={}",
                    startedAt,
                    endedAt
            );
            return 0;
        }
        if (seconds > Integer.MAX_VALUE) {
            log.warn(
                    "임장 체류 시간이 int 범위를 초과해 최댓값으로 보정합니다. "
                            + "startedAt={}, endedAt={}, seconds={}",
                    startedAt,
                    endedAt,
                    seconds
            );
            return Integer.MAX_VALUE;
        }
        return (int) seconds;
    }

    private static OffsetDateTime toSeoul(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }

    public record FinishResult(
            FieldVisitResponseCode responseCode,
            FieldVisitFinishResponse body
    ) {
    }
}
