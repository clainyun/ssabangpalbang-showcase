package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplication;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationApproveResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationRejectResponse;
import com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import com.ssafy.ssabangpalbang.study.service.port.StudyNotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudyApplicationDecisionService {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final StudyRepository studyRepository;
    private final StudyApplicationRepository studyApplicationRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final MemberRepository memberRepository;
    private final StudyNotificationPort studyNotificationPort;

    @Transactional
    public StudyApplicationApproveResponse approve(Long memberId, Long studyId, Long applicationId) {
        Study study = studyRepository.findForUpdateByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        validateLeader(study, memberId, ErrorCode.STUDY_APPLICATION_APPROVE_FORBIDDEN);
        StudyApplication application = findMatchingApplication(studyId, applicationId);
        long currentMemberCount = studyMemberRepository.countByStudyIdAndStatus(studyId, StudyMemberStatus.ACTIVE);
        Optional<StudyMember> existingMember = studyMemberRepository.findByStudyIdAndMemberId(
                studyId, application.getApplicantId());
        validateApproval(application, study, currentMemberCount, existingMember);

        Instant now = Instant.now();
        application.approve(now);
        existingMember
                .ifPresentOrElse(StudyMember::reactivate,
                        () -> studyMemberRepository.save(StudyMember.createMember(studyId, application.getApplicantId())));

        long approvedMemberCount = currentMemberCount + 1;
        if (approvedMemberCount == study.getCapacity()) {
            study.closeRecruitment(now);
        }
        Member applicant = memberRepository.findById(application.getApplicantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        studyNotificationPort.notifyApplicationApproved(notification(
                application,
                study,
                memberId,
                applicant.isServiceNotificationAgreed()
        ));
        return new StudyApplicationApproveResponse(application.getId(), study.getId(),
                new StudyApplicationApproveResponse.Applicant(applicant.getId(), applicant.getNickname(),
                        applicant.getProfileImageUrl(), applicant.getSelectedCharacterId()),
                application.getStatus().name(), approvedMemberCount, study.getCapacity(),
                study.getStatus().name(), format(application.getDecidedAt()));
    }

    @Transactional
    public StudyApplicationRejectResponse reject(Long memberId, Long studyId, Long applicationId) {
        Study study = studyRepository.findForUpdateByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        validateLeader(study, memberId, ErrorCode.STUDY_APPLICATION_REJECT_FORBIDDEN);
        StudyApplication application = findMatchingApplication(studyId, applicationId);
        validateRejection(application, study);

        application.reject(Instant.now());
        Member applicant = memberRepository.findById(application.getApplicantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        studyNotificationPort.notifyApplicationRejected(notification(
                application,
                study,
                memberId,
                applicant.isServiceNotificationAgreed()
        ));
        return new StudyApplicationRejectResponse(application.getId(), study.getId(),
                new StudyApplicationRejectResponse.Applicant(applicant.getId(), applicant.getNickname(),
                        applicant.getSelectedCharacterId()),
                application.getStatus().name(), format(application.getDecidedAt()));
    }

    private void validateLeader(Study study, Long memberId, ErrorCode errorCode) {
        if (!StudyAccessPolicy.isLeader(study, memberId)) {
            throw new BusinessException(errorCode);
        }
    }

    private StudyApplication findMatchingApplication(Long studyId, Long applicationId) {
        StudyApplication application = studyApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_APPLICATION_NOT_FOUND));
        if (!application.getStudyId().equals(studyId)) {
            throw new BusinessException(ErrorCode.STUDY_APPLICATION_STUDY_MISMATCH);
        }
        return application;
    }

    private void validateApproval(
            StudyApplication application,
            Study study,
            long currentMemberCount,
            Optional<StudyMember> existingMember
    ) {
        if (!StudyAccessPolicy.canApproveApplication(application.getStatus(), study.getStatus(),
                currentMemberCount, study.getCapacity())) {
            if (application.getStatus() != StudyApplicationStatus.PENDING) {
                throw new BusinessException(ErrorCode.STUDY_APPLICATION_ALREADY_PROCESSED,
                        Map.of("status", application.getStatus().name()));
            }
            if (study.getStatus() != StudyStatus.RECRUITING) {
                throw new BusinessException(ErrorCode.STUDY_APPLICATION_APPROVE_NOT_ALLOWED,
                        Map.of("studyStatus", study.getStatus().name()));
            }
            throw new BusinessException(ErrorCode.STUDY_CAPACITY_FULL,
                    Map.of("currentMemberCount", currentMemberCount, "capacity", study.getCapacity()));
        }
        existingMember
                .filter(member -> member.getStatus() == StudyMemberStatus.ACTIVE)
                .ifPresent(member -> {
                    throw new BusinessException(ErrorCode.STUDY_ALREADY_MEMBER);
                });
    }

    private void validateRejection(StudyApplication application, Study study) {
        if (!StudyAccessPolicy.canRejectApplication(application.getStatus(), study.getStatus())) {
            if (application.getStatus() != StudyApplicationStatus.PENDING) {
                throw new BusinessException(ErrorCode.STUDY_APPLICATION_ALREADY_PROCESSED,
                        Map.of("status", application.getStatus().name()));
            }
            throw new BusinessException(ErrorCode.STUDY_APPLICATION_REJECT_NOT_ALLOWED,
                    Map.of("studyStatus", study.getStatus().name()));
        }
    }

    private StudyNotificationPort.ApplicationDecisionNotification notification(
            StudyApplication application,
            Study study,
            Long actorId,
            boolean serviceNotificationAgreed
    ) {
        return new StudyNotificationPort.ApplicationDecisionNotification(application.getApplicantId(), actorId,
                study.getId(), application.getId(), study.getTitle(), serviceNotificationAgreed);
    }

    private String format(Instant instant) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(SEOUL_ZONE_ID));
    }
}
