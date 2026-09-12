package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.review.repository.MemberReviewRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberRole;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.dto.response.StudyMemberListResponse;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyDetailQueryRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyMemberQueryService {

    private final StudyRepository studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final MemberRepository memberRepository;
    private final StudyDetailQueryRepository studyDetailQueryRepository;
    private final MemberReviewRepository memberReviewRepository;

    @Transactional(readOnly = true)
    public StudyMemberListResponse getMembers(Long memberId, Long studyId) {
        Study study = studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        requireActiveMember(memberId);
        List<StudyMember> activeMembers = studyMemberRepository.findByStudyIdAndStatus(
                studyId, StudyMemberStatus.ACTIVE);
        boolean requesterIsLeader = StudyAccessPolicy.isLeader(study, memberId);
        boolean fieldVisitStarted = studyDetailQueryRepository.findFieldSessionStatus(studyId).isPresent();
        if (!requesterIsLeader
                && !StudyAccessPolicy.isActiveMember(activeMembers, memberId)) {
            throw new BusinessException(ErrorCode.STUDY_MEMBER_LIST_FORBIDDEN);
        }

        Map<Long, Member> membersById = memberRepository.findAllById(
                        activeMembers.stream().map(StudyMember::getMemberId).toList())
                .stream()
                .filter(StudyMemberQueryService::isActiveMember)
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        List<StudyMember> sortedMembers = activeMembers.stream()
                .filter(studyMember -> membersById.containsKey(studyMember.getMemberId()))
                .sorted(Comparator
                        .comparing((StudyMember studyMember) ->
                                studyMember.getRole() != StudyMemberRole.LEADER)
                        .thenComparing(StudyMember::getJoinedAt)
                .thenComparing(StudyMember::getMemberId))
                .toList();
        Set<Long> reviewedMemberIds = Set.copyOf(
                memberReviewRepository.findRevieweeIdsByStudyIdAndReviewerId(
                        studyId,
                        memberId
                )
        );
        List<StudyMemberListResponse.MemberItem> items = sortedMembers.stream()
                .map(studyMember -> StudyMemberListResponse.MemberItem.from(
                        studyMember,
                        membersById.get(studyMember.getMemberId()),
                        StudyAccessPolicy.canKick(
                                study.getStatus(),
                                fieldVisitStarted,
                                requesterIsLeader,
                                memberId,
                                studyMember.getMemberId()),
                        reviewedMemberIds.contains(studyMember.getMemberId())))
                .toList();
        return new StudyMemberListResponse(
                study.getId(),
                requesterIsLeader,
                items.size(),
                study.getCapacity(),
                items
        );
    }

    private void requireActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(StudyMemberQueryService::isActiveMember)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private static boolean isActiveMember(Member member) {
        return member.getStatus() == MemberStatus.ACTIVE
                && member.getDeletedAt() == null;
    }
}
