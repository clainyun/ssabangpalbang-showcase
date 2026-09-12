package com.ssafy.ssabangpalbang.review.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.review.domain.MemberReview;
import com.ssafy.ssabangpalbang.review.domain.MemberReviewTag;
import com.ssafy.ssabangpalbang.review.domain.ReviewTag;
import com.ssafy.ssabangpalbang.review.dto.request.MemberReviewCreateRequest;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewCreateResponse;
import com.ssafy.ssabangpalbang.review.dto.response.ReviewTagView;
import com.ssafy.ssabangpalbang.review.repository.MemberReviewRepository;
import com.ssafy.ssabangpalbang.review.repository.MemberReviewTagRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberReviewService {

    private static final int CONTENT_MAX_LENGTH = 500;
    private static final int MAX_TAGS = 15;

    private final MemberRepository memberRepository;
    private final StudyRepository studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final MemberReviewRepository memberReviewRepository;
    private final MemberReviewTagRepository memberReviewTagRepository;

    @Transactional
    public MemberReviewCreateResponse createReview(
            Long reviewerId,
            Long studyId,
            Long revieweeId,
            MemberReviewCreateRequest request
    ) {
        requireActiveMember(reviewerId, ErrorCode.MEMBER_NOT_FOUND);
        Study study = requireStudy(studyId);
        requireActiveStudyMember(
                studyId,
                reviewerId,
                ErrorCode.MEMBER_REVIEW_CREATE_FORBIDDEN
        );
        if (study.getStatus() == StudyStatus.CANCELED) {
            throw new BusinessException(
                    ErrorCode.MEMBER_REVIEW_STUDY_CANCELED
            );
        }
        if (reviewerId.equals(revieweeId)) {
            throw new BusinessException(
                    ErrorCode.MEMBER_REVIEW_SELF_NOT_ALLOWED
            );
        }
        requireActiveStudyMember(
                studyId,
                revieweeId,
                ErrorCode.MEMBER_REVIEW_TARGET_NOT_FOUND
        );
        requireActiveMember(
                revieweeId,
                ErrorCode.MEMBER_REVIEW_TARGET_NOT_FOUND
        );

        List<ReviewTag> tags = resolveTags(request.tagsOrEmpty());
        boolean liked = request.liked();
        requireSignal(tags, liked);
        String content = normalizeContent(request.content());

        if (memberReviewRepository
                .existsByStudyIdAndReviewerIdAndRevieweeId(
                        studyId,
                        reviewerId,
                        revieweeId
                )) {
            throw new BusinessException(
                    ErrorCode.MEMBER_REVIEW_ALREADY_EXISTS
            );
        }

        MemberReview saved;
        try {
            saved = memberReviewRepository.saveAndFlush(
                    MemberReview.create(
                            studyId,
                            reviewerId,
                            revieweeId,
                            liked,
                            content
                    )
            );
        } catch (DataIntegrityViolationException exception) {
            // 리뷰 본체 saveAndFlush에서만 발생하는 무결성 위반은
            // uq_member_review_study_reviewer_reviewee 동시 등록 경쟁이므로
            // 도메인 중복으로 변환한다. 태그 저장 등 다른 무결성 오류는 그대로 전파한다.
            throw new BusinessException(
                    ErrorCode.MEMBER_REVIEW_ALREADY_EXISTS
            );
        }
        memberReviewTagRepository.saveAll(tags.stream()
                .map(tag -> MemberReviewTag.create(saved.getId(), tag))
                .toList());
        List<ReviewTagView> tagViews = tags.stream()
                .map(ReviewTagView::of)
                .toList();
        return MemberReviewCreateResponse.from(saved, tagViews);
    }

    private List<ReviewTag> resolveTags(List<String> tagCodes) {
        if (tagCodes.size() > MAX_TAGS) {
            throw new BusinessException(ErrorCode.MEMBER_REVIEW_TAG_INVALID);
        }
        LinkedHashSet<ReviewTag> resolved = new LinkedHashSet<>();
        for (String tagCode : tagCodes) {
            ReviewTag tag = ReviewTag.fromCode(tagCode);
            if (!resolved.add(tag)) {
                throw new BusinessException(
                        ErrorCode.MEMBER_REVIEW_TAG_INVALID
                );
            }
        }
        return List.copyOf(resolved);
    }

    private void requireSignal(List<ReviewTag> tags, boolean liked) {
        if (tags.isEmpty() && !liked) {
            throw new BusinessException(
                    ErrorCode.MEMBER_REVIEW_SIGNAL_REQUIRED
            );
        }
    }

    private Study requireStudy(Long studyId) {
        return studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.STUDY_NOT_FOUND));
    }

    private void requireActiveMember(Long memberId, ErrorCode errorCode) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(errorCode));
    }

    private void requireActiveStudyMember(
            Long studyId,
            Long memberId,
            ErrorCode errorCode
    ) {
        studyMemberRepository.findByStudyIdAndMemberId(studyId, memberId)
                .filter(studyMember ->
                        studyMember.getStatus() == StudyMemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(errorCode));
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String normalized = content.strip();
        if (normalized.length() > CONTENT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }
}
