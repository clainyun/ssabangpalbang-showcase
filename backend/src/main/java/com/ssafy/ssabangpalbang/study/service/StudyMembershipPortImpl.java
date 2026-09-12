package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.chat.port.StudyMembershipPort;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 채팅 도메인이 요구하는 {@link StudyMembershipPort}의 스터디 측 구현이다.
 *
 * <p>현재 권한 기준(BE-008 병합 전):</p>
 * <ul>
 *     <li>스터디가 존재함(삭제되지 않음)</li>
 *     <li>StudyMember가 존재하고 status == ACTIVE</li>
 *     <li>COMPLETED 스터디는 SUBSCRIBE(이 클래스의 isStudyMember)는 허용하지만
 *         SEND(isStudyOpenForSend)는 차단한다</li>
 * </ul>
 *
 * <p>BE-008이 승인·강퇴 시 StudyMember 상태를 올바르게 ACTIVE/REMOVED로 관리하면,
 * 이 클래스는 수정 없이 그대로 연동되는 것을 목표로 한다.</p>
 */
@Component
@RequiredArgsConstructor
public class StudyMembershipPortImpl implements StudyMembershipPort {

    private final StudyRepository studyRepository;
    private final StudyMemberRepository studyMemberRepository;

    @Override
    public boolean isStudyMember(Long studyId, Long memberId) {
        return studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .map(study -> studyMemberRepository
                        .findByStudyIdAndMemberId(studyId, memberId)
                        .filter(studyMember ->
                                studyMember.getStatus() == StudyMemberStatus.ACTIVE)
                        .isPresent())
                .orElse(false);
    }

    @Override
    public boolean isStudyOpenForSend(Long studyId) {
        return studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .map(study -> study.getStatus() != StudyStatus.COMPLETED
                        && study.getStatus() != StudyStatus.CANCELED)
                .orElse(false);
    }
}
