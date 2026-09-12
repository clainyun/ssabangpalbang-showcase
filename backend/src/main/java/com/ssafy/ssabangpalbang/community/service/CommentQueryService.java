package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.community.dto.request.CommentListCondition;
import com.ssafy.ssabangpalbang.community.dto.response.CommentAuthorResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentListItemResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentListResponse;
import com.ssafy.ssabangpalbang.community.repository.CommentQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentCursorRow;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentListRow;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentPostContextRow;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommentQueryService {

    private static final ZoneId SEOUL_ZONE_ID =
            ZoneId.of("Asia/Seoul");
    private static final String WITHDRAWN_NICKNAME = "탈퇴한 회원";
    private static final String DEFAULT_CHARACTER_ID = "PALBANG";
    private static final String CURSOR_INVALID_REASON =
            "해당 게시글의 댓글 커서를 확인해 주세요.";
    private static final String SIZE_INVALID_REASON =
            "조회 개수는 1 이상 100 이하이어야 합니다.";

    private final MemberRepository memberRepository;
    private final CommentQueryRepository commentQueryRepository;

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public CommentListResponse getComments(
            Long memberId,
            Long postId,
            CommentListCondition condition
    ) {
        validateActiveMember(memberId);
        validatePostId(postId);

        CommentListCondition normalized = condition == null
                ? new CommentListCondition(null, null).normalize()
                : condition.normalize();
        validateCondition(normalized);

        CommentPostContextRow post =
                commentQueryRepository.findVisiblePostContext(postId)
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.POST_NOT_FOUND
                        ));
        CommentCursorRow cursor = normalized.cursor() == null
                ? null
                : commentQueryRepository.findCursor(
                        postId,
                        normalized.cursor()
                ).orElseThrow(this::invalidCursor);

        List<CommentListRow> fetched =
                commentQueryRepository.findComments(
                        postId,
                        cursor,
                        normalized.size() + 1
                );
        boolean hasNext = fetched.size() > normalized.size();
        List<CommentListRow> pageRows = hasNext
                ? List.copyOf(fetched.subList(0, normalized.size()))
                : List.copyOf(fetched);
        List<CommentListItemResponse> content = pageRows.stream()
                .map(row -> assembleItem(row, post, memberId))
                .toList();
        Long nextCursor = hasNext && !content.isEmpty()
                ? content.get(content.size() - 1).commentId()
                : null;
        long totalCount =
                commentQueryRepository.countActiveComments(postId);

        return new CommentListResponse(
                postId,
                content,
                totalCount,
                nextCursor,
                hasNext
        );
    }

    private void validateActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member ->
                        member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    private void validatePostId(Long postId) {
        if (postId == null || postId < 1) {
            throw invalidRequest(
                    "postId",
                    "게시글 ID는 1 이상의 숫자여야 합니다."
            );
        }
    }

    private void validateCondition(CommentListCondition condition) {
        if (condition.cursor() != null && condition.cursor() < 1) {
            throw invalidRequest(
                    "cursor",
                    "커서는 1 이상의 숫자여야 합니다."
            );
        }
        if (condition.size() < 1 || condition.size() > 100) {
            throw invalidRequest("size", SIZE_INVALID_REASON);
        }
    }

    private BusinessException invalidCursor() {
        return invalidRequest("cursor", CURSOR_INVALID_REASON);
    }

    private BusinessException invalidRequest(
            String field,
            String reason
    ) {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of("field", field, "reason", reason)
        );
    }

    private CommentListItemResponse assembleItem(
            CommentListRow row,
            CommentPostContextRow post,
            Long memberId
    ) {
        boolean activeAuthor = row.authorId() != null
                && row.authorStatus() == MemberStatus.ACTIVE
                && row.authorDeletedAt() == null;
        CommentAuthorResponse author = activeAuthor
                ? new CommentAuthorResponse(
                        row.authorId(),
                        row.authorNickname(),
                        row.authorProfileImageUrl(),
                        row.authorSelectedCharacterId()
                )
                : new CommentAuthorResponse(
                        null,
                        WITHDRAWN_NICKNAME,
                        null,
                        DEFAULT_CHARACTER_ID
                );
        boolean isMine = activeAuthor
                && Objects.equals(row.authorId(), memberId);
        boolean isPostAuthor = row.authorId() != null
                && post.authorId() != null
                && Objects.equals(row.authorId(), post.authorId());

        return new CommentListItemResponse(
                row.commentId(),
                row.content(),
                author,
                isMine,
                isPostAuthor,
                isMine,
                isMine,
                toSeoulDateTime(row.createdAt()),
                toSeoulDateTime(row.updatedAt())
        );
    }

    private OffsetDateTime toSeoulDateTime(Instant instant) {
        return Objects.requireNonNull(instant)
                .atZone(SEOUL_ZONE_ID)
                .toOffsetDateTime();
    }
}
