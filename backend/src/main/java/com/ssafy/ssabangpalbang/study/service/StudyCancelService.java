package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.dto.response.StudyCancelResponse;
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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudyCancelService {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final StudyRepository studyRepository;
    private final MemberRepository memberRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final StudyApplicationRepository studyApplicationRepository;
    private final StudyNotificationPort studyNotificationPort;

    @Transactional
    public StudyCancelResponse cancel(Long memberId, Long studyId) {
        requireActiveMember(memberId);
        Study study = studyRepository.findForUpdateByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        if (!StudyAccessPolicy.isLeader(study, memberId)) {
            throw new BusinessException(ErrorCode.STUDY_CANCEL_FORBIDDEN);
        }
        if (study.getStatus() == StudyStatus.CANCELED) {
            throw new BusinessException(ErrorCode.STUDY_ALREADY_CANCELED);
        }
        if (!StudyAccessPolicy.canCancel(study.getStatus())) {
            throw new BusinessException(
                    ErrorCode.STUDY_CANCEL_NOT_ALLOWED,
                    Map.of("studyId", studyId, "status", study.getStatus().name())
            );
        }

        Set<Long> recipientIds = cancellationRecipientIds(studyId, memberId);
        Instant canceledAt = Instant.now();
        study.cancel(canceledAt);
        studyRepository.saveAndFlush(study);
        recipientIds.forEach(recipientId -> studyNotificationPort.notifyStudyCanceled(
                new StudyNotificationPort.StudyCanceledNotification(
                        recipientId,
                        memberId,
                        studyId,
                        study.getTitle()
                )
        ));

        return new StudyCancelResponse(
                studyId,
                study.getStatus().name(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                        study.getCanceledAt().atZone(SEOUL_ZONE_ID)
                )
        );
    }

    private void requireActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Set<Long> cancellationRecipientIds(Long studyId, Long actorId) {
        Set<Long> recipientIds = new LinkedHashSet<>();
        studyMemberRepository.findByStudyIdAndStatus(studyId, StudyMemberStatus.ACTIVE)
                .forEach(member -> recipientIds.add(member.getMemberId()));
        studyApplicationRepository.findByStudyIdAndStatus(
                        studyId, StudyApplicationStatus.PENDING)
                .forEach(application -> recipientIds.add(application.getApplicantId()));
        recipientIds.remove(actorId);
        return recipientIds;
    }
}
