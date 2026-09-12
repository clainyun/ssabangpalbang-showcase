package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitCloseRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitCloseResponse;
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
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FieldVisitCloseService {

    private static final Logger log =
            LoggerFactory.getLogger(FieldVisitCloseService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final FieldVisitAccessService accessService;
    private final FieldSessionRepository fieldSessionRepository;
    private final FieldParticipantRepository fieldParticipantRepository;
    private final FieldVisitSessionCloser sessionCloser;
    private final ReportRepository reportRepository;
    private final Clock clock;

    @Transactional
    public CloseResult close(
            Long studyId,
            Long memberId,
            FieldVisitCloseRequestBody request
    ) {
        Study study = accessService.requireStudy(studyId);
        accessService.requireActiveStudyMember(
                studyId,
                memberId,
                ErrorCode.FIELD_VISIT_CLOSE_FORBIDDEN
        );
        if (!memberId.equals(study.getLeaderId())) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_CLOSE_FORBIDDEN);
        }

        FieldSession session = fieldSessionRepository
                .findByStudyIdForUpdate(studyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_VISIT_NOT_FOUND
                ));
        if (session.getStatus() == FieldSessionStatus.ENDED) {
            return new CloseResult(
                    FieldVisitResponseCode.FIELD_VISIT_ALREADY_CLOSED,
                    toResponse(studyId, session, List.of(), false)
            );
        }

        List<FieldParticipant> inProgressParticipants =
                fieldParticipantRepository.findInProgressBySessionIdForUpdate(
                        session.getId()
                );
        if (!inProgressParticipants.isEmpty()
                && !Boolean.TRUE.equals(request.closeConfirmed())) {
            throw new BusinessException(
                    ErrorCode.FIELD_VISIT_CLOSE_CONFIRMATION_REQUIRED,
                    Map.of(
                            "field",
                            "closeConfirmed",
                            "unfinishedParticipantCount",
                            inProgressParticipants.size()
                    )
            );
        }

        Instant endedAt = clock.instant();
        for (FieldParticipant participant : inProgressParticipants) {
            participant.endByLeader(
                    endedAt,
                    calculateStayDuration(participant.getStartedAt(), endedAt)
            );
        }
        boolean reportTriggered = sessionCloser.endByLeader(
                session,
                study,
                endedAt,
                memberId
        );

        return new CloseResult(
                FieldVisitResponseCode.FIELD_VISIT_CLOSE_SUCCESS,
                toResponse(
                        studyId,
                        session,
                        inProgressParticipants,
                        reportTriggered
                )
        );
    }

    private FieldVisitCloseResponse toResponse(
            Long studyId,
            FieldSession session,
            List<FieldParticipant> forcedEndedParticipants,
            boolean reportTriggered
    ) {
        Report report = reportRepository.findByStudyId(studyId).orElse(null);
        List<FieldVisitCloseResponse.ForcedEndedParticipantBody> participants =
                forcedEndedParticipants.stream()
                        .map(this::toParticipantBody)
                        .toList();
        return new FieldVisitCloseResponse(
                studyId,
                new FieldVisitCloseResponse.SessionBody(
                        session.getId(),
                        session.getStatus().name(),
                        toSeoul(session.getStartedAt()),
                        toSeoul(session.getEndedAt()),
                        session.getEndedById(),
                        session.getEndReason()
                ),
                participants,
                participants.size(),
                reportTriggered,
                report == null ? null : report.getId(),
                report == null ? null : report.getStatus().name()
        );
    }

    private FieldVisitCloseResponse.ForcedEndedParticipantBody toParticipantBody(
            FieldParticipant participant
    ) {
        return new FieldVisitCloseResponse.ForcedEndedParticipantBody(
                participant.getId(),
                participant.getMemberId(),
                participant.getEndReason(),
                toSeoul(participant.getEndedAt()),
                participant.getStayDurationSec()
        );
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

    public record CloseResult(
            FieldVisitResponseCode responseCode,
            FieldVisitCloseResponse body
    ) {
    }
}
