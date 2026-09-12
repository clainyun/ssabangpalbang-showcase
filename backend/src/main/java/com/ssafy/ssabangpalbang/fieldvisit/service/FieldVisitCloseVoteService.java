package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitCloseVote;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitCloseVoteResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitCloseVoteStatusBody;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitCloseVoteRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FieldVisitCloseVoteService {

    private final FieldVisitAccessService accessService;
    private final FieldSessionRepository fieldSessionRepository;
    private final FieldParticipantRepository fieldParticipantRepository;
    private final FieldVisitCloseVoteRepository closeVoteRepository;
    private final FieldVisitSessionCloser sessionCloser;
    private final ReportRepository reportRepository;
    private final Clock clock;

    @Transactional
    public VoteResult vote(Long studyId, Long memberId) {
        Study study = accessService.requireStudy(studyId);
        accessService.requireActiveStudyMember(
                studyId,
                memberId,
                ErrorCode.FIELD_VISIT_CLOSE_VOTE_FORBIDDEN
        );

        FieldSession session = fieldSessionRepository
                .findByStudyIdForUpdate(studyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_VISIT_NOT_FOUND
                ));

        if (session.getStatus() == FieldSessionStatus.ENDED) {
            return alreadyEndedResult(studyId, session, memberId);
        }

        FieldParticipant participant = fieldParticipantRepository
                .findBySessionIdAndMemberIdForUpdate(session.getId(), memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_VISIT_CLOSE_VOTE_FORBIDDEN
                ));

        boolean alreadyVoted = closeVoteRepository
                .existsBySessionIdAndParticipantId(
                        session.getId(), participant.getId()
                );
        if (!alreadyVoted) {
            // session 비관적 락 아래에서 직렬화되므로 UNIQUE 위반은 숨기지 않는다.
            closeVoteRepository.saveAndFlush(FieldVisitCloseVote.create(
                    session.getId(),
                    participant.getId(),
                    clock.instant()
            ));
        }

        long startedCount = fieldParticipantRepository.countBySessionId(
                session.getId()
        );
        long voteCount = closeVoteRepository.countBySessionId(session.getId());
        int required = requiredVotes(startedCount);

        boolean sessionEnded = false;
        boolean reportTriggered = false;
        if (voteCount >= required && required > 0) {
            Instant endedAt = clock.instant();
            List<FieldParticipant> inProgress =
                    fieldParticipantRepository.findInProgressBySessionIdForUpdate(
                            session.getId()
                    );
            for (FieldParticipant open : inProgress) {
                open.endByMajority(
                        endedAt,
                        calculateStayDuration(open.getStartedAt(), endedAt)
                );
            }
            reportTriggered = sessionCloser.endByMajority(
                    session, study, endedAt
            );
            sessionEnded = session.getStatus() == FieldSessionStatus.ENDED;
        }

        FieldVisitResponseCode code = sessionEnded
                ? FieldVisitResponseCode.FIELD_VISIT_CLOSE_VOTE_AND_SESSION_END_SUCCESS
                : FieldVisitResponseCode.FIELD_VISIT_CLOSE_VOTE_SUCCESS;

        return new VoteResult(
                code,
                toResponse(
                        studyId,
                        session,
                        startedCount,
                        voteCount,
                        required,
                        true,
                        false,
                        sessionEnded,
                        reportTriggered
                )
        );
    }

    @Transactional(readOnly = true)
    public FieldVisitCloseVoteStatusBody buildStatus(
            FieldSession session,
            FieldParticipant participant
    ) {
        if (session == null) {
            return new FieldVisitCloseVoteStatusBody(0, 0, 0, false, false);
        }
        long startedCount = fieldParticipantRepository.countBySessionId(
                session.getId()
        );
        long voteCount = closeVoteRepository.countBySessionId(session.getId());
        int required = requiredVotes(startedCount);
        boolean hasVoted = participant != null
                && closeVoteRepository.existsBySessionIdAndParticipantId(
                session.getId(), participant.getId()
        );
        boolean canVote = session.getStatus() == FieldSessionStatus.IN_PROGRESS
                && participant != null
                && !hasVoted;
        return new FieldVisitCloseVoteStatusBody(
                (int) startedCount,
                (int) voteCount,
                required,
                hasVoted,
                canVote
        );
    }

    static int requiredVotes(long startedParticipantCount) {
        if (startedParticipantCount <= 0) {
            return 0;
        }
        return (int) (startedParticipantCount / 2) + 1;
    }

    private VoteResult alreadyEndedResult(
            Long studyId,
            FieldSession session,
            Long memberId
    ) {
        FieldParticipant participant = fieldParticipantRepository
                .findBySessionIdAndMemberId(session.getId(), memberId)
                .orElse(null);
        long startedCount = fieldParticipantRepository.countBySessionId(
                session.getId()
        );
        long voteCount = closeVoteRepository.countBySessionId(session.getId());
        int required = requiredVotes(startedCount);
        boolean hasVoted = participant != null
                && closeVoteRepository.existsBySessionIdAndParticipantId(
                session.getId(), participant.getId()
        );
        return new VoteResult(
                FieldVisitResponseCode.FIELD_VISIT_ALREADY_CLOSED,
                toResponse(
                        studyId,
                        session,
                        startedCount,
                        voteCount,
                        required,
                        hasVoted,
                        false,
                        true,
                        false
                )
        );
    }

    private FieldVisitCloseVoteResponse toResponse(
            Long studyId,
            FieldSession session,
            long startedCount,
            long voteCount,
            int required,
            boolean hasVoted,
            boolean canVote,
            boolean sessionEnded,
            boolean reportTriggered
    ) {
        Report report = reportRepository.findByStudyId(studyId).orElse(null);
        return new FieldVisitCloseVoteResponse(
                studyId,
                session.getId(),
                (int) startedCount,
                (int) voteCount,
                required,
                hasVoted,
                canVote,
                sessionEnded,
                session.getEndReason(),
                reportTriggered,
                report == null ? null : report.getId(),
                report == null ? null : report.getStatus().name()
        );
    }

    private int calculateStayDuration(Instant startedAt, Instant endedAt) {
        long seconds = Duration.between(startedAt, endedAt).getSeconds();
        if (seconds < 0) {
            return 0;
        }
        if (seconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) seconds;
    }

    public record VoteResult(
            FieldVisitResponseCode responseCode,
            FieldVisitCloseVoteResponse body
    ) {
    }
}
