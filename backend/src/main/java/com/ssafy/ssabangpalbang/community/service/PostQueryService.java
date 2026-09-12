package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.community.domain.PostAttachment;
import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.dto.request.PostListCondition;
import com.ssafy.ssabangpalbang.community.dto.request.PostSort;
import com.ssafy.ssabangpalbang.community.dto.response.CursorPageInfoResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostDetailResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostListItemResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostListResponse;
import com.ssafy.ssabangpalbang.community.repository.PostAttachmentRepository;
import com.ssafy.ssabangpalbang.community.repository.PostCommandRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.projection.PostDetailRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostListCardRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostListKeyRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostReactionCountRow;
import com.ssafy.ssabangpalbang.community.support.PostCursor;
import com.ssafy.ssabangpalbang.community.support.PostCursorCodec;
import com.ssafy.ssabangpalbang.community.support.PostResponseAssembler;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.MediaFileQueryPort;
import com.ssafy.ssabangpalbang.media.service.MediaFileSnapshot;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

@Service
@RequiredArgsConstructor
public class PostQueryService {

    private final MemberRepository memberRepository;
    private final PostCommandRepository postCommandRepository;
    private final PostQueryRepository postQueryRepository;
    private final PostAttachmentRepository postAttachmentRepository;
    private final MediaFileQueryPort mediaFileQueryPort;
    private final PostResponseAssembler postResponseAssembler;
    private final PostCursorCodec postCursorCodec;

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public PostListResponse getPosts(
            Long memberId,
            PostListCondition condition
    ) {
        validateActiveMember(memberId);

        PostListCondition normalized = condition.normalize();
        validateListRequest(normalized);
        BoardType boardType = parseBoardType(normalized.boardType());
        PostSort sort = PostSort.parse(normalized.sort());
        String filterHash = postCursorCodec.filterHash(
                boardType,
                sort,
                normalized.keyword()
        );
        PostCursor cursor = normalized.cursor() == null
                ? null
                : postCursorCodec.decode(
                        normalized.cursor(),
                        sort,
                        filterHash
                );

        List<PostListKeyRow> fetchedKeys =
                postQueryRepository.findListKeys(
                        boardType,
                        sort,
                        normalized.keyword(),
                        cursor,
                        normalized.size() + 1
                );
        boolean hasNext = fetchedKeys.size() > normalized.size();
        List<PostListKeyRow> pageKeys = hasNext
                ? List.copyOf(fetchedKeys.subList(
                        0,
                        normalized.size()
                ))
                : List.copyOf(fetchedKeys);
        List<Long> postIds = pageKeys.stream()
                .map(PostListKeyRow::postId)
                .toList();

        List<PostListCardRow> rows =
                postQueryRepository.findListCards(postIds);
        List<PostReactionCountRow> reactions =
                postQueryRepository.findReactionCounts(postIds, memberId);
        List<PostAttachment> attachments = postIds.isEmpty()
                ? List.of()
                : postAttachmentRepository
                .findAllByPostIdInOrderByPostIdAscDisplayOrderAscIdAsc(
                        postIds
                );
        List<Long> fileIds = attachments.stream()
                .map(PostAttachment::getFileId)
                .distinct()
                .toList();
        List<MediaFileSnapshot> files = fileIds.isEmpty()
                ? List.of()
                : mediaFileQueryPort.findAllById(fileIds);

        List<PostListItemResponse> content =
                postResponseAssembler.assembleList(
                        pageKeys,
                        rows,
                        memberId,
                        reactions,
                        attachments,
                        files
                );
        boolean effectiveHasNext = hasNext && !content.isEmpty();
        String nextCursor = effectiveHasNext
                ? encodeNextCursor(
                        pageKeys,
                        content.get(content.size() - 1).postId(),
                        sort,
                        filterHash
                )
                : null;

        return new PostListResponse(
                boardType == null ? null : boardType.name(),
                sort.name(),
                normalized.keyword(),
                content,
                new CursorPageInfoResponse(
                        normalized.size(),
                        nextCursor,
                        effectiveHasNext
                )
        );
    }

    @Transactional
    public PostDetailResponse getDetail(Long memberId, Long postId) {
        validateActiveMember(memberId);

        OptionalLong updatedViewCount =
                postCommandRepository.incrementViewCountIfVisible(postId);
        if (updatedViewCount.isEmpty()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        PostDetailRow row = postQueryRepository
                .findVisibleDetail(postId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));
        PostInteractionRow interactions = postQueryRepository
                .findInteractions(postId, memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));
        List<PostAttachment> attachments = postAttachmentRepository
                .findAllByPostIdOrderByDisplayOrderAscIdAsc(postId);
        List<MediaFileSnapshot> files = mediaFileQueryPort.findAllById(
                attachments.stream()
                        .map(PostAttachment::getFileId)
                        .toList()
        );

        return postResponseAssembler.assembleDetail(
                row,
                memberId,
                updatedViewCount.getAsLong(),
                interactions,
                attachments,
                files
        );
    }

    private void validateActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    private void validateListRequest(PostListCondition condition) {
        int keywordLength = condition.keyword() == null
                ? 0
                : condition.keyword().codePointCount(
                        0,
                        condition.keyword().length()
                );
        if (keywordLength > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field", "keyword",
                            "reason", "검색어는 100자 이하여야 합니다."
                    )
            );
        }
        if (condition.size() < 1 || condition.size() > 50) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field", "size",
                            "reason", "페이지 크기는 1~50이어야 합니다."
                    )
            );
        }
    }

    private BoardType parseBoardType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return BoardType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.POST_BOARD_TYPE_INVALID,
                    Map.of(
                            "field", "boardType",
                            "allowedValues", List.of(
                                    "INFORMATION",
                                    "FREE"
                            )
                    )
            );
        }
    }

    private String encodeNextCursor(
            List<PostListKeyRow> pageKeys,
            Long lastPostId,
            PostSort sort,
            String filterHash
    ) {
        PostListKeyRow lastKey = pageKeys.stream()
                .filter(key -> key.postId().equals(lastPostId))
                .findFirst()
                .orElseThrow();
        PostCursor cursor = sort == PostSort.HOT
                ? PostCursor.hot(
                        lastKey.hotScore(),
                        lastKey.createdAt(),
                        lastKey.postId(),
                        filterHash
                )
                : PostCursor.latest(
                        lastKey.createdAt(),
                        lastKey.postId(),
                        filterHash
                );
        return postCursorCodec.encode(cursor);
    }
}
