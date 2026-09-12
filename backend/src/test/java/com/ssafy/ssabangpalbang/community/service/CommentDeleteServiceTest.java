package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.community.dto.response.CommentDeleteResponse;
import com.ssafy.ssabangpalbang.community.repository.CommentCommandRepository;
import com.ssafy.ssabangpalbang.community.repository.CommentQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostCommentRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostRepository;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentPostMetricsRow;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentSnapshot;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentDeleteServiceTest {

    private static final Long MEMBER_ID = 12L;
    private static final Long OTHER_MEMBER_ID = 13L;
    private static final Long COMMENT_ID = 36L;
    private static final Long POST_ID = 154L;
    private static final Instant CLOCK_INSTANT =
            Instant.parse("2026-07-25T08:25:00.123456789Z");
    private static final Instant DELETED_AT =
            Instant.parse("2026-07-25T08:25:00.123456Z");

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private PostCommentRepository postCommentRepository;
    @Mock
    private PostQueryRepository postQueryRepository;
    @Mock
    private CommentCommandRepository commentCommandRepository;
    @Mock
    private CommentQueryRepository commentQueryRepository;
    @Mock
    private Clock clock;

    @InjectMocks
    private CommentCommandService commentCommandService;

    @BeforeEach
    void setUp() {
        when(memberRepository.findByIdForShare(MEMBER_ID))
                .thenReturn(Optional.of(activeMember()));
    }

    @Test
    void softDeletesOwnedCommentAndReturnsCurrentMetrics() {
        CommentSnapshot snapshot = activeSnapshot(MEMBER_ID);
        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.of(snapshot));
        when(clock.instant()).thenReturn(CLOCK_INSTANT);
        when(commentCommandRepository.softDelete(
                COMMENT_ID,
                MEMBER_ID,
                DELETED_AT
        )).thenReturn(1);
        when(commentQueryRepository.findPostMetrics(POST_ID))
                .thenReturn(Optional.of(new CommentPostMetricsRow(
                        true,
                        0L,
                        2L,
                        11L,
                        false,
                        new BigDecimal("17.70")
                )));

        CommentDeleteResponse response = commentCommandService.delete(
                MEMBER_ID,
                COMMENT_ID
        );

        assertThat(response.commentId()).isEqualTo(COMMENT_ID);
        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.deletedAt().toInstant())
                .isEqualTo(DELETED_AT);
        assertThat(response.postAvailable()).isTrue();
        assertThat(response.postMetrics().commentCount()).isZero();
        assertThat(response.postMetrics().likeCount()).isEqualTo(2L);
        assertThat(response.postMetrics().viewCount()).isEqualTo(11L);
        assertThat(response.postMetrics().isHot()).isFalse();
        assertThat(response.postMetrics().hotScore())
                .isEqualByComparingTo("17.70");

        InOrder order = inOrder(
                memberRepository,
                commentQueryRepository,
                commentCommandRepository
        );
        order.verify(memberRepository).findByIdForShare(MEMBER_ID);
        order.verify(commentQueryRepository)
                .findSnapshot(COMMENT_ID);
        order.verify(commentCommandRepository).softDelete(
                COMMENT_ID,
                MEMBER_ID,
                DELETED_AT
        );
        order.verify(commentQueryRepository)
                .findPostMetrics(POST_ID);
        verify(clock).instant();
        verifyNoInteractions(
                postRepository,
                postCommentRepository,
                postQueryRepository
        );
    }

    @Test
    void allowsDeletionWhenOriginalPostIsUnavailable() {
        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.of(activeSnapshot(MEMBER_ID)));
        when(clock.instant()).thenReturn(CLOCK_INSTANT);
        when(commentCommandRepository.softDelete(
                COMMENT_ID,
                MEMBER_ID,
                DELETED_AT
        )).thenReturn(1);
        when(commentQueryRepository.findPostMetrics(POST_ID))
                .thenReturn(Optional.of(new CommentPostMetricsRow(
                        false,
                        0L,
                        0L,
                        3L,
                        null,
                        null
                )));

        CommentDeleteResponse response = commentCommandService.delete(
                MEMBER_ID,
                COMMENT_ID
        );

        assertThat(response.postAvailable()).isFalse();
        assertThat(response.postMetrics().isHot()).isFalse();
        assertThat(response.postMetrics().hotScore()).isNull();
    }

    @Test
    void rejectsMissingCommentBeforeDeletion() {
        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.empty());

        assertError(
                () -> commentCommandService.delete(
                        MEMBER_ID,
                        COMMENT_ID
                ),
                ErrorCode.COMMENT_NOT_FOUND
        );

        verifyNoInteractions(commentCommandRepository, clock);
    }

    @Test
    void rejectsAlreadyDeletedCommentBeforeOwnershipCheck() {
        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.of(new CommentSnapshot(
                        COMMENT_ID,
                        POST_ID,
                        OTHER_MEMBER_ID,
                        DELETED_AT
                )));

        assertThatThrownBy(() -> commentCommandService.delete(
                MEMBER_ID,
                COMMENT_ID
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(
                                    ErrorCode.COMMENT_ALREADY_DELETED
                            );
                    assertThat(exception.getData())
                            .containsEntry("commentId", COMMENT_ID);
                }
        );

        verifyNoInteractions(commentCommandRepository, clock);
    }

    @Test
    void rejectsCommentOwnedByAnotherMember() {
        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.of(activeSnapshot(
                        OTHER_MEMBER_ID
                )));

        assertError(
                () -> commentCommandService.delete(
                        MEMBER_ID,
                        COMMENT_ID
                ),
                ErrorCode.COMMENT_DELETE_FORBIDDEN
        );

        verifyNoInteractions(commentCommandRepository, clock);
    }

    @Test
    void reclassifiesConcurrentDeletionAsAlreadyDeleted() {
        CommentSnapshot initial = activeSnapshot(MEMBER_ID);
        CommentSnapshot deleted = new CommentSnapshot(
                COMMENT_ID,
                POST_ID,
                MEMBER_ID,
                DELETED_AT.minusSeconds(1)
        );
        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.of(initial))
                .thenReturn(Optional.of(deleted));
        when(clock.instant()).thenReturn(CLOCK_INSTANT);
        when(commentCommandRepository.softDelete(
                COMMENT_ID,
                MEMBER_ID,
                DELETED_AT
        )).thenReturn(0);

        assertError(
                () -> commentCommandService.delete(
                        MEMBER_ID,
                        COMMENT_ID
                ),
                ErrorCode.COMMENT_ALREADY_DELETED
        );

        verify(commentQueryRepository, never())
                .findPostMetrics(POST_ID);
    }

    @Test
    void rejectsInactiveMemberBeforeCommentLookup() {
        Member inactiveMember = activeMember();
        ReflectionTestUtils.setField(
                inactiveMember,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findByIdForShare(MEMBER_ID))
                .thenReturn(Optional.of(inactiveMember));

        assertError(
                () -> commentCommandService.delete(
                        MEMBER_ID,
                        COMMENT_ID
                ),
                ErrorCode.MEMBER_NOT_FOUND
        );

        verifyNoInteractions(
                commentQueryRepository,
                commentCommandRepository,
                clock
        );
    }

    @Test
    void failsWhenMetricsCannotBeReadAfterDeletion() {
        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.of(activeSnapshot(MEMBER_ID)));
        when(clock.instant()).thenReturn(CLOCK_INSTANT);
        when(commentCommandRepository.softDelete(
                COMMENT_ID,
                MEMBER_ID,
                DELETED_AT
        )).thenReturn(1);
        when(commentQueryRepository.findPostMetrics(POST_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentCommandService.delete(
                MEMBER_ID,
                COMMENT_ID
        )).isInstanceOf(IllegalStateException.class);
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(errorCode)
                );
    }

    private CommentSnapshot activeSnapshot(Long authorId) {
        return new CommentSnapshot(
                COMMENT_ID,
                POST_ID,
                authorId,
                null
        );
    }

    private Member activeMember() {
        Member member = new Member(
                "comment-delete@example.com",
                "encoded-password",
                "댓글삭제회원"
        );
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }
}
