package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyApplication;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.dto.response.StudyApplicationListResponse;
import com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyApplicationQueryService {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final StudyRepository studyRepository;
    private final StudyApplicationRepository studyApplicationRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public StudyApplicationListResponse getApplications(
            Long memberId, Long studyId, String status, Long cursor, int size
    ) {
        StudyApplicationStatus applicationStatus = parseStatus(status);
        validatePage(cursor, size);

        Study study = studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        if (!StudyAccessPolicy.isLeader(study, memberId)) {
            throw new BusinessException(ErrorCode.STUDY_APPLICATION_LIST_FORBIDDEN);
        }

        List<StudyApplication> applications = studyApplicationRepository.findPage(
                studyId, applicationStatus, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = applications.size() > size;
        List<StudyApplication> content = hasNext ? applications.subList(0, size) : applications;
        Map<Long, Member> members = membersById(content.stream()
                .map(StudyApplication::getApplicantId).toList());
        long currentMemberCount = studyMemberRepository.countByStudyIdAndStatus(
                studyId, StudyMemberStatus.ACTIVE);

        return new StudyApplicationListResponse(
                content.stream().map(application -> toItem(application, members.get(application.getApplicantId()),
                        study, currentMemberCount)).toList(),
                new StudyApplicationListResponse.Summary(
                        studyApplicationRepository.countByStudyIdAndStatus(studyId, StudyApplicationStatus.PENDING),
                        studyApplicationRepository.countByStudyIdAndStatus(studyId, StudyApplicationStatus.APPROVED),
                        studyApplicationRepository.countByStudyIdAndStatus(studyId, StudyApplicationStatus.REJECTED),
                        currentMemberCount, study.getCapacity()),
                hasNext ? content.get(content.size() - 1).getId() : null,
                hasNext);
    }

    private StudyApplicationStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return StudyApplicationStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.STUDY_APPLICATION_STATUS_INVALID);
        }
    }

    private void validatePage(Long cursor, int size) {
        if ((cursor != null && cursor < 1) || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Map<Long, Member> membersById(Collection<Long> memberIds) {
        return memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
    }

    private StudyApplicationListResponse.ApplicationItem toItem(
            StudyApplication application, Member member, Study study, long currentMemberCount
    ) {
        return new StudyApplicationListResponse.ApplicationItem(
                application.getId(),
                new StudyApplicationListResponse.Applicant(member.getId(), member.getNickname(),
                        member.getSelectedCharacterId()),
                application.getIntro(), application.getPurpose(), application.getStatus().name(),
                StudyAccessPolicy.canApproveApplication(application.getStatus(), study.getStatus(),
                        currentMemberCount, study.getCapacity()),
                StudyAccessPolicy.canRejectApplication(application.getStatus(), study.getStatus()),
                format(application.getCreatedAt()), format(application.getDecidedAt()));
    }

    private String format(Instant instant) {
        return instant == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(SEOUL_ZONE_ID));
    }
}
