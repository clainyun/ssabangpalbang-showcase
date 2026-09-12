package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitStartProperties;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitCandidate;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitStartRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitStartRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitStartResponse;
import com.ssafy.ssabangpalbang.fieldvisit.geo.GeoDistanceCalculator;
import com.ssafy.ssabangpalbang.fieldvisit.integration.FieldVisitSessionStartedEvent;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitCandidateRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitStartRequestRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Schedule;
import com.ssafy.ssabangpalbang.study.domain.ScheduleStatus;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FieldVisitStartService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String PARTICIPANT_ENDED_MESSAGE =
            "이미 종료한 임장은 다시 시작할 수 없습니다.";
    private static final String IMMEDIATE_MEETING_PLACE = "현장";

    private final StudyRepository studyRepository;
    private final MemberRepository memberRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final ScheduleRepository scheduleRepository;
    private final ApartmentRepository apartmentRepository;
    private final FieldSessionRepository fieldSessionRepository;
    private final FieldVisitCandidateRepository fieldVisitCandidateRepository;
    private final FieldParticipantRepository fieldParticipantRepository;
    private final FieldVisitStartRequestRepository startRequestRepository;
    private final ChecklistRepository checklistRepository;
    private final FieldVisitStartProperties startProperties;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public StartResult start(
            Long studyId,
            Long memberId,
            FieldVisitStartRequestBody request
    ) {
        String clientRequestId = normalizeClientRequestId(request.clientRequestId());
        double latitude = request.latitude();
        double longitude = request.longitude();
        String fingerprint = FieldVisitStartFingerprint.of(
                studyId, latitude, longitude
        );

        Study study = studyRepository
                .findForNoKeyUpdateByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));

        FieldSession session = fieldSessionRepository
                .findByStudyIdForUpdate(studyId)
                .orElse(null);
        List<StudyMember> fixedCandidates = null;
        if (session == null) {
            fixedCandidates = lockFirstStartCandidateMembers(studyId, memberId);
        } else {
            requireActiveMember(studyId, memberId);
        }

        FieldVisitStartRequest existingRequest = startRequestRepository
                .findByMemberIdAndClientRequestId(memberId, clientRequestId)
                .orElse(null);
        if (existingRequest != null) {
            return alreadyStartedFromHistory(
                    existingRequest,
                    study,
                    session,
                    fingerprint,
                    latitude,
                    longitude
            );
        }

        if (session != null) {
            requireExistingSessionStartAllowed(study, session);
            FieldParticipant participant = fieldParticipantRepository
                    .findBySessionIdAndMemberIdForUpdate(session.getId(), memberId)
                    .orElse(null);
            if (participant != null) {
                if (participant.getStatus() == FieldParticipantStatus.ENDED) {
                    throw new BusinessException(
                            ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED,
                            PARTICIPANT_ENDED_MESSAGE
                    );
                }
                if (request.isScheduleOverrideConfirmed()) {
                    requireScheduleStartAtReached(study.getId(), true);
                }
                return alreadyStartedExisting(
                        study,
                        session,
                        participant,
                        clientRequestId,
                        fingerprint,
                        latitude,
                        longitude
                );
            }
            requireFixedCandidate(session.getId(), memberId);
            requireScheduleStartAtReached(
                    study.getId(), request.isScheduleOverrideConfirmed()
            );
            int distance = requireWithinRadius(study, latitude, longitude);
            Instant now = clock.instant();
            FieldParticipant created = fieldParticipantRepository.save(
                    FieldParticipant.start(session.getId(), memberId, now)
            );
            saveHistory(
                    memberId, studyId, session.getId(), created.getId(),
                    clientRequestId, fingerprint, now
            );
            return createdResult(study, session, created, distance, true);
        }

        requireFirstStartAllowed(
                study, request.isScheduleOverrideConfirmed()
        );

        int distance = requireWithinRadius(study, latitude, longitude);
        Instant now = clock.instant();
        study.markInProgress();
        FieldSession createdSession = fieldSessionRepository.save(
                FieldSession.start(studyId, now)
        );
        saveCandidateSnapshot(fixedCandidates, createdSession.getId(), now);
        FieldParticipant createdParticipant = fieldParticipantRepository.save(
                FieldParticipant.start(createdSession.getId(), memberId, now)
        );
        saveHistory(
                memberId,
                studyId,
                createdSession.getId(),
                createdParticipant.getId(),
                clientRequestId,
                fingerprint,
                now
        );
        List<Long> notificationRecipientIds = fixedCandidates.stream()
                .map(StudyMember::getMemberId)
                .filter(candidateId -> !candidateId.equals(memberId))
                .toList();
        eventPublisher.publishEvent(new FieldVisitSessionStartedEvent(
                studyId,
                createdSession.getId(),
                memberId,
                study.getTitle(),
                now,
                notificationRecipientIds
        ));
        return createdResult(
                study, createdSession, createdParticipant, distance, false
        );
    }

    private StartResult alreadyStartedFromHistory(
            FieldVisitStartRequest history,
            Study study,
            FieldSession session,
            String fingerprint,
            double latitude,
            double longitude
    ) {
        Long studyId = study.getId();
        boolean studyIdMismatch = !Objects.equals(history.getStudyId(), studyId);
        boolean fingerprintMismatch = !Objects.equals(
                history.getRequestFingerprint(), fingerprint
        );
        if (studyIdMismatch || fingerprintMismatch) {
            logIdempotencyMismatch(
                    history.getMemberId(),
                    studyId,
                    history.getClientRequestId(),
                    studyIdMismatch,
                    fingerprintMismatch
            );
            throw new BusinessException(
                    ErrorCode.FIELD_VISIT_START_IDEMPOTENCY_MISMATCH
            );
        }
        if (session == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (!Objects.equals(history.getSessionId(), session.getId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        requireExistingSessionStartAllowed(study, session);
        FieldParticipant participant = fieldParticipantRepository
                .findBySessionIdAndMemberIdForUpdate(
                        session.getId(), history.getMemberId()
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR
                ));
        if (!Objects.equals(history.getParticipantId(), participant.getId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (participant.getStatus() == FieldParticipantStatus.ENDED) {
            throw new BusinessException(
                    ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED,
                    PARTICIPANT_ENDED_MESSAGE
            );
        }
        int distance = measureDistance(study, latitude, longitude);
        return alreadyResult(study, session, participant, distance);
    }

    private StartResult alreadyStartedExisting(
            Study study,
            FieldSession session,
            FieldParticipant participant,
            String clientRequestId,
            String fingerprint,
            double latitude,
            double longitude
    ) {
        Instant now = clock.instant();
        saveHistory(
                participant.getMemberId(),
                study.getId(),
                session.getId(),
                participant.getId(),
                clientRequestId,
                fingerprint,
                now
        );
        int distance = measureDistance(study, latitude, longitude);
        return alreadyResult(study, session, participant, distance);
    }

    private void saveHistory(
            Long memberId,
            Long studyId,
            Long sessionId,
            Long participantId,
            String clientRequestId,
            String fingerprint,
            Instant createdAt
    ) {
        startRequestRepository.saveAndFlush(FieldVisitStartRequest.create(
                memberId,
                studyId,
                sessionId,
                participantId,
                clientRequestId,
                fingerprint,
                createdAt
        ));
    }

    private void requireActiveMember(Long studyId, Long memberId) {
        Member member = memberRepository.findByIdForNoKeyUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_VISIT_START_FORBIDDEN
                ));
        if (member.getStatus() != MemberStatus.ACTIVE
                || member.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_START_FORBIDDEN);
        }
        boolean active = studyMemberRepository
                .findByStudyIdAndMemberId(studyId, memberId)
                .filter(sm -> sm.getStatus() == StudyMemberStatus.ACTIVE)
                .isPresent();
        if (!active) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_START_FORBIDDEN);
        }
    }

    private List<StudyMember> lockFirstStartCandidateMembers(
            Long studyId,
            Long requesterId
    ) {
        List<Long> candidateMemberIds = studyMemberRepository
                .findByStudyIdAndStatus(studyId, StudyMemberStatus.ACTIVE)
                .stream()
                .map(StudyMember::getMemberId)
                .distinct()
                .sorted()
                .toList();
        if (!candidateMemberIds.contains(requesterId)) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_START_FORBIDDEN);
        }

        Set<Long> activeAccountIds = memberRepository
                .findAllByIdForNoKeyUpdate(candidateMemberIds)
                .stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .map(Member::getId)
                .collect(Collectors.toSet());

        List<StudyMember> fixedCandidates = studyMemberRepository
                .findByStudyIdAndStatus(studyId, StudyMemberStatus.ACTIVE)
                .stream()
                .filter(studyMember -> activeAccountIds.contains(
                        studyMember.getMemberId()
                ))
                .sorted((left, right) -> left.getMemberId().compareTo(
                        right.getMemberId()
                ))
                .toList();
        boolean requesterIsActive = fixedCandidates.stream()
                .anyMatch(candidate -> candidate.getMemberId().equals(requesterId));
        if (!requesterIsActive) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_START_FORBIDDEN);
        }
        return fixedCandidates;
    }

    private void requireExistingSessionStartAllowed(
            Study study,
            FieldSession session
    ) {
        if (session.getStatus() == FieldSessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_ALREADY_ENDED);
        }
        if (study.getStatus() != StudyStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_START_FORBIDDEN);
        }
    }

    private void requireFixedCandidate(Long sessionId, Long memberId) {
        if (!fieldVisitCandidateRepository.existsBySessionIdAndMemberId(
                sessionId, memberId
        )) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_START_FORBIDDEN);
        }
    }

    private void saveCandidateSnapshot(
            List<StudyMember> fixedCandidates,
            Long sessionId,
            Instant createdAt
    ) {
        List<FieldVisitCandidate> candidates = fixedCandidates.stream()
                .map(StudyMember::getMemberId)
                .map(memberId -> FieldVisitCandidate.create(
                        sessionId, memberId, createdAt
                ))
                .toList();
        fieldVisitCandidateRepository.saveAll(candidates);
    }

    private void requireFirstStartAllowed(
            Study study,
            boolean scheduleOverrideConfirmed
    ) {
        if (study.getStatus() != StudyStatus.CLOSED) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_START_FORBIDDEN);
        }
        requireScheduleStartAtReached(
                study.getId(), scheduleOverrideConfirmed
        );
    }

    private void requireScheduleStartAtReached(
            Long studyId,
            boolean scheduleOverrideConfirmed
    ) {
        Instant now = clock.instant();
        Schedule schedule = scheduleRepository
                .findByStudyIdAndStatus(studyId, ScheduleStatus.SCHEDULED)
                .orElse(null);

        if (schedule == null) {
            if (!scheduleOverrideConfirmed) {
                throw new BusinessException(
                        ErrorCode.FIELD_VISIT_START_FORBIDDEN
                );
            }
            schedule = activateScheduleNow(studyId, now);
        } else if (scheduleOverrideConfirmed
                && schedule.getStartAt().isAfter(now)) {
            schedule.update(now, null, null);
            scheduleRepository.flush();
        }

        if (!FieldVisitStartTimePolicy.isStartAtReached(
                now, schedule.getStartAt()
        )) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_START_NOT_AVAILABLE);
        }
    }

    private Schedule activateScheduleNow(Long studyId, Instant now) {
        Schedule schedule = scheduleRepository.findByStudyId(studyId)
                .orElse(null);
        if (schedule == null) {
            Schedule created = Schedule.create(
                    studyId, now, null, IMMEDIATE_MEETING_PLACE
            );
            scheduleRepository.save(created);
            scheduleRepository.flush();
            return created;
        }
        if (schedule.getStatus() == ScheduleStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.FIELD_VISIT_START_FORBIDDEN);
        }

        Instant endAt = schedule.getEndAt();
        if (endAt != null && !endAt.isAfter(now)) {
            endAt = null;
        }
        String meetingPlace = schedule.getMeetingPlace();
        if (meetingPlace == null || meetingPlace.isBlank()) {
            meetingPlace = IMMEDIATE_MEETING_PLACE;
        }
        schedule.reactivate(now, endAt, meetingPlace);
        scheduleRepository.flush();
        return schedule;
    }

    private int requireWithinRadius(
            Study study,
            double latitude,
            double longitude
    ) {
        int distance = measureDistance(study, latitude, longitude);
        int allowed = startProperties.allowedRadiusMeters();
        if (distance > allowed) {
            throw new BusinessException(
                    ErrorCode.FIELD_VISIT_OUT_OF_RANGE,
                    Map.of(
                            "distanceMeters", distance,
                            "allowedRadiusMeters", allowed
                    )
            );
        }
        return distance;
    }

    private String normalizeClientRequestId(String clientRequestId) {
        try {
            return UUID.fromString(clientRequestId.strip()).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field", "clientRequestId",
                            "reason", "clientRequestId는 UUID 형식이어야 합니다."
                    )
            );
        }
    }

    private int measureDistance(
            Study study,
            double latitude,
            double longitude
    ) {
        Apartment apartment = apartmentRepository.findById(study.getApartmentId())
                .orElseThrow(() -> {
                    log.error(
                            "Apartment missing for field visit start. studyId={} apartmentId={}",
                            study.getId(),
                            study.getApartmentId()
                    );
                    return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                });
        if (apartment.getLatitude() == null || apartment.getLongitude() == null) {
            log.error(
                    "Apartment coordinates missing. apartmentId={}",
                    apartment.getId()
            );
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return GeoDistanceCalculator.distanceMeters(
                latitude,
                longitude,
                apartment.getLatitude(),
                apartment.getLongitude()
        );
    }

    private void logIdempotencyMismatch(
            Long memberId,
            Long studyId,
            String clientRequestId,
            boolean studyIdMismatch,
            boolean fingerprintMismatch
    ) {
        String mismatchReasons = studyIdMismatch
                ? (fingerprintMismatch
                        ? "[studyId,requestFingerprint]"
                        : "[studyId]")
                : "[requestFingerprint]";
        log.warn(
                "event=FIELD_VISIT_START_IDEMPOTENCY_MISMATCH memberId={} studyId={} clientRequestId={} mismatchReasons={}",
                memberId,
                studyId,
                clientRequestId,
                mismatchReasons
        );
    }

    private StartResult createdResult(
            Study study,
            FieldSession session,
            FieldParticipant participant,
            int distance,
            boolean checklistMaybeExists
    ) {
        boolean checklistGenerated = checklistMaybeExists
                && checklistRepository
                .findBySessionIdAndMemberId(session.getId(), participant.getMemberId())
                .isPresent();
        return new StartResult(
                HttpStatus.CREATED,
                FieldVisitResponseCode.FIELD_VISIT_START_SUCCESS,
                toResponse(study, session, participant, distance, checklistGenerated)
        );
    }

    private StartResult alreadyResult(
            Study study,
            FieldSession session,
            FieldParticipant participant,
            int distance
    ) {
        boolean checklistGenerated = checklistRepository
                .findBySessionIdAndMemberId(session.getId(), participant.getMemberId())
                .isPresent();
        return new StartResult(
                HttpStatus.OK,
                FieldVisitResponseCode.FIELD_VISIT_ALREADY_STARTED,
                toResponse(study, session, participant, distance, checklistGenerated)
        );
    }

    private FieldVisitStartResponse toResponse(
            Study study,
            FieldSession session,
            FieldParticipant participant,
            int distance,
            boolean checklistGenerated
    ) {
        return new FieldVisitStartResponse(
                study.getId(),
                study.getApartmentId(),
                distance,
                startProperties.allowedRadiusMeters(),
                new FieldVisitStartResponse.SessionBody(
                        session.getId(),
                        session.getStatus().name(),
                        toSeoul(session.getStartedAt())
                ),
                new FieldVisitStartResponse.ParticipantBody(
                        participant.getId(),
                        participant.getStatus().name(),
                        toSeoul(participant.getStartedAt())
                ),
                checklistGenerated
        );
    }

    private static OffsetDateTime toSeoul(Instant instant) {
        return instant.atZone(SEOUL).toOffsetDateTime();
    }

    public record StartResult(
            HttpStatus httpStatus,
            FieldVisitResponseCode responseCode,
            FieldVisitStartResponse body
    ) {
    }
}
