package com.ssafy.ssabangpalbang.study.service;

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
import com.ssafy.ssabangpalbang.study.dto.request.StudyScheduleCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyScheduleUpdateRequest;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleCreateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleDeleteResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleDetailResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleUpdateResponse;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import com.ssafy.ssabangpalbang.study.service.port.StudyNotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyScheduleService {

    private final StudyRepository studyRepository;
    private final MemberRepository memberRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final ScheduleRepository scheduleRepository;
    private final StudyNotificationPort notificationPort;

    @Transactional
    public StudyScheduleCreateResponse createSchedule(
            Long memberId,
            Long studyId,
            StudyScheduleCreateRequest request
    ) {
        Study study = studyForUpdate(studyId);
        active(memberId);
        leader(study, memberId, ErrorCode.STUDY_SCHEDULE_CREATE_FORBIDDEN);
        writable(study, ErrorCode.STUDY_SCHEDULE_CREATE_NOT_ALLOWED);
        valid(request.startAt(), request.endAt());

        Schedule schedule = scheduleRepository.findByStudyId(studyId)
                .map(existing -> {
                    if (existing.getStatus() != ScheduleStatus.CANCELED) {
                        throw new BusinessException(ErrorCode.STUDY_SCHEDULE_ALREADY_EXISTS);
                    }
                    existing.reactivate(
                            request.startAt(),
                            request.endAt(),
                            request.meetingPlace().strip()
                    );
                    return existing;
                })
                .orElseGet(() -> scheduleRepository.save(Schedule.create(
                        studyId,
                        request.startAt(),
                        request.endAt(),
                        request.meetingPlace().strip()
                )));

        try {
            scheduleRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.STUDY_SCHEDULE_ALREADY_EXISTS);
        }
        notifyMembers(study, schedule, memberId, notificationPort::notifyScheduleCreated);
        return StudyScheduleCreateResponse.from(schedule);
    }

    @Transactional
    public StudyScheduleUpdateResponse updateSchedule(
            Long memberId,
            Long studyId,
            StudyScheduleUpdateRequest request
    ) {
        Study study = studyForUpdate(studyId);
        active(memberId);
        leader(study, memberId, ErrorCode.STUDY_SCHEDULE_UPDATE_FORBIDDEN);
        writable(study, ErrorCode.STUDY_SCHEDULE_UPDATE_NOT_ALLOWED);
        if (request.startAt() == null
                && request.endAt() == null
                && request.meetingPlace() == null) {
            throw new BusinessException(ErrorCode.STUDY_SCHEDULE_UPDATE_EMPTY);
        }
        if (request.meetingPlace() != null && request.meetingPlace().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Schedule schedule = scheduleRepository.findByStudyId(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_SCHEDULE_NOT_FOUND));
        if (schedule.getStatus() == ScheduleStatus.CANCELED) {
            throw new BusinessException(ErrorCode.STUDY_SCHEDULE_NOT_FOUND);
        }
        if (schedule.getStatus() == ScheduleStatus.COMPLETED) {
            throw new BusinessException(
                    ErrorCode.STUDY_SCHEDULE_UPDATE_NOT_ALLOWED,
                    Map.of("scheduleStatus", "COMPLETED")
            );
        }

        boolean notificationChanged = request.startAt() != null
                && !request.startAt().equals(schedule.getStartAt())
                || request.meetingPlace() != null
                && !request.meetingPlace().strip().equals(schedule.getMeetingPlace());
        Instant finalStartAt = request.startAt() == null
                ? schedule.getStartAt()
                : request.startAt();
        Instant finalEndAt = request.endAt() == null
                ? schedule.getEndAt()
                : request.endAt();
        valid(finalStartAt, finalEndAt);

        schedule.update(
                request.startAt(),
                request.endAt(),
                request.meetingPlace() == null ? null : request.meetingPlace().strip()
        );
        scheduleRepository.flush();
        if (notificationChanged) {
            notifyMembers(study, schedule, memberId, notificationPort::notifyScheduleChanged);
        }
        return StudyScheduleUpdateResponse.from(schedule);
    }

    public StudyScheduleDetailResponse getSchedule(Long memberId, Long studyId) {
        Study study = study(studyId);
        active(memberId);
        boolean leader = StudyAccessPolicy.isLeader(study, memberId);
        boolean activeMember = StudyAccessPolicy.isActiveMember(
                studyMemberRepository.findByStudyIdAndStatus(
                        studyId,
                        StudyMemberStatus.ACTIVE
                ),
                memberId
        );
        if (!leader && !activeMember) {
            throw new BusinessException(ErrorCode.STUDY_SCHEDULE_ACCESS_DENIED);
        }
        return StudyScheduleDetailResponse.from(
                study,
                leader,
                StudyAccessPolicy.canManageSchedule(study.getStatus(), leader),
                scheduleRepository.findByStudyId(studyId).orElse(null)
        );
    }

    @Transactional
    public StudyScheduleDeleteResponse deleteSchedule(Long memberId, Long studyId) {
        Study study = studyForUpdate(studyId);
        active(memberId);
        leader(study, memberId, ErrorCode.STUDY_SCHEDULE_DELETE_FORBIDDEN);
        writable(study, ErrorCode.STUDY_SCHEDULE_DELETE_NOT_ALLOWED);
        Schedule schedule = scheduleRepository.findByStudyId(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_SCHEDULE_NOT_FOUND));
        if (schedule.getStatus() == ScheduleStatus.CANCELED) {
            throw new BusinessException(ErrorCode.STUDY_SCHEDULE_NOT_FOUND);
        }
        if (schedule.getStatus() == ScheduleStatus.COMPLETED) {
            throw new BusinessException(
                    ErrorCode.STUDY_SCHEDULE_DELETE_NOT_ALLOWED,
                    Map.of("scheduleStatus", "COMPLETED")
            );
        }

        schedule.cancel();
        scheduleRepository.flush();
        notifyMembers(study, schedule, memberId, notificationPort::notifyScheduleCanceled);
        return StudyScheduleDeleteResponse.from(schedule);
    }

    private void notifyMembers(
            Study study,
            Schedule schedule,
            Long actorId,
            Consumer<StudyNotificationPort.ScheduleNotification> notifier
    ) {
        var recipients = studyMemberRepository.findByStudyIdAndStatus(
                        study.getId(),
                        StudyMemberStatus.ACTIVE
                ).stream()
                .filter(studyMember -> !studyMember.getMemberId().equals(study.getLeaderId()))
                .toList();
        var recipientIds = recipients.stream()
                .map(StudyMember::getMemberId)
                .toList();
        var notificationAgreements = memberRepository.findAllById(recipientIds)
                .stream()
                .collect(Collectors.toMap(
                        Member::getId,
                        Member::isServiceNotificationAgreed
                ));
        recipients.forEach(studyMember -> notifier.accept(
                new StudyNotificationPort.ScheduleNotification(
                        studyMember.getMemberId(),
                        actorId,
                        study.getId(),
                        schedule.getId(),
                        study.getTitle(),
                        StudyScheduleCreateResponse.f(schedule.getStartAt()),
                        schedule.getMeetingPlace(),
                        schedule.getUpdatedAt().toEpochMilli(),
                        notificationAgreements.getOrDefault(
                                studyMember.getMemberId(),
                                false
                        )
                )
        ));
    }

    private Study study(Long studyId) {
        return studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
    }

    private Study studyForUpdate(Long studyId) {
        return studyRepository.findForUpdateByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
    }

    private void active(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE
                        && member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void leader(Study study, Long memberId, ErrorCode errorCode) {
        if (!StudyAccessPolicy.isLeader(study, memberId)) {
            throw new BusinessException(errorCode);
        }
    }

    private void writable(Study study, ErrorCode errorCode) {
        if (!StudyAccessPolicy.canManageSchedule(study.getStatus(), true)) {
            throw new BusinessException(
                    errorCode,
                    Map.of("studyStatus", study.getStatus().name())
            );
        }
    }

    private void valid(Instant startAt, Instant endAt) {
        if (!startAt.isAfter(Instant.now())) {
            throw new BusinessException(
                    ErrorCode.STUDY_SCHEDULE_TIME_INVALID,
                    "임장 시작 시각은 현재 이후여야 합니다."
            );
        }
        if (endAt != null && endAt.isBefore(startAt)) {
            throw new BusinessException(
                    ErrorCode.STUDY_SCHEDULE_TIME_INVALID,
                    Map.of("field", "endTime")
            );
        }
    }
}
