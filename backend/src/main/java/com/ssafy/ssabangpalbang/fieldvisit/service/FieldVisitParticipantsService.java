package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitCandidate;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitParticipantsResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitCandidateRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 참여자별 임장 상태 조회(BE-014) 서비스다.
 *
 * <p>세션 최초 시작 시점에 고정한 {@link FieldVisitCandidate} 명단을 기준으로,
 * 실제 GPS 시작 여부에 따라 IN_PROGRESS/ENDED/NOT_JOINED 상태를 파생해 반환한다.</p>
 */
@Service
@RequiredArgsConstructor
public class FieldVisitParticipantsService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final StudyRepository studyRepository;
    private final MemberRepository memberRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final FieldSessionRepository fieldSessionRepository;
    private final FieldVisitCandidateRepository fieldVisitCandidateRepository;
    private final FieldParticipantRepository fieldParticipantRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ParticipantsResult getParticipants(Long studyId, Long memberId) {
        Study study = studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));

        boolean activeAccount = memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .isPresent();
        if (!activeAccount) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_PARTICIPANTS_ACCESS_DENIED);
        }

        boolean viewerIsLeader = StudyAccessPolicy.isLeader(study, memberId);
        boolean activeMember = studyMemberRepository
                .findByStudyIdAndMemberId(studyId, memberId)
                .filter(sm -> sm.getStatus() == StudyMemberStatus.ACTIVE)
                .isPresent();
        if (!viewerIsLeader && !activeMember) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_PARTICIPANTS_ACCESS_DENIED);
        }

        FieldSession session = fieldSessionRepository.findByStudyId(studyId)
                .orElse(null);
        if (session == null) {
            return new ParticipantsResult(
                    FieldVisitResponseCode.FIELD_VISIT_PARTICIPANTS_SUCCESS,
                    new FieldVisitParticipantsResponse(
                            studyId,
                            null,
                            "NOT_STARTED",
                            0,
                            0,
                            0,
                            0,
                            List.of()
                    )
            );
        }

        Instant now = clock.instant();
        boolean sessionInProgress =
                session.getStatus() == FieldSessionStatus.IN_PROGRESS;

        List<FieldVisitCandidate> candidates =
                fieldVisitCandidateRepository.findBySessionId(session.getId());
        Map<Long, FieldParticipant> participantsByMember = fieldParticipantRepository
                .findBySessionId(session.getId())
                .stream()
                .collect(Collectors.toMap(
                        FieldParticipant::getMemberId,
                        Function.identity()
                ));
        List<Long> memberIds = candidates.stream()
                .map(FieldVisitCandidate::getMemberId)
                .toList();
        Map<Long, Member> membersById = memberRepository.findAllById(memberIds)
                .stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        List<FieldVisitParticipantsResponse.ParticipantBody> participants =
                new ArrayList<>();
        int inProgressCount = 0;
        int endedCount = 0;
        int notJoinedCount = 0;
        for (FieldVisitCandidate candidate : candidates) {
            Long candidateMemberId = candidate.getMemberId();
            Member member = membersById.get(candidateMemberId);
            if (member == null) {
                continue;
            }
            FieldParticipant participant = participantsByMember.get(candidateMemberId);
            String status = resolveStatus(participant);
            switch (status) {
                case "IN_PROGRESS" -> inProgressCount++;
                case "ENDED" -> endedCount++;
                default -> notJoinedCount++;
            }
            boolean rowIsLeader = study.getLeaderId().equals(candidateMemberId);
            boolean rowIsMe = candidateMemberId.equals(memberId);
            participants.add(new FieldVisitParticipantsResponse.ParticipantBody(
                    participant == null ? null : participant.getId(),
                    candidateMemberId,
                    member.getNickname(),
                    member.getProfileImageUrl(),
                    member.getSelectedCharacterId(),
                    rowIsLeader ? "LEADER" : "MEMBER",
                    status,
                    participant == null ? null : toSeoul(participant.getStartedAt()),
                    participant == null || participant.getEndedAt() == null
                            ? null
                            : toSeoul(participant.getEndedAt()),
                    resolveEndReason(participant),
                    stayDurationSec(participant, now),
                    rowIsMe,
                    rowIsLeader,
                    canRequestFinish(
                            viewerIsLeader, sessionInProgress, status, rowIsMe
                    )
            ));
        }

        participants.sort(participantComparator());

        return new ParticipantsResult(
                FieldVisitResponseCode.FIELD_VISIT_PARTICIPANTS_SUCCESS,
                new FieldVisitParticipantsResponse(
                        studyId,
                        session.getId(),
                        session.getStatus().name(),
                        participants.size(),
                        inProgressCount,
                        endedCount,
                        notJoinedCount,
                        participants
                )
        );
    }

    private static String resolveStatus(FieldParticipant participant) {
        if (participant == null) {
            return "NOT_JOINED";
        }
        return participant.getStatus().name();
    }

    /**
     * 저장된 종료 사유를 응답 사유로 매핑한다.
     *
     * <p>SELF_ENDED·LEADER_FORCED는 그대로, 그 외 세션 단위 종료
     * (MAJORITY_FORCED 등)는 SESSION_ENDED로 반환한다. 미종료는 null.</p>
     */
    private static String resolveEndReason(FieldParticipant participant) {
        if (participant == null
                || participant.getStatus() != FieldParticipantStatus.ENDED) {
            return null;
        }
        String endReason = participant.getEndReason();
        if ("SELF_ENDED".equals(endReason) || "LEADER_FORCED".equals(endReason)) {
            return endReason;
        }
        return "SESSION_ENDED";
    }

    private static int stayDurationSec(FieldParticipant participant, Instant now) {
        if (participant == null) {
            return 0;
        }
        if (participant.getStatus() == FieldParticipantStatus.ENDED) {
            return participant.getStayDurationSec() == null
                    ? 0
                    : Math.max(0, participant.getStayDurationSec());
        }
        return nonNegativeSeconds(participant.getStartedAt(), now);
    }

    private static boolean canRequestFinish(
            boolean viewerIsLeader,
            boolean sessionInProgress,
            String status,
            boolean rowIsMe
    ) {
        return viewerIsLeader
                && sessionInProgress
                && "IN_PROGRESS".equals(status)
                && !rowIsMe;
    }

    /**
     * 스터디장을 첫 번째, 시작한 참여자를 startedAt 오름차순으로, 아직 시작하지
     * 않은(NOT_JOINED) 참여자를 그 뒤에 둔다. 마지막은 memberId로 안정 정렬한다.
     */
    private static Comparator<FieldVisitParticipantsResponse.ParticipantBody>
            participantComparator() {
        return Comparator
                .comparing(
                        (FieldVisitParticipantsResponse.ParticipantBody p) -> !p.isLeader()
                )
                .thenComparing(p -> p.startedAt() == null)
                .thenComparing(
                        FieldVisitParticipantsResponse.ParticipantBody::startedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(FieldVisitParticipantsResponse.ParticipantBody::memberId);
    }

    private static int nonNegativeSeconds(Instant start, Instant end) {
        long seconds = Duration.between(start, end).getSeconds();
        return (int) Math.max(0L, seconds);
    }

    private static OffsetDateTime toSeoul(Instant instant) {
        return instant.atZone(SEOUL).toOffsetDateTime();
    }

    public record ParticipantsResult(
            FieldVisitResponseCode responseCode,
            FieldVisitParticipantsResponse body
    ) {
    }
}
