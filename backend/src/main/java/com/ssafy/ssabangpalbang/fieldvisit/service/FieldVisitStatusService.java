package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistAnswer;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistAnswerRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitCandidateRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Schedule;
import com.ssafy.ssabangpalbang.study.domain.ScheduleStatus;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import com.ssafy.ssabangpalbang.study.service.StudyAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FieldVisitStatusService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final StudyRepository studyRepository;
    private final MemberRepository memberRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final ScheduleRepository scheduleRepository;
    private final FieldSessionRepository fieldSessionRepository;
    private final FieldVisitCandidateRepository fieldVisitCandidateRepository;
    private final FieldParticipantRepository fieldParticipantRepository;
    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistAnswerRepository checklistAnswerRepository;
    private final FieldRecordCountRepository fieldRecordCountRepository;
    private final ReportRepository reportRepository;
    private final FieldVisitCloseVoteService closeVoteService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public StatusResult getStatus(Long studyId, Long memberId) {
        Study study = studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));

        boolean activeAccount = memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .isPresent();
        if (!activeAccount) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_ACCESS_DENIED);
        }

        boolean activeMember = studyMemberRepository
                .findByStudyIdAndMemberId(studyId, memberId)
                .filter(sm -> sm.getStatus() == StudyMemberStatus.ACTIVE)
                .isPresent();
        if (!activeMember) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_ACCESS_DENIED);
        }

        Instant now = clock.instant();
        FieldSession session = fieldSessionRepository.findByStudyId(studyId)
                .orElse(null);
        FieldParticipant participant = null;
        if (session != null) {
            participant = fieldParticipantRepository
                    .findBySessionIdAndMemberId(session.getId(), memberId)
                    .orElse(null);
        }

        String status = resolveStatus(session);
        boolean reportExists = reportRepository.existsByStudyId(studyId);
        Schedule schedule = scheduleRepository
                .findByStudyIdAndStatus(studyId, ScheduleStatus.SCHEDULED)
                .orElse(null);
        boolean hasSchedule = schedule != null;
        Instant scheduleStartAt = schedule == null ? null : schedule.getStartAt();
        boolean isLeader = StudyAccessPolicy.isLeader(study, memberId);
        boolean fixedCandidate = session != null
                && fieldVisitCandidateRepository.existsBySessionIdAndMemberId(
                session.getId(), memberId
        );

        FieldVisitStatusResponse.ChecklistProgressBody progress =
                buildChecklistProgress(session, participant, memberId);
        FieldVisitStatusResponse.PermissionsBody permissions = buildPermissions(
                study,
                session,
                participant,
                hasSchedule,
                scheduleStartAt,
                now,
                fixedCandidate,
                isLeader,
                reportExists
        );

        FieldVisitStatusResponse body = new FieldVisitStatusResponse(
                studyId,
                status,
                toSessionBody(session, now),
                toParticipantBody(participant, now),
                progress,
                permissions,
                closeVoteService.buildStatus(session, participant)
        );
        return new StatusResult(
                FieldVisitResponseCode.FIELD_VISIT_STATUS_SUCCESS,
                body
        );
    }

    private static String resolveStatus(FieldSession session) {
        if (session == null) {
            return "NOT_STARTED";
        }
        return session.getStatus().name();
    }

    private FieldVisitStatusResponse.SessionBody toSessionBody(
            FieldSession session,
            Instant now
    ) {
        if (session == null) {
            return null;
        }
        Instant end = session.getEndedAt() != null ? session.getEndedAt() : now;
        int elapsed = nonNegativeSeconds(session.getStartedAt(), end);
        // 지도에서 "참여 중 N명 / 종료 M명"을 준실시간으로 보여 주려고 상태별 참여자
        // 수를 함께 내려 줍니다. 리포지토리 카운트 쿼리를 그대로 재사용합니다.
        int activeCount = (int) fieldParticipantRepository
                .countBySessionIdAndStatus(session.getId(), FieldParticipantStatus.IN_PROGRESS);
        int endedCount = (int) fieldParticipantRepository
                .countBySessionIdAndStatus(session.getId(), FieldParticipantStatus.ENDED);
        return new FieldVisitStatusResponse.SessionBody(
                session.getId(),
                toSeoul(session.getStartedAt()),
                session.getEndedAt() == null ? null : toSeoul(session.getEndedAt()),
                session.getEndedById(),
                session.getEndReason(),
                elapsed,
                activeCount,
                endedCount
        );
    }

    private FieldVisitStatusResponse.ParticipantBody toParticipantBody(
            FieldParticipant participant,
            Instant now
    ) {
        if (participant == null) {
            return null;
        }
        Integer stayDuration;
        if (participant.getStatus() == FieldParticipantStatus.ENDED) {
            stayDuration = participant.getStayDurationSec() == null
                    ? 0
                    : Math.max(0, participant.getStayDurationSec());
        } else {
            stayDuration = nonNegativeSeconds(participant.getStartedAt(), now);
        }
        return new FieldVisitStatusResponse.ParticipantBody(
                participant.getId(),
                participant.getStatus().name(),
                toSeoul(participant.getStartedAt()),
                participant.getEndedAt() == null
                        ? null
                        : toSeoul(participant.getEndedAt()),
                participant.getEndReason(),
                stayDuration
        );
    }

    private FieldVisitStatusResponse.ChecklistProgressBody buildChecklistProgress(
            FieldSession session,
            FieldParticipant participant,
            Long memberId
    ) {
        if (session == null || participant == null) {
            return emptyProgress();
        }
        Checklist checklist = checklistRepository
                .findBySessionIdAndMemberId(session.getId(), memberId)
                .orElse(null);
        if (checklist == null) {
            return emptyProgress();
        }
        List<ChecklistItem> items = checklistItemRepository
                .findByChecklistIdOrderByDisplayOrderAsc(checklist.getId());
        List<Long> itemIds = items.stream().map(ChecklistItem::getId).toList();
        Map<Long, ChecklistAnswer> answers = checklistAnswerRepository
                .findByChecklistItemIdIn(itemIds)
                .stream()
                .collect(Collectors.toMap(
                        ChecklistAnswer::getChecklistItemId,
                        Function.identity()
                ));
        int completed = (int) items.stream()
                .filter(item -> {
                    ChecklistAnswer answer = answers.get(item.getId());
                    return answer != null && answer.isCompleted();
                })
                .count();
        Map<Long, Integer> recordCounts = fieldRecordCountRepository
                .countByAuthorAndItemIds(memberId, itemIds);
        int recordCount = recordCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        return new FieldVisitStatusResponse.ChecklistProgressBody(
                true,
                checklist.getId(),
                completed,
                items.size(),
                recordCount
        );
    }

    private static FieldVisitStatusResponse.ChecklistProgressBody emptyProgress() {
        return new FieldVisitStatusResponse.ChecklistProgressBody(
                false, null, 0, 0, 0
        );
    }

    private FieldVisitStatusResponse.PermissionsBody buildPermissions(
            Study study,
            FieldSession session,
            FieldParticipant participant,
            boolean hasSchedule,
            Instant scheduleStartAt,
            Instant now,
            boolean fixedCandidate,
            boolean isLeader,
            boolean reportExists
    ) {
        boolean canStart = canStart(
                study,
                session,
                participant,
                hasSchedule,
                scheduleStartAt,
                now,
                fixedCandidate
        );
        boolean studyInProgress = study.getStatus() == StudyStatus.IN_PROGRESS;
        boolean writable = studyInProgress
                && session != null
                && session.getStatus() == FieldSessionStatus.IN_PROGRESS
                && participant != null
                && participant.getStatus() == FieldParticipantStatus.IN_PROGRESS
                && !reportExists;
        boolean canFinish = studyInProgress
                && session != null
                && session.getStatus() == FieldSessionStatus.IN_PROGRESS
                && participant != null
                && participant.getStatus() == FieldParticipantStatus.IN_PROGRESS;
        boolean canClose = studyInProgress
                && isLeader
                && session != null
                && session.getStatus() == FieldSessionStatus.IN_PROGRESS;
        return new FieldVisitStatusResponse.PermissionsBody(
                canStart,
                writable,
                writable,
                canFinish,
                canClose
        );
    }

    private boolean canStart(
            Study study,
            FieldSession session,
            FieldParticipant participant,
            boolean hasSchedule,
            Instant scheduleStartAt,
            Instant now,
            boolean fixedCandidate
    ) {
        if (participant != null) {
            return false;
        }
        if (study.getStatus() == StudyStatus.CANCELED
                || study.getStatus() == StudyStatus.COMPLETED) {
            return false;
        }
        if (!hasSchedule
                || scheduleStartAt == null
                || !FieldVisitStartTimePolicy.isStartAtReached(now, scheduleStartAt)) {
            return false;
        }
        if (session == null) {
            return study.getStatus() == StudyStatus.CLOSED;
        }
        return study.getStatus() == StudyStatus.IN_PROGRESS
                && session.getStatus() == FieldSessionStatus.IN_PROGRESS
                && fixedCandidate;
    }

    private static int nonNegativeSeconds(Instant start, Instant end) {
        long seconds = Duration.between(start, end).getSeconds();
        return (int) Math.max(0L, seconds);
    }

    private static OffsetDateTime toSeoul(Instant instant) {
        return instant.atZone(SEOUL).toOffsetDateTime();
    }

    public record StatusResult(
            FieldVisitResponseCode responseCode,
            FieldVisitStatusResponse body
    ) {
    }
}
