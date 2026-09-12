package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.request.MemberStudyStatus;
import com.ssafy.ssabangpalbang.member.dto.response.MemberStudyResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.repository.MemberStudyRow;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberStudyService {

    private final MemberRepository memberRepository;
    private final StudyRepository studyRepository;

    @Transactional(readOnly = true)
    public PageResponse<MemberStudyResponse> getMyStudies(
            Long memberId,
            MemberStudyStatus status,
            int page,
            int size
    ) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));

        Page<MemberStudyRow> studies = studyRepository.findMemberStudies(
                memberId,
                status.name(),
                PageRequest.of(page, size)
        );
        return PageResponse.from(studies.map(MemberStudyResponse::from));
    }
}
