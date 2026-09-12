package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.domain.PostComment;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.community.dto.request.CommentUpdateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.CommentResponse;
import com.ssafy.ssabangpalbang.community.repository.CommentCommandRepository;
import com.ssafy.ssabangpalbang.community.repository.CommentQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostCommentRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostRepository;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentSnapshot;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentUpdateRow;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentUpdateServiceTest {

    private static final Long MEMBER_ID = 12L;
    private static final Long OTHER_MEMBER_ID = 13L;
    private static final Long COMMENT_ID = 36L;
    private static final Long POST_ID = 154L;
    private static final Instant CREATED_AT =
            Instant.parse("2026-07-25T08:15:00Z");
    private static final Instant CLOCK_INSTANT =
            Instant.parse("2026-07-25T08:20:00.123456789Z");
    private static final Instant UPDATED_AT =
            Instant.parse("2026-07-25T08:20:00.123456Z");

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

    private Member member;
    private Post post;
    private CommentSnapshot snapshot;

    @BeforeEach
    void setUp() {
        member = activeMember();
        post = activePost(MEMBER_ID);
        snapshot = new CommentSnapshot(
                COMMENT_ID,
                POST_ID,
                MEMBER_ID,
                null
        );
        lenient().when(memberRepository.findByIdForShare(MEMBER_ID))
                .thenReturn(Optional.of(member));
        lenient().when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.of(snapshot));
        lenient().when(postRepository.findByIdForShare(POST_ID))
                .thenReturn(Optional.of(post));
        lenient().when(commentQueryRepository
                        .findSnapshotForUpdate(COMMENT_ID))
                .thenReturn(Optional.of(snapshot));
        lenient().when(clock.instant()).thenReturn(CLOCK_INSTANT);
        lenient().when(commentCommandRepository
                        .updateContentIfActive(
                                COMMENT_ID,
                                MEMBER_ID,
                                "수정된 댓글",
                                UPDATED_AT
                        ))
                .thenReturn(Optional.of(updateRow("수정된 댓글")));
    }

    @Test
    void updatesTrimmedPlainTextAfterLockAndReturnsContract() {
        when(commentCommandRepository.updateContentIfActive(
                COMMENT_ID,
                MEMBER_ID,
                "<script>alert(1)</script>",
                UPDATED_AT
        )).thenReturn(Optional.of(
                updateRow("<script>alert(1)</script>")
        ));

        CommentResponse response = commentCommandService.update(
                MEMBER_ID,
                COMMENT_ID,
                new CommentUpdateRequest(
                        "\u00A0<script>alert(1)</script>\u200B"
                )
        );

        assertThat(response.commentId()).isEqualTo(COMMENT_ID);
        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.content())
                .isEqualTo("<script>alert(1)</script>");
        assertThat(response.author().memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.author().nickname()).isEqualTo("옥수탐방러");
        assertThat(response.isMine()).isTrue();
        assertThat(response.isPostAuthor()).isTrue();
        assertThat(response.canEdit()).isTrue();
        assertThat(response.canDelete()).isTrue();
        assertThat(response.createdAt().toInstant())
                .isEqualTo(CREATED_AT);
        assertThat(response.updatedAt().toInstant())
                .isEqualTo(UPDATED_AT);

        InOrder order = inOrder(
                memberRepository,
                commentQueryRepository,
                postRepository,
                clock,
                commentCommandRepository
        );
        order.verify(memberRepository).findByIdForShare(MEMBER_ID);
        order.verify(commentQueryRepository).findSnapshot(COMMENT_ID);
        order.verify(postRepository).findByIdForShare(POST_ID);
        order.verify(commentQueryRepository)
                .findSnapshotForUpdate(COMMENT_ID);
        order.verify(clock).instant();
        order.verify(commentCommandRepository)
                .updateContentIfActive(
                        COMMENT_ID,
                        MEMBER_ID,
                        "<script>alert(1)</script>",
                        UPDATED_AT
                );
        verifyNoInteractions(postQueryRepository);
    }

    @Test
    void rejectsInvalidContentBeforeRepositoryAccess() {
        List<String> invalidValues = java.util.Arrays.asList(
                null,
                "",
                " ",
                "\u00A0",
                "\u200B",
                "\uFEFF",
                "a\0b",
                "\uD83D",
                "😀".repeat(1001)
        );

        for (String content : invalidValues) {
            assertError(
                    () -> commentCommandService.update(
                            MEMBER_ID,
                            COMMENT_ID,
                            new CommentUpdateRequest(content)
                    ),
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }

        verifyNoInteractions(
                memberRepository,
                postRepository,
                postCommentRepository,
                postQueryRepository,
                commentCommandRepository,
                commentQueryRepository,
                clock
        );
    }

    @Test
    void rejectsInactiveMemberBeforeCommentLookup() {
        ReflectionTestUtils.setField(
                member,
                "status",
                MemberStatus.WITHDRAWN
        );

        assertError(
                () -> update("수정된 댓글"),
                ErrorCode.MEMBER_NOT_FOUND
        );

        verify(commentQueryRepository, never())
                .findSnapshot(COMMENT_ID);
    }

    @Test
    void classifiesMissingAndDeletedCommentsBeforePost() {
        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.empty());
        assertError(
                () -> update("수정된 댓글"),
                ErrorCode.COMMENT_NOT_FOUND
        );

        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.of(new CommentSnapshot(
                        COMMENT_ID,
                        POST_ID,
                        OTHER_MEMBER_ID,
                        UPDATED_AT
                )));
        assertThatThrownBy(() -> update("수정된 댓글"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode
                                                    .COMMENT_ALREADY_DELETED
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "삭제된 댓글은 수정할 수 없습니다."
                                    );
                            assertThat(exception.getData()).isNull();
                        }
                );

        verify(postRepository, never()).findByIdForShare(POST_ID);
    }

    @Test
    void hidesForeignCommentBehindUnavailablePost() {
        CommentSnapshot foreign = new CommentSnapshot(
                COMMENT_ID,
                POST_ID,
                OTHER_MEMBER_ID,
                null
        );
        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.of(foreign));
        ReflectionTestUtils.setField(
                post,
                "status",
                PostStatus.HIDDEN
        );

        assertError(
                () -> update("수정된 댓글"),
                ErrorCode.POST_NOT_FOUND
        );
        verify(commentQueryRepository, never())
                .findSnapshotForUpdate(COMMENT_ID);
    }

    @Test
    void forbidsForeignCommentAfterPostValidation() {
        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(Optional.of(new CommentSnapshot(
                        COMMENT_ID,
                        POST_ID,
                        OTHER_MEMBER_ID,
                        null
                )));

        assertError(
                () -> update("수정된 댓글"),
                ErrorCode.COMMENT_UPDATE_FORBIDDEN
        );
        verify(commentQueryRepository, never())
                .findSnapshotForUpdate(COMMENT_ID);
    }

    @Test
    void revalidatesDeletedStateAfterAcquiringRowLock() {
        when(commentQueryRepository.findSnapshotForUpdate(COMMENT_ID))
                .thenReturn(Optional.of(new CommentSnapshot(
                        COMMENT_ID,
                        POST_ID,
                        MEMBER_ID,
                        UPDATED_AT
                )));

        assertError(
                () -> update("수정된 댓글"),
                ErrorCode.COMMENT_ALREADY_DELETED
        );
        verifyNoInteractions(clock);
        verify(commentCommandRepository, never())
                .updateContentIfActive(
                        COMMENT_ID,
                        MEMBER_ID,
                        "수정된 댓글",
                        UPDATED_AT
                );
    }

    @Test
    void reclassifiesZeroRowUpdateAsConcurrentDeletion() {
        CommentSnapshot deleted = new CommentSnapshot(
                COMMENT_ID,
                POST_ID,
                MEMBER_ID,
                UPDATED_AT
        );
        when(commentQueryRepository.findSnapshot(COMMENT_ID))
                .thenReturn(
                        Optional.of(snapshot),
                        Optional.of(deleted)
                );
        when(commentCommandRepository.updateContentIfActive(
                COMMENT_ID,
                MEMBER_ID,
                "수정된 댓글",
                UPDATED_AT
        )).thenReturn(Optional.empty());

        assertError(
                () -> update("수정된 댓글"),
                ErrorCode.COMMENT_ALREADY_DELETED
        );
    }

    @Test
    void rejectsUnexpectedImmutableReferenceChange() {
        when(commentQueryRepository.findSnapshotForUpdate(COMMENT_ID))
                .thenReturn(Optional.of(new CommentSnapshot(
                        COMMENT_ID,
                        POST_ID + 1,
                        MEMBER_ID,
                        null
                )));

        assertThatThrownBy(() -> update("수정된 댓글"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("댓글의 불변 참조 정보가 변경되었습니다.");
        verifyNoInteractions(clock);
    }

    @Test
    void acceptsExactlyOneThousandUnicodeCodePoints() {
        String content = "😀".repeat(1000);
        when(commentCommandRepository.updateContentIfActive(
                COMMENT_ID,
                MEMBER_ID,
                content,
                UPDATED_AT
        )).thenReturn(Optional.of(updateRow(content)));

        CommentResponse response = update(content);

        assertThat(response.content()).isEqualTo(content);
    }

    private CommentResponse update(String content) {
        return commentCommandService.update(
                MEMBER_ID,
                COMMENT_ID,
                new CommentUpdateRequest(content)
        );
    }

    private CommentUpdateRow updateRow(String content) {
        return new CommentUpdateRow(
                COMMENT_ID,
                POST_ID,
                MEMBER_ID,
                content,
                CREATED_AT,
                UPDATED_AT
        );
    }

    private void assertError(
            Runnable action,
            ErrorCode expected
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(expected)
                );
    }

    private Member activeMember() {
        Member result = new Member(
                "comment-update@example.com",
                "encoded-password",
                "옥수탐방러"
        );
        ReflectionTestUtils.setField(result, "id", MEMBER_ID);
        ReflectionTestUtils.setField(
                result,
                "selectedCharacterId",
                "DURI"
        );
        return result;
    }

    private Post activePost(Long authorId) {
        Post result = Post.createMemberPost(
                authorId,
                BoardType.FREE,
                "제목",
                "본문",
                null
        );
        ReflectionTestUtils.setField(result, "id", POST_ID);
        ReflectionTestUtils.setField(result, "viewCount", 11L);
        return result;
    }
}
