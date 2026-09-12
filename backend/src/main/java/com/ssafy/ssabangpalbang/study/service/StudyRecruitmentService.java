package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.dto.response.StudyRecruitmentCloseResponse;
import com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudyRecruitmentService {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final StudyRepository studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final StudyApplicationRepository studyApplicationRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public StudyRecruitmentCloseResponse closeRecruitment(Long memberId, Long studyId) {
        requireActiveMember(memberId);
        Study study = studyRepository.findForUpdateByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));

        if (!StudyAccessPolicy.isLeader(study, memberId)) {
            throw new BusinessException(ErrorCode.STUDY_RECRUITMENT_CLOSE_FORBIDDEN);
        }
        if (study.getStatus() == StudyStatus.CLOSED) {
            throw new BusinessException(ErrorCode.STUDY_RECRUITMENT_ALREADY_CLOSED);
        }
        if (study.getStatus() != StudyStatus.RECRUITING) {
            throw new BusinessException(
                    ErrorCode.STUDY_RECRUITMENT_CLOSE_NOT_ALLOWED,
                    Map.of("studyStatus", study.getStatus().name())
            );
        }

        Instant now = Instant.now();
        study.closeRecruitment(now);
        studyRepository.saveAndFlush(study);

        return new StudyRecruitmentCloseResponse(
                studyId,
                study.getStatus().name(),
                studyMemberRepository.countByStudyIdAndStatus(
                        studyId,
                        StudyMemberStatus.ACTIVE
                ),
                study.getCapacity(),
                studyApplicationRepository.countByStudyIdAndStatus(
                        studyId,
                        StudyApplicationStatus.PENDING
                ),
                format(study.getRecruitmentClosedAt())
        );
    }

    @Transactional
    public StudyRecruitmentCloseResponse reopenRecruitment(Long memberId, Long studyId) {
        requireActiveMember(memberId);
        Study study = studyRepository.findForUpdateByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));

        if (!StudyAccessPolicy.isLeader(study, memberId)) {
            throw new BusinessException(ErrorCode.STUDY_RECRUITMENT_OPEN_FORBIDDEN);
        }
        if (study.getStatus() == StudyStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.STUDY_RECRUITMENT_ALREADY_OPEN);
        }
        if (study.getStatus() != StudyStatus.CLOSED) {
            throw new BusinessException(
                    ErrorCode.STUDY_RECRUITMENT_OPEN_NOT_ALLOWED,
                    Map.of("studyStatus", study.getStatus().name())
            );
        }

        long activeMemberCount = studyMemberRepository.countByStudyIdAndStatus(
                studyId,
                StudyMemberStatus.ACTIVE
        );
        if (activeMemberCount >= study.getCapacity()) {
            throw new BusinessException(ErrorCode.STUDY_CAPACITY_FULL);
        }

        study.reopenRecruitment();
        studyRepository.saveAndFlush(study);

        return new StudyRecruitmentCloseResponse(
                studyId,
                study.getStatus().name(),
                studyMemberRepository.countByStudyIdAndStatus(
                        studyId,
                        StudyMemberStatus.ACTIVE
                ),
                study.getCapacity(),
                studyApplicationRepository.countByStudyIdAndStatus(
                        studyId,
                        StudyApplicationStatus.PENDING
                ),
                study.getRecruitmentClosedAt() == null
                        ? null
                        : format(study.getRecruitmentClosedAt())
        );
    }

    private void requireActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private String format(Instant instant) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                instant.atZone(SEOUL_ZONE_ID)
        );
    }
}
