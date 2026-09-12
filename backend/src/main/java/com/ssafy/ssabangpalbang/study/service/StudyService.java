package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplication;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberRole;
import com.ssafy.ssabangpalbang.study.domain.Schedule;
import com.ssafy.ssabangpalbang.study.domain.ScheduleStatus;
import com.ssafy.ssabangpalbang.study.dto.request.StudyCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyApplicationCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyUpdateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.ApartmentStudySort;
import com.ssafy.ssabangpalbang.study.dto.response.ApartmentStudyResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyCreateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationCreateResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyDetailResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyUpdateResponse;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyDetailQueryRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import com.ssafy.ssabangpalbang.study.repository.RecruitingStudyRow;
import com.ssafy.ssabangpalbang.study.service.port.StudyNotificationPort;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyService {

    private static final String APPLICATION_UNIQUE_CONSTRAINT =
            "study_application_study_id_applicant_id_key";

    private final StudyRepository studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final MemberRepository memberRepository;
    private final ApartmentRepository apartmentRepository;
    private final StudyApplicationRepository studyApplicationRepository;
    private final ScheduleRepository scheduleRepository;
    private final StudyDetailQueryRepository studyDetailQueryRepository;
    private final StudyNotificationPort studyNotificationPort;
    private final Clock clock;

    @Transactional
    public StudyCreateResponse create(Long memberId, StudyCreateRequest request) {
        validateCapacity(request.capacity());
        StudyPurpose purpose = parsePurpose(request.purpose());

        Member member = memberRepository.findById(memberId)
                .filter(found -> found.getStatus() == MemberStatus.ACTIVE)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Apartment apartment = apartmentRepository.findById(request.apartmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APARTMENT_NOT_FOUND));

        Study study = studyRepository.save(Study.create(
                apartment.getId(),
                member.getId(),
                request.title(),
                request.intro(),
                request.goal(),
                request.capacity(),
                purpose
        ));
        studyMemberRepository.save(StudyMember.createLeader(study.getId(), member.getId()));
        if (request.capacity() == 1) {
            study.closeRecruitment(Instant.now());
        }

        return StudyCreateResponse.from(study, apartment, member);
    }

    @Transactional
    public StudyUpdateResponse updateDetails(
            Long memberId,
            Long studyId,
            StudyUpdateRequest request
    ) {
        memberRepository.findById(memberId)
                .filter(found -> found.getStatus() == MemberStatus.ACTIVE)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Study study = studyRepository.findForUpdateByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));

        boolean isLeader = StudyAccessPolicy.isLeader(study, memberId);
        if (!isLeader) {
            throw new BusinessException(ErrorCode.STUDY_UPDATE_FORBIDDEN);
        }
        studyMemberRepository.findForUpdateByStudyIdAndMemberId(studyId, memberId)
                .filter(membership -> membership.getStatus() == StudyMemberStatus.ACTIVE)
                .filter(membership -> membership.getRole() == StudyMemberRole.LEADER)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_UPDATE_FORBIDDEN));
        if (!StudyAccessPolicy.canUpdateDetails(study.getStatus(), true)) {
            throw new BusinessException(
                    ErrorCode.STUDY_UPDATE_NOT_ALLOWED,
                    Map.of("studyStatus", study.getStatus().name())
            );
        }
        validateUpdateDetails(request);

        study.updateDetails(request.title(), request.intro(), request.goal());
        return StudyUpdateResponse.from(study);
    }

    @Transactional
    public StudyApplicationCreateResponse applyToStudy(
            Long studyId,
            Long applicantId,
            StudyApplicationCreateRequest request
    ) {
        StudyPurpose purpose = parseApplicationPurpose(request.purpose());
        Member applicant = memberRepository.findById(applicantId)
                .filter(found -> found.getStatus() == MemberStatus.ACTIVE)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Study study = studyRepository.findForUpdateByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));

        if (study.getStatus() != StudyStatus.RECRUITING
                || study.getLeaderId().equals(applicantId)) {
            throw new BusinessException(ErrorCode.STUDY_APPLICATION_NOT_ALLOWED);
        }

        if (studyMemberRepository.findByStudyIdAndMemberId(studyId, applicantId)
                .filter(found -> found.getStatus() == StudyMemberStatus.ACTIVE)
                .isPresent()) {
            throw new BusinessException(ErrorCode.STUDY_ALREADY_MEMBER);
        }
        if (studyApplicationRepository.findByStudyIdAndApplicantId(studyId, applicantId)
                .isPresent()) {
            throw new BusinessException(ErrorCode.STUDY_APPLICATION_ALREADY_EXISTS);
        }

        long activeMemberCount = studyMemberRepository.countByStudyIdAndStatus(
                studyId, StudyMemberStatus.ACTIVE);
        if (activeMemberCount >= study.getCapacity()) {
            throw new BusinessException(ErrorCode.STUDY_CAPACITY_FULL);
        }

        StudyApplication application = StudyApplication.create(
                studyId,
                applicantId,
                request.intro(),
                purpose
        );
        try {
            application = studyApplicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateApplicationConstraint(exception)) {
                throw new BusinessException(ErrorCode.STUDY_APPLICATION_ALREADY_EXISTS);
            }
            throw exception;
        }
        boolean leaderServiceNotificationAgreed = memberRepository
                .findById(study.getLeaderId())
                .map(Member::isServiceNotificationAgreed)
                .orElse(false);
        studyNotificationPort.notifyApplicationSubmitted(new StudyNotificationPort.ApplicationSubmittedNotification(
                study.getLeaderId(), applicantId, studyId, application.getId(),
                study.getTitle(), applicant.getNickname(), leaderServiceNotificationAgreed));
        return StudyApplicationCreateResponse.from(application, applicant);
    }

    @Transactional(readOnly = true)
    public StudyDetailResponse getDetail(Long memberId, Long studyId) {
        Study study = studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        StudyMember membership = studyMemberRepository.findByStudyIdAndMemberId(studyId, memberId)
                .filter(found -> found.getStatus() == StudyMemberStatus.ACTIVE)
                .orElse(null);
        boolean isMember = membership != null;
        boolean isLeader = StudyAccessPolicy.isLeader(study, memberId);
        long memberCount = studyMemberRepository.countByStudyIdAndStatus(
                studyId, StudyMemberStatus.ACTIVE);
        String participation = isMember ? "APPROVED"
                : studyApplicationRepository.findByStudyIdAndApplicantId(studyId, memberId)
                .map(application -> application.getStatus().name()).orElse("NONE");
        Schedule schedule = scheduleRepository.findByStudyIdAndStatus(
                studyId, ScheduleStatus.SCHEDULED).orElse(null);
        Apartment apartment = apartmentRepository.findById(study.getApartmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APARTMENT_NOT_FOUND));
        Member leader = memberRepository.findById(study.getLeaderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<StudyMember> activeMembers = List.of();
        Map<Long, Member> memberMap = Map.of();
        Long unread = null;
        String fieldStatus = null;
        Long fieldSessionId = null;
        StudyDetailResponse.ReportSummary report = null;
        if (isMember) {
            activeMembers = studyMemberRepository.findByStudyIdAndStatus(
                            studyId, StudyMemberStatus.ACTIVE).stream()
                    .sorted(Comparator
                            .comparing((StudyMember sm) -> sm.getRole() != StudyMemberRole.LEADER)
                            .thenComparing(StudyMember::getJoinedAt))
                    .toList();
            memberMap = memberRepository.findAllById(
                            activeMembers.stream().map(StudyMember::getMemberId).toList())
                    .stream().collect(Collectors.toMap(Member::getId, Function.identity()));
            unread = studyDetailQueryRepository.countUnreadChat(studyId, memberId);
            StudyDetailQueryRepository.FieldSessionReportRow fieldReference =
                    studyDetailQueryRepository.findFieldSessionReport(studyId)
                            .orElse(null);
            fieldStatus = fieldReference == null
                    ? "NOT_STARTED"
                    : fieldReference.fieldVisitStatus();
            fieldSessionId = fieldReference == null
                    ? null
                    : fieldReference.fieldSessionId();
            if (fieldReference != null && fieldReference.reportId() != null) {
                report = new StudyDetailResponse.ReportSummary(
                        fieldReference.reportId(),
                        fieldReference.reportStatus()
                );
            }
        }
        boolean readOnly = StudyAccessPolicy.isReadOnly(study.getStatus());
        boolean canApply = StudyAccessPolicy.canApply(
                study.getStatus(),
                memberCount,
                study.getCapacity(),
                isLeader,
                isMember,
                participation
        );
        boolean canStartFieldVisit = StudyAccessPolicy.canStartFieldVisit(
                isMember,
                study.getStatus(),
                schedule != null,
                schedule == null ? null : schedule.getStartAt(),
                clock.instant(),
                fieldStatus
        );
        StudyAccessPolicy.Permissions permissions =
                StudyAccessPolicy.permissions(isLeader, isMember, study.getStatus());
        return StudyDetailResponse.from(
                study, apartment, leader, schedule, activeMembers, memberMap,
                memberCount, participation, isLeader, isMember, unread, fieldStatus,
                fieldSessionId, report, canApply, canStartFieldVisit, readOnly,
                new StudyDetailResponse.Permissions(
                        permissions.canManageApplications(),
                        permissions.canManageMembers(),
                        permissions.canManageNotices(),
                        permissions.canManageSchedule(),
                        permissions.canUseChat(),
                        permissions.canUseFieldVisit()
                ));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApartmentStudyResponse> getRecruitingStudies(
            Long memberId,
            Long apartmentId,
            ApartmentStudySort sort,
            int page,
            int size
    ) {
        if (!apartmentRepository.existsById(apartmentId)) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_NOT_FOUND,
                    Map.of("apartmentId", apartmentId)
            );
        }
        memberRepository.findById(memberId)
                .filter(found -> found.getStatus() == MemberStatus.ACTIVE)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        PageRequest pageable = PageRequest.of(page, size);
        Page<RecruitingStudyRow> rows = switch (sort) {
            case SCHEDULE_ASC -> studyRepository.findRecruitingOrderByScheduleAsc(
                    apartmentId, pageable);
            case CREATED_DESC -> studyRepository.findRecruitingOrderByCreatedDesc(
                    apartmentId, pageable);
            case REMAINING_CAPACITY_DESC ->
                    studyRepository.findRecruitingOrderByRemainingCapacityDesc(
                            apartmentId, pageable);
        };
        if (rows.isEmpty()) {
            return new PageResponse<>(
                    List.of(),
                    rows.getTotalElements(),
                    rows.getNumber(),
                    rows.getSize(),
                    rows.getTotalPages()
            );
        }

        List<Long> studyIds = rows.stream()
                .map(RecruitingStudyRow::getStudyId)
                .toList();
        Map<Long, Member> leaders = memberRepository.findAllById(
                        rows.stream()
                                .map(RecruitingStudyRow::getLeaderId)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<Long, StudyMember> memberships =
                studyMemberRepository.findByStudyIdInAndMemberIdAndStatus(
                                studyIds, memberId, StudyMemberStatus.ACTIVE)
                        .stream()
                        .collect(Collectors.toMap(StudyMember::getStudyId, Function.identity()));
        Map<Long, StudyApplication> applications =
                studyApplicationRepository.findByStudyIdInAndApplicantId(studyIds, memberId)
                        .stream()
                        .collect(Collectors.toMap(
                                StudyApplication::getStudyId,
                                Function.identity()
                        ));

        return PageResponse.from(rows.map(row -> {
            boolean isMember = memberships.containsKey(row.getStudyId());
            String applicationStatus = isMember
                    ? "APPROVED"
                    : Optional.ofNullable(applications.get(row.getStudyId()))
                    .map(application -> application.getStatus().name())
                    .orElse("NONE");
            Member leader = Optional.ofNullable(leaders.get(row.getLeaderId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            return ApartmentStudyResponse.from(
                    row, leader, applicationStatus, isMember, memberId);
        }));
    }

    private void validateCapacity(Integer capacity) {
        if (capacity == null || capacity < 1 || capacity > 20) {
            throw new BusinessException(ErrorCode.STUDY_CAPACITY_INVALID);
        }
    }

    private void validateUpdateDetails(StudyUpdateRequest request) {
        if (request.title() == null && request.goal() == null && request.intro() == null) {
            throw new BusinessException(ErrorCode.STUDY_UPDATE_EMPTY);
        }
        if (request.title() != null
                && (!hasVisibleText(request.title()) || request.title().length() > 200)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.goal() != null
                && (!hasVisibleText(request.goal()) || request.goal().length() > 300)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.intro() != null && request.intro().length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private boolean hasVisibleText(String value) {
        return value.codePoints().anyMatch(codePoint ->
                !Character.isWhitespace(codePoint)
                        && !Character.isSpaceChar(codePoint)
                        && Character.getType(codePoint) != Character.FORMAT
        );
    }

    private StudyPurpose parsePurpose(String purpose) {
        if (purpose == null) {
            throw new BusinessException(ErrorCode.STUDY_PURPOSE_INVALID);
        }
        try {
            return StudyPurpose.valueOf(purpose);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.STUDY_PURPOSE_INVALID);
        }
    }

    private StudyPurpose parseApplicationPurpose(String purpose) {
        try {
            return StudyPurpose.valueOf(purpose);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.STUDY_APPLICATION_PURPOSE_INVALID);
        }
    }

    private boolean isDuplicateApplicationConstraint(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && APPLICATION_UNIQUE_CONSTRAINT.equals(
                    constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
