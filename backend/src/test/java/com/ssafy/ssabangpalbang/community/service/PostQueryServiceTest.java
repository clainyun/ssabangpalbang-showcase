package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.community.dto.request.PostListCondition;
import com.ssafy.ssabangpalbang.community.dto.request.PostSort;
import com.ssafy.ssabangpalbang.community.dto.response.PostDetailResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostListItemResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostListResponse;
import com.ssafy.ssabangpalbang.community.repository.PostAttachmentRepository;
import com.ssafy.ssabangpalbang.community.repository.PostCommandRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.projection.PostDetailRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostListKeyRow;
import com.ssafy.ssabangpalbang.community.support.PostCursor;
import com.ssafy.ssabangpalbang.community.support.PostCursorCodec;
import com.ssafy.ssabangpalbang.community.support.PostResponseAssembler;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.MediaFileQueryPort;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostQueryServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PostCommandRepository postCommandRepository;

    @Mock
    private PostQueryRepository postQueryRepository;

    @Mock
    private PostAttachmentRepository postAttachmentRepository;

    @Mock
    private MediaFileQueryPort mediaFileQueryPort;

    @Mock
    private PostResponseAssembler postResponseAssembler;

    @Mock
    private PostCursorCodec postCursorCodec;

    private PostQueryService postQueryService;

    @BeforeEach
    void setUp() {
        postQueryService = new PostQueryService(
                memberRepository,
                postCommandRepository,
                postQueryRepository,
                postAttachmentRepository,
                mediaFileQueryPort,
                postResponseAssembler,
                postCursorCodec
        );
    }

    @Test
    void appliesDefaultsAndBuildsNextCursorFromLastReturnedItem() {
        Member member = activeMember();
        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member));
        when(postCursorCodec.filterHash(
                null,
                PostSort.LATEST,
                null
        )).thenReturn("filter-hash");
        Instant createdAt = Instant.parse("2026-07-29T07:30:00Z");
        List<PostListKeyRow> fetchedKeys = IntStream.range(0, 21)
                .mapToObj(index -> new PostListKeyRow(
                        200L - index,
                        createdAt.minusSeconds(index),
                        null
                ))
                .toList();
        when(postQueryRepository.findListKeys(
                null,
                PostSort.LATEST,
                null,
                null,
                21
        )).thenReturn(fetchedKeys);
        List<PostListKeyRow> pageKeys =
                fetchedKeys.subList(0, 20);
        List<Long> postIds = pageKeys.stream()
                .map(PostListKeyRow::postId)
                .toList();
        when(postQueryRepository.findListCards(postIds))
                .thenReturn(List.of());
        when(postQueryRepository.findReactionCounts(postIds, 7L))
                .thenReturn(List.of());
        when(postAttachmentRepository
                .findAllByPostIdInOrderByPostIdAscDisplayOrderAscIdAsc(
                        postIds
                )).thenReturn(List.of());
        PostListItemResponse item =
                mock(PostListItemResponse.class);
        when(item.postId()).thenReturn(181L);
        when(postResponseAssembler.assembleList(
                pageKeys,
                List.of(),
                7L,
                List.of(),
                List.of(),
                List.of()
        )).thenReturn(List.of(item));
        when(postCursorCodec.encode(org.mockito.ArgumentMatchers.any(
                PostCursor.class
        ))).thenReturn("next-cursor");

        PostListResponse response = postQueryService.getPosts(
                7L,
                new PostListCondition(
                        null,
                        null,
                        "\u3000\u00a0",
                        null,
                        null
                )
        );

        assertThat(response.boardType()).isNull();
        assertThat(response.sort()).isEqualTo("LATEST");
        assertThat(response.keyword()).isNull();
        assertThat(response.pageInfo().size()).isEqualTo(20);
        assertThat(response.pageInfo().hasNext()).isTrue();
        assertThat(response.pageInfo().nextCursor())
                .isEqualTo("next-cursor");
        ArgumentCaptor<PostCursor> cursorCaptor =
                ArgumentCaptor.forClass(PostCursor.class);
        verify(postCursorCodec).encode(cursorCaptor.capture());
        assertThat(cursorCaptor.getValue().postId()).isEqualTo(181L);
        assertThat(cursorCaptor.getValue().filterHash())
                .isEqualTo("filter-hash");
        verify(mediaFileQueryPort, never())
                .findAllById(org.mockito.ArgumentMatchers.anyList());
        verifyNoInteractions(postCommandRepository);
    }

    @Test
    void returnsSuccessfulEmptyPageWithoutCursor() {
        Member member = activeMember();
        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member));
        when(postCursorCodec.filterHash(
                BoardType.FREE,
                PostSort.LATEST,
                "없는 검색어"
        )).thenReturn("filter-hash");
        when(postQueryRepository.findListKeys(
                BoardType.FREE,
                PostSort.LATEST,
                "없는 검색어",
                null,
                21
        )).thenReturn(List.of());
        when(postQueryRepository.findListCards(List.of()))
                .thenReturn(List.of());
        when(postQueryRepository.findReactionCounts(List.of(), 7L))
                .thenReturn(List.of());
        when(postResponseAssembler.assembleList(
                List.of(),
                List.of(),
                7L,
                List.of(),
                List.of(),
                List.of()
        )).thenReturn(List.of());

        PostListResponse response = postQueryService.getPosts(
                7L,
                new PostListCondition(
                        "FREE",
                        "LATEST",
                        "  없는 검색어  ",
                        null,
                        20
                )
        );

        assertThat(response.content()).isEmpty();
        assertThat(response.pageInfo().hasNext()).isFalse();
        assertThat(response.pageInfo().nextCursor()).isNull();
        verify(postCursorCodec, never()).encode(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsInvalidListConditionsBeforeQuerying() {
        Member member = activeMember();
        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> postQueryService.getPosts(
                7L,
                new PostListCondition(
                        "INFO",
                        "LATEST",
                        null,
                        null,
                        20
                )
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.POST_BOARD_TYPE_INVALID)
        );
        assertThatThrownBy(() -> postQueryService.getPosts(
                7L,
                new PostListCondition(
                        null,
                        "latest",
                        null,
                        null,
                        20
                )
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.POST_SORT_INVALID)
        );
        assertThatThrownBy(() -> postQueryService.getPosts(
                7L,
                new PostListCondition(
                        null,
                        "LATEST",
                        "가".repeat(101),
                        null,
                        20
                )
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
        );

        verifyNoInteractions(postQueryRepository);
    }

    @Test
    void incrementsBeforeLoadingAndReturnsAssembledDetail() {
        Member member = activeMember();
        PostDetailRow row = detailRow();
        PostInteractionRow interactions = new PostInteractionRow(
                2L,
                1L,
                true,
                null,
                null,
                null
        );
        PostDetailResponse expected = mock(PostDetailResponse.class);

        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member));
        when(postCommandRepository.incrementViewCountIfVisible(154L))
                .thenReturn(OptionalLong.of(25L));
        when(postQueryRepository.findVisibleDetail(154L))
                .thenReturn(Optional.of(row));
        when(postQueryRepository.findInteractions(154L, 7L))
                .thenReturn(Optional.of(interactions));
        when(postAttachmentRepository
                .findAllByPostIdOrderByDisplayOrderAscIdAsc(154L))
                .thenReturn(List.of());
        when(mediaFileQueryPort.findAllById(List.of()))
                .thenReturn(List.of());
        when(postResponseAssembler.assembleDetail(
                row,
                7L,
                25L,
                interactions,
                List.of(),
                List.of()
        )).thenReturn(expected);

        PostDetailResponse actual =
                postQueryService.getDetail(7L, 154L);

        assertThat(actual).isSameAs(expected);
        InOrder order = inOrder(
                memberRepository,
                postCommandRepository,
                postQueryRepository,
                postResponseAssembler
        );
        order.verify(memberRepository).findById(7L);
        order.verify(postCommandRepository)
                .incrementViewCountIfVisible(154L);
        order.verify(postQueryRepository).findVisibleDetail(154L);
        order.verify(postQueryRepository)
                .findInteractions(154L, 7L);
        order.verify(postResponseAssembler).assembleDetail(
                row,
                7L,
                25L,
                interactions,
                List.of(),
                List.of()
        );
    }

    @Test
    void rejectsInactiveMemberBeforeIncrement() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.WITHDRAWN);
        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() ->
                postQueryService.getDetail(7L, 154L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(
                postCommandRepository,
                postQueryRepository,
                postResponseAssembler
        );
    }

    @Test
    void mapsInvisiblePostToPostNotFoundWithoutReadingOriginal() {
        Member member = activeMember();
        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member));
        when(postCommandRepository.incrementViewCountIfVisible(154L))
                .thenReturn(OptionalLong.empty());

        assertThatThrownBy(() ->
                postQueryService.getDetail(7L, 154L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);

        verifyNoInteractions(
                postQueryRepository,
                postResponseAssembler
        );
    }

    private Member activeMember() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getDeletedAt()).thenReturn(null);
        return member;
    }

    private PostDetailRow detailRow() {
        Instant createdAt = Instant.parse("2026-07-29T07:30:00Z");
        return new PostDetailRow(
                154L,
                BoardType.INFORMATION,
                "제목",
                "본문",
                PostStatus.ACTIVE,
                false,
                7L,
                "집보는다람쥐",
                null,
                "JIPKONG",
                MemberStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt
        );
    }
}
