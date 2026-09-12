package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.domain.PostComment;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.community.dto.request.CommentCreateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.CommentCreateResponse;
import com.ssafy.ssabangpalbang.community.repository.CommentCommandRepository;
import com.ssafy.ssabangpalbang.community.repository.CommentQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostCommentRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostRepository;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentCommandServiceTest {

    private static final Long MEMBER_ID = 12L;
    private static final Long POST_ID = 154L;
    private static final Instant CREATED_AT =
            Instant.parse("2026-07-25T08:15:00Z");

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

    @BeforeEach
    void setUp() {
        member = activeMember();
        post = activePost(MEMBER_ID);
        lenient().when(memberRepository.findByIdForShare(MEMBER_ID))
                .thenReturn(Optional.of(member));
        lenient().when(postRepository.findByIdForShare(POST_ID))
                .thenReturn(Optional.of(post));
    }

    @Test
    void createsTrimmedPlainTextCommentAndReturnsCurrentMetrics() {
        stubSavedComment();
        when(postQueryRepository.findInteractions(POST_ID, MEMBER_ID))
                .thenReturn(Optional.of(new PostInteractionRow(
                        4L,
                        6L,
                        false,
                        new BigDecimal("24.30"),
                        true,
                        2L
                )));

        CommentCreateResponse response = commentCommandService.create(
                MEMBER_ID,
                POST_ID,
                new CommentCreateRequest(
                        "\u00A0<script>alert(1)</script>\u200B"
                )
        );

        ArgumentCaptor<PostComment> captor =
                ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).save(captor.capture());
        PostComment saved = captor.getValue();
        assertThat(saved.getPostId()).isEqualTo(POST_ID);
        assertThat(saved.getAuthorId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getContent())
                .isEqualTo("<script>alert(1)</script>");
        assertThat(saved.getDeletedAt()).isNull();

        assertThat(response.comment().commentId()).isEqualTo(36L);
        assertThat(response.comment().isMine()).isTrue();
        assertThat(response.comment().isPostAuthor()).isTrue();
        assertThat(response.comment().canEdit()).isTrue();
        assertThat(response.comment().canDelete()).isTrue();
        assertThat(response.comment().author().memberId())
                .isEqualTo(MEMBER_ID);
        assertThat(response.comment().author().nickname())
                .isEqualTo("옥수탐방러");
        assertThat(response.comment().createdAt().toInstant())
                .isEqualTo(CREATED_AT);
        assertThat(response.comment().createdAt().getOffset())
                .isEqualTo(ZoneId.of("Asia/Seoul")
                        .getRules()
                        .getOffset(CREATED_AT));
        assertThat(response.postMetrics().commentCount())
                .isEqualTo(6L);
        assertThat(response.postMetrics().likeCount())
                .isEqualTo(4L);
        assertThat(response.postMetrics().viewCount())
                .isEqualTo(11L);
        assertThat(response.postMetrics().isHot()).isTrue();
        assertThat(response.postMetrics().hotScore())
                .isEqualByComparingTo("24.30");

        InOrder order = inOrder(
                postCommentRepository,
                postQueryRepository
        );
        order.verify(postCommentRepository).save(any());
        order.verify(postCommentRepository).flush();
        order.verify(postQueryRepository)
                .findInteractions(POST_ID, MEMBER_ID);
    }

    @Test
    void acceptsOneThousandUnicodeCodePoints() {
        String content = "😀".repeat(1000);
        stubSavedComment();
        when(postQueryRepository.findInteractions(POST_ID, MEMBER_ID))
                .thenReturn(Optional.of(new PostInteractionRow(
                        0L,
                        1L,
                        false,
                        null,
                        null,
                        null
                )));

        CommentCreateResponse response = commentCommandService.create(
                MEMBER_ID,
                POST_ID,
                new CommentCreateRequest(content)
        );

        assertThat(response.comment().content()).isEqualTo(content);
        assertThat(response.postMetrics().isHot()).isFalse();
        assertThat(response.postMetrics().hotScore()).isNull();
    }

    @Test
    void rejectsVisuallyBlankNullAndOverlongContent() {
        List<String> invalidValues = java.util.Arrays.asList(
                null,
                "",
                " ",
                "\u00A0",
                "\u200B",
                "\uFEFF",
                "\u0085",
                "\u200C",
                "\u200D",
                "\u2060",
                "\uD800",
                "\uDC00",
                "a\0b",
                "😀".repeat(1001)
        );

        for (String content : invalidValues) {
            assertThatThrownBy(() -> commentCommandService.create(
                    MEMBER_ID,
                    POST_ID,
                    new CommentCreateRequest(content)
            )).isInstanceOfSatisfying(
                    BusinessException.class,
                    exception -> {
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.INVALID_INPUT_VALUE
                                );
                        assertThat(exception.getData())
                                .containsEntry("field", "content")
                                .containsEntry(
                                        "reason",
                                        CommentCreateRequest
                                                .CONTENT_INVALID_REASON
                                );
                    }
            );
        }

        verifyNoInteractions(
                memberRepository,
                postRepository,
                postCommentRepository,
                postQueryRepository
        );
    }

    @Test
    void allowsActiveAutoReportAndMarksItAsNotPostAuthor() {
        ReflectionTestUtils.setField(post, "authorId", null);
        ReflectionTestUtils.setField(post, "autoReport", true);
        ReflectionTestUtils.setField(post, "reportId", 81L);
        stubSavedComment();
        when(postQueryRepository.findInteractions(POST_ID, MEMBER_ID))
                .thenReturn(Optional.of(new PostInteractionRow(
                        0L,
                        1L,
                        false,
                        null,
                        null,
                        null
                )));

        CommentCreateResponse response = commentCommandService.create(
                MEMBER_ID,
                POST_ID,
                new CommentCreateRequest("자동 리포트 댓글")
        );

        assertThat(response.comment().isPostAuthor()).isFalse();
    }

    @Test
    void rejectsInactiveMemberBeforePostLookup() {
        ReflectionTestUtils.setField(
                member,
                "status",
                MemberStatus.WITHDRAWN
        );

        assertError(
                () -> commentCommandService.create(
                        MEMBER_ID,
                        POST_ID,
                        new CommentCreateRequest("댓글")
                ),
                ErrorCode.MEMBER_NOT_FOUND
        );
        verify(postRepository, never()).findByIdForShare(any());
        verifyNoInteractions(
                postCommentRepository,
                postQueryRepository
        );
    }

    @Test
    void rejectsDeletedMemberBeforePostLookup() {
        ReflectionTestUtils.setField(
                member,
                "deletedAt",
                CREATED_AT
        );

        assertError(
                () -> commentCommandService.create(
                        MEMBER_ID,
                        POST_ID,
                        new CommentCreateRequest("댓글")
                ),
                ErrorCode.MEMBER_NOT_FOUND
        );
        verify(postRepository, never()).findByIdForShare(any());
        verifyNoInteractions(
                postCommentRepository,
                postQueryRepository
        );
    }

    @Test
    void hidesMissingHiddenAndDeletedPostsBehindPostNotFound() {
        when(postRepository.findByIdForShare(POST_ID))
                .thenReturn(Optional.empty());
        assertPostNotFound();

        ReflectionTestUtils.setField(post, "status", PostStatus.HIDDEN);
        when(postRepository.findByIdForShare(POST_ID))
                .thenReturn(Optional.of(post));
        assertPostNotFound();

        ReflectionTestUtils.setField(post, "status", PostStatus.ACTIVE);
        ReflectionTestUtils.setField(
                post,
                "deletedAt",
                CREATED_AT
        );
        assertPostNotFound();

        verifyNoInteractions(
                postCommentRepository,
                postQueryRepository
        );
    }

    private void assertPostNotFound() {
        assertError(
                () -> commentCommandService.create(
                        MEMBER_ID,
                        POST_ID,
                        new CommentCreateRequest("댓글")
                ),
                ErrorCode.POST_NOT_FOUND
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

    private void stubSavedComment() {
        when(postCommentRepository.save(any(PostComment.class)))
                .thenAnswer(invocation -> {
                    PostComment comment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(comment, "id", 36L);
                    ReflectionTestUtils.setField(
                            comment,
                            "createdAt",
                            CREATED_AT
                    );
                    ReflectionTestUtils.setField(
                            comment,
                            "updatedAt",
                            CREATED_AT
                    );
                    return comment;
                });
    }

    private Member activeMember() {
        Member result = new Member(
                "comment@example.com",
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
