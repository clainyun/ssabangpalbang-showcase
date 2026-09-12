package com.ssafy.ssabangpalbang.review.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.review.domain.MemberReview;
import com.ssafy.ssabangpalbang.review.domain.MemberReviewTag;
import com.ssafy.ssabangpalbang.review.domain.ReviewTag;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewItemResponse;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewListResponse;
import com.ssafy.ssabangpalbang.review.dto.response.ReviewTagView;
import com.ssafy.ssabangpalbang.review.repository.MemberReviewRepository;
import com.ssafy.ssabangpalbang.review.repository.MemberReviewTagRepository;
import com.ssafy.ssabangpalbang.review.support.MemberReviewCursor;
import com.ssafy.ssabangpalbang.review.support.MemberReviewCursorCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberReviewQueryService {

    private final MemberRepository memberRepository;
    private final MemberReviewRepository memberReviewRepository;
    private final MemberReviewTagRepository memberReviewTagRepository;
    private final MemberReviewSummaryReader memberReviewSummaryReader;
    private final MemberReviewCursorCodec cursorCodec;

    @Transactional(readOnly = true)
    public MemberReviewListResponse getReviews(
            Long viewerId,
            Long revieweeId,
            String encodedCursor,
            int size
    ) {
        requireActiveViewer(viewerId);
        requireActiveReviewee(revieweeId);

        MemberReviewCursor cursor = encodedCursor == null
                ? null
                : cursorCodec.decode(encodedCursor, revieweeId);
        PageRequest limit = PageRequest.of(0, size + 1);
        List<MemberReview> fetched = cursor == null
                ? memberReviewRepository.findFirstPageByRevieweeId(
                        revieweeId,
                        limit
                )
                : memberReviewRepository.findNextPageByRevieweeId(
                        revieweeId,
                        cursor.createdAt(),
                        cursor.reviewId(),
                        limit
                );

        boolean hasNext = fetched.size() > size;
        List<MemberReview> page = hasNext
                ? fetched.subList(0, size)
                : fetched;
        Map<Long, List<ReviewTagView>> tagsByReview = loadTags(page);
        List<MemberReviewItemResponse> content = page.stream()
                .map(review -> MemberReviewItemResponse.from(
                        review,
                        tagsByReview.getOrDefault(
                                review.getId(),
                                List.of()
                        )
                ))
                .toList();
        String nextCursor = hasNext
                ? encodeCursor(revieweeId, page.get(page.size() - 1))
                : null;

        return new MemberReviewListResponse(
                memberReviewSummaryReader.read(revieweeId),
                content,
                nextCursor,
                hasNext
        );
    }

    private Map<Long, List<ReviewTagView>> loadTags(List<MemberReview> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        List<Long> reviewIds = page.stream()
                .map(MemberReview::getId)
                .toList();
        return memberReviewTagRepository.findByReviewIdIn(reviewIds).stream()
                .filter(tag -> ReviewTag.isValidCode(tag.getTagCode()))
                .collect(Collectors.groupingBy(
                        MemberReviewTag::getReviewId,
                        Collectors.mapping(
                                tag -> ReviewTagView.of(
                                        ReviewTag.fromCode(tag.getTagCode())
                                ),
                                Collectors.toCollection(ArrayList::new)
                        )
                ));
    }

    private String encodeCursor(
            Long revieweeId,
            MemberReview review
    ) {
        return cursorCodec.encode(MemberReviewCursor.of(
                revieweeId,
                review.getCreatedAt(),
                review.getId()
        ));
    }

    private void requireActiveViewer(Long viewerId) {
        Member viewer = memberRepository.findById(viewerId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (viewer.getStatus() != MemberStatus.ACTIVE
                || viewer.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.AUTH_MEMBER_WITHDRAWN);
        }
    }

    private void requireActiveReviewee(Long revieweeId) {
        memberRepository.findById(revieweeId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
