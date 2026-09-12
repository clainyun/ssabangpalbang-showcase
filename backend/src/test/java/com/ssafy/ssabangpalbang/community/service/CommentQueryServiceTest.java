package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.community.dto.request.CommentListCondition;
import com.ssafy.ssabangpalbang.community.dto.response.CommentListResponse;
import com.ssafy.ssabangpalbang.community.repository.CommentQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentCursorRow;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentListRow;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentPostContextRow;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentQueryServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CommentQueryRepository commentQueryRepository;

    private CommentQueryService commentQueryService;

    @BeforeEach
    void setUp() {
        commentQueryService = new CommentQueryService(
                memberRepository,
                commentQueryRepository
        );
    }

    @Test
    void appliesDefaultsAndBuildsCursorFromLastReturnedComment() {
        stubActiveMember(12L);
        CommentPostContextRow post =
                new CommentPostContextRow(154L, 7L);
        when(commentQueryRepository.findVisiblePostContext(154L))
                .thenReturn(Optional.of(post));
        List<CommentListRow> fetched = LongStream
                .rangeClosed(1, 21)
                .mapToObj(id -> row(
                        id,
                        12L,
                        MemberStatus.ACTIVE,
                        null
                ))
                .toList();
        when(commentQueryRepository.findComments(154L, null, 21))
                .thenReturn(fetched);
        when(commentQueryRepository.countActiveComments(154L))
                .thenReturn(25L);

        CommentListResponse response =
                commentQueryService.getComments(
                        12L,
                        154L,
                        new CommentListCondition(null, null)
                );

        assertThat(response.content()).hasSize(20);
        assertThat(response.nextCursor()).isEqualTo(20L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.totalCount()).isEqualTo(25L);
        assertThat(response.content().get(0).isMine()).isTrue();
        assertThat(response.content().get(0).canEdit()).isTrue();
        assertThat(response.content().get(0).canDelete()).isTrue();
    }

    @Test
    void usesCursorBoundaryAndReturnsEmptyLastPage() {
        stubActiveMember(12L);
        CommentCursorRow cursor = new CommentCursorRow(
                36L,
                Instant.parse("2026-07-25T07:42:00Z")
        );
        when(commentQueryRepository.findVisiblePostContext(154L))
                .thenReturn(Optional.of(
                        new CommentPostContextRow(154L, 7L)
                ));
        when(commentQueryRepository.findCursor(154L, 36L))
                .thenReturn(Optional.of(cursor));
        when(commentQueryRepository.findComments(154L, cursor, 11))
                .thenReturn(List.of());
        when(commentQueryRepository.countActiveComments(154L))
                .thenReturn(2L);

        CommentListResponse response =
                commentQueryService.getComments(
                        12L,
                        154L,
                        new CommentListCondition(36L, 10)
                );

        assertThat(response.content()).isEmpty();
        assertThat(response.totalCount()).isEqualTo(2L);
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void anonymizesWithdrawnAuthorAndCalculatesPostAuthorFlag() {
        stubActiveMember(12L);
        when(commentQueryRepository.findVisiblePostContext(154L))
                .thenReturn(Optional.of(
                        new CommentPostContextRow(154L, 7L)
                ));
        CommentListRow postAuthor = row(
                36L,
                7L,
                MemberStatus.ACTIVE,
                null
        );
        CommentListRow withdrawn = row(
                37L,
                9L,
                MemberStatus.WITHDRAWN,
                Instant.parse("2026-07-25T07:00:00Z")
        );
        when(commentQueryRepository.findComments(154L, null, 21))
                .thenReturn(List.of(postAuthor, withdrawn));
        when(commentQueryRepository.countActiveComments(154L))
                .thenReturn(2L);

        CommentListResponse response =
                commentQueryService.getComments(
                        12L,
                        154L,
                        new CommentListCondition(null, 20)
                );

        assertThat(response.content().get(0).isPostAuthor()).isTrue();
        assertThat(response.content().get(0).isMine()).isFalse();
        assertThat(response.content().get(1).author().memberId())
                .isNull();
        assertThat(response.content().get(1).author().nickname())
                .isEqualTo("탈퇴한 회원");
        assertThat(response.content().get(1).author()
                .selectedCharacterId()).isEqualTo("PALBANG");
        assertThat(response.content().get(1).canEdit()).isFalse();
        assertThat(response.content().get(1).canDelete()).isFalse();
    }

    @Test
    void keepsPostAuthorFlagWhenWithdrawnAuthorIsAnonymized() {
        stubActiveMember(12L);
        when(commentQueryRepository.findVisiblePostContext(154L))
                .thenReturn(Optional.of(
                        new CommentPostContextRow(154L, 7L)
                ));
        when(commentQueryRepository.findComments(154L, null, 21))
                .thenReturn(List.of(row(
                        36L,
                        7L,
                        MemberStatus.WITHDRAWN,
                        Instant.parse("2026-07-25T07:00:00Z")
                )));
        when(commentQueryRepository.countActiveComments(154L))
                .thenReturn(1L);

        CommentListResponse response =
                commentQueryService.getComments(
                        12L,
                        154L,
                        new CommentListCondition(null, 20)
                );

        assertThat(response.content().get(0).author().memberId())
                .isNull();
        assertThat(response.content().get(0).isPostAuthor()).isTrue();
        assertThat(response.content().get(0).isMine()).isFalse();
        assertThat(response.content().get(0).canEdit()).isFalse();
        assertThat(response.content().get(0).canDelete()).isFalse();
    }

    @Test
    void rejectsCursorThatDoesNotBelongToPost() {
        stubActiveMember(12L);
        when(commentQueryRepository.findVisiblePostContext(154L))
                .thenReturn(Optional.of(
                        new CommentPostContextRow(154L, 7L)
                ));
        when(commentQueryRepository.findCursor(154L, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                commentQueryService.getComments(
                        12L,
                        154L,
                        new CommentListCondition(999L, 20)
                ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.INVALID_INPUT_VALUE
                                    );
                            assertThat(exception.getData())
                                    .isEqualTo(java.util.Map.of(
                                            "field",
                                            "cursor",
                                            "reason",
                                            "해당 게시글의 댓글 커서를 확인해 주세요."
                                    ));
                        }
                );

        verify(commentQueryRepository, never())
                .findComments(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt()
                );
    }

    @Test
    void rejectsInactiveMemberBeforePostLookup() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.WITHDRAWN);
        when(memberRepository.findById(12L))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() ->
                commentQueryService.getComments(
                        12L,
                        154L,
                        new CommentListCondition(null, 20)
                ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(commentQueryRepository);
    }

    private void stubActiveMember(Long memberId) {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getDeletedAt()).thenReturn(null);
        when(memberRepository.findById(memberId))
                .thenReturn(Optional.of(member));
    }

    private CommentListRow row(
            Long commentId,
            Long authorId,
            MemberStatus status,
            Instant authorDeletedAt
    ) {
        Instant createdAt = Instant.parse(
                "2026-07-25T07:42:00Z"
        ).plusSeconds(commentId);
        return new CommentListRow(
                commentId,
                "댓글 " + commentId,
                authorId,
                "작성자 " + authorId,
                null,
                "DURI",
                status,
                authorDeletedAt,
                createdAt,
                createdAt
        );
    }
}
