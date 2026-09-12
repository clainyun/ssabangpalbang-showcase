package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberRole;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.dto.response.StudyMemberKickResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyMemberLeaveResponse;
import com.ssafy.ssabangpalbang.study.repository.StudyDetailQueryRepository;
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
public class StudyMemberCommandService {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final StudyRepository studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final StudyDetailQueryRepository studyDetailQueryRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public StudyMemberKickResponse kick(Long requesterId, Long studyId, Long memberId) {
        requireActiveMember(requesterId, ErrorCode.MEMBER_NOT_FOUND);
        Study study = studyRepository.findForUpdateByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));

        if (!StudyAccessPolicy.isLeader(study, requesterId)) {
            throw new BusinessException(ErrorCode.STUDY_MEMBER_KICK_FORBIDDEN);
        }
        if (memberId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.STUDY_LEADER_CANNOT_BE_KICKED);
        }

        StudyMember target = studyMemberRepository
                .findForUpdateByStudyIdAndMemberId(studyId, memberId)
                .filter(member -> member.getStatus() == StudyMemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_MEMBER_NOT_FOUND));
        if (target.getRole() == StudyMemberRole.LEADER) {
            throw new BusinessException(ErrorCode.STUDY_LEADER_CANNOT_BE_KICKED);
        }
        requireActiveMember(memberId, ErrorCode.STUDY_MEMBER_NOT_FOUND);

        if (!StudyAccessPolicy.canKick(
                study.getStatus(),
                studyDetailQueryRepository.findFieldSessionStatus(studyId).isPresent(),
                true,
                requesterId,
                memberId
        )) {
            throw new BusinessException(
                    ErrorCode.STUDY_MEMBER_KICK_NOT_ALLOWED,
                    Map.of("studyStatus", study.getStatus().name())
            );
        }

        Instant now = Instant.now();
        target.kick(now);
        studyMemberRepository.saveAndFlush(target);
        long currentMemberCount = studyMemberRepository.countByStudyIdAndStatus(
                studyId,
                StudyMemberStatus.ACTIVE
        );

        return new StudyMemberKickResponse(
                studyId,
                memberId,
                currentMemberCount,
                study.getCapacity(),
                study.getStatus().name(),
                format(target.getLeftAt())
        );
    }

    @Transactional
    public StudyMemberLeaveResponse leave(Long requesterId, Long studyId) {
        requireActiveMember(requesterId, ErrorCode.MEMBER_NOT_FOUND);
        Study study = studyRepository.findForUpdateByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        StudyMember membership = studyMemberRepository
                .findForUpdateByStudyIdAndMemberId(studyId, requesterId)
                .filter(member -> member.getStatus() == StudyMemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_MEMBER_NOT_FOUND));

        boolean isLeader = StudyAccessPolicy.isLeader(study, requesterId)
                || membership.getRole() == StudyMemberRole.LEADER;
        if (isLeader) {
            throw new BusinessException(ErrorCode.STUDY_LEADER_CANNOT_LEAVE);
        }
        if (!StudyAccessPolicy.canLeave(
                study.getStatus(),
                studyDetailQueryRepository.findFieldSessionStatus(studyId).isPresent(),
                isLeader
        )) {
            throw new BusinessException(
                    ErrorCode.STUDY_MEMBER_LEAVE_NOT_ALLOWED,
                    Map.of("studyStatus", study.getStatus().name())
            );
        }

        Instant now = Instant.now();
        membership.leave(now);
        studyMemberRepository.saveAndFlush(membership);
        long currentMemberCount = studyMemberRepository.countByStudyIdAndStatus(
                studyId,
                StudyMemberStatus.ACTIVE
        );

        return new StudyMemberLeaveResponse(
                studyId,
                requesterId,
                currentMemberCount,
                study.getCapacity(),
                study.getStatus().name(),
                format(membership.getLeftAt())
        );
    }

    private void requireActiveMember(Long memberId, ErrorCode errorCode) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(errorCode));
    }

    private String format(Instant instant) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                instant.atZone(SEOUL_ZONE_ID)
        );
    }
}
