package com.ssafy.ssabangpalbang.review.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.review.domain.MemberReview;
import com.ssafy.ssabangpalbang.review.domain.MemberReviewTag;
import com.ssafy.ssabangpalbang.review.domain.ReviewTag;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewListResponse;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewSummaryResponse;
import com.ssafy.ssabangpalbang.review.dto.response.ReviewTagCountView;
import com.ssafy.ssabangpalbang.review.repository.MemberReviewRepository;
import com.ssafy.ssabangpalbang.review.repository.MemberReviewTagRepository;
import com.ssafy.ssabangpalbang.review.support.MemberReviewCursor;
import com.ssafy.ssabangpalbang.review.support.MemberReviewCursorCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberReviewQueryServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock MemberReviewRepository memberReviewRepository;
    @Mock MemberReviewTagRepository memberReviewTagRepository;
    @Mock MemberReviewSummaryReader memberReviewSummaryReader;
    @Mock MemberReviewCursorCodec cursorCodec;

    private MemberReviewQueryService service;

    @BeforeEach
    void setUp() {
        service = new MemberReviewQueryService(
                memberRepository,
                memberReviewRepository,
                memberReviewTagRepository,
                memberReviewSummaryReader,
                cursorCodec
        );
    }

    @Test
    void 첫_페이지를_최신순으로_조회하고_요약과_다음_커서를_반환한다() {
        stubActiveMembers(1L, 2L);
        MemberReview newest = review(30L, true, "최신 리뷰", "2026-08-03T13:30:00Z");
        MemberReview second = review(20L, false, null, "2026-08-03T13:20:00Z");
        MemberReview extra = review(10L, true, "다음 페이지", "2026-08-03T13:10:00Z");
        when(memberReviewRepository.findFirstPageByRevieweeId(
                2L,
                PageRequest.of(0, 3)
        )).thenReturn(List.of(newest, second, extra));
        when(memberReviewTagRepository.findByReviewIdIn(any()))
                .thenReturn(List.of(
                        MemberReviewTag.create(30L, ReviewTag.PUNCTUAL)
                ));
        when(cursorCodec.encode(any(MemberReviewCursor.class)))
                .thenReturn("next-opaque-cursor");
        when(memberReviewSummaryReader.read(2L))
                .thenReturn(summary());

        MemberReviewListResponse response = service.getReviews(1L, 2L, null, 2);

        assertThat(response.summary().reviewCount()).isEqualTo(12L);
        assertThat(response.summary().likeReceivedCount()).isEqualTo(9L);
        assertThat(response.summary().topTags()).extracting("code")
                .containsExactly("PUNCTUAL");
        assertThat(response.content()).extracting("reviewId")
                .containsExactly(30L, 20L);
        assertThat(response.content().get(0).liked()).isTrue();
        assertThat(response.content().get(0).tags()).extracting("code")
                .containsExactly("PUNCTUAL");
        assertThat(response.content().get(1).liked()).isFalse();
        assertThat(response.content().get(1).tags()).isEmpty();
        assertThat(response.content().get(1).content()).isNull();
        assertThat(response.content().get(0).createdAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
        assertThat(response.nextCursor()).isEqualTo("next-opaque-cursor");
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void 다음_커서는_복호화한_시각과_ID를_경계로_사용한다() {
        stubActiveMembers(1L, 2L);
        MemberReviewCursor cursor = MemberReviewCursor.of(
                2L,
                Instant.parse("2026-08-03T13:20:00Z"),
                20L
        );
        when(cursorCodec.decode("opaque-cursor", 2L))
                .thenReturn(cursor);
        when(memberReviewRepository.findNextPageByRevieweeId(
                2L,
                cursor.createdAt(),
                cursor.reviewId(),
                PageRequest.of(0, 3)
        )).thenReturn(List.of(review(
                10L,
                false,
                "마지막 리뷰",
                "2026-08-03T13:10:00Z"
        )));
        when(memberReviewTagRepository.findByReviewIdIn(any()))
                .thenReturn(List.of());
        when(memberReviewSummaryReader.read(2L))
                .thenReturn(summary());

        MemberReviewListResponse response = service.getReviews(
                1L,
                2L,
                "opaque-cursor",
                2
        );

        assertThat(response.content()).extracting("reviewId")
                .containsExactly(10L);
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
        verify(cursorCodec, never()).encode(any());
    }

    @Test
    void 평가가_없으면_빈_요약과_빈_목록을_반환한다() {
        stubActiveMembers(1L, 2L);
        when(memberReviewRepository.findFirstPageByRevieweeId(
                2L,
                PageRequest.of(0, 21)
        )).thenReturn(List.of());
        when(memberReviewSummaryReader.read(2L))
                .thenReturn(new MemberReviewSummaryResponse(List.of(), 0, 0));

        MemberReviewListResponse response = service.getReviews(1L, 2L, null, 20);

        assertThat(response.summary().topTags()).isEmpty();
        assertThat(response.summary().likeReceivedCount()).isZero();
        assertThat(response.summary().reviewCount()).isZero();
        assertThat(response.content()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
        verify(memberReviewTagRepository, never()).findByReviewIdIn(any());
    }

    @Test
    void 탈퇴한_조회자와_없는_대상_회원은_각각_403과_404다() {
        Member withdrawn = activeMember(1L);
        withdrawn.withdraw(Instant.parse("2026-08-03T14:00:00Z"));
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(withdrawn));

        BusinessException withdrawnException = catchThrowableOfType(
                () -> service.getReviews(1L, 2L, null, 20),
                BusinessException.class
        );
        assertThat(withdrawnException.getErrorCode())
                .isEqualTo(ErrorCode.AUTH_MEMBER_WITHDRAWN);
        verifyNoInteractions(
                memberReviewRepository,
                memberReviewTagRepository,
                memberReviewSummaryReader,
                cursorCodec
        );

        Member viewer = activeMember(1L);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(viewer));
        when(memberRepository.findById(2L)).thenReturn(Optional.empty());

        BusinessException notFoundException = catchThrowableOfType(
                () -> service.getReviews(1L, 2L, null, 20),
                BusinessException.class
        );
        assertThat(notFoundException.getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        verifyNoInteractions(
                memberReviewRepository,
                memberReviewTagRepository,
                memberReviewSummaryReader,
                cursorCodec
        );
    }

    private void stubActiveMembers(Long viewerId, Long revieweeId) {
        when(memberRepository.findById(viewerId))
                .thenReturn(Optional.of(activeMember(viewerId)));
        when(memberRepository.findById(revieweeId))
                .thenReturn(Optional.of(activeMember(revieweeId)));
    }

    private Member activeMember(Long id) {
        Member member = new Member(
                "member" + id + "@example.com",
                "hash",
                "회원" + id
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private MemberReview review(
            Long id,
            boolean liked,
            String content,
            String createdAt
    ) {
        MemberReview review = MemberReview.create(10L, 7L, 2L, liked, content);
        ReflectionTestUtils.setField(review, "id", id);
        ReflectionTestUtils.setField(
                review,
                "createdAt",
                Instant.parse(createdAt)
        );
        return review;
    }

    private MemberReviewSummaryResponse summary() {
        return new MemberReviewSummaryResponse(
                List.of(new ReviewTagCountView(
                        "PUNCTUAL",
                        "시간 약속을 잘 지켜요",
                        "⏰",
                        "PERSON",
                        8
                )),
                9L,
                12L
        );
    }
}
