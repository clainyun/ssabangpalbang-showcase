package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.domain.PostComment;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.community.dto.request.CommentCreateRequest;
import com.ssafy.ssabangpalbang.community.dto.request.CommentUpdateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.CommentAuthorResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentCreateResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentDeleteResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentPostMetricsResponse;
import com.ssafy.ssabangpalbang.community.dto.response.CommentResponse;
import com.ssafy.ssabangpalbang.community.repository.CommentCommandRepository;
import com.ssafy.ssabangpalbang.community.repository.CommentQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostCommentRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostRepository;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentPostMetricsRow;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentSnapshot;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentUpdateRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommentCommandService {

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final ZoneId SEOUL_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostQueryRepository postQueryRepository;
    private final CommentCommandRepository commentCommandRepository;
    private final CommentQueryRepository commentQueryRepository;
    private final Clock clock;

    @Transactional
    public CommentCreateResponse create(
            Long memberId,
            Long postId,
            CommentCreateRequest request
    ) {
        String content = normalizeContent(request.content());
        Member member = findActiveMember(memberId);
        Post post = findCommentablePost(postId);

        PostComment comment = postCommentRepository.save(
                PostComment.create(postId, memberId, content)
        );
        postCommentRepository.flush();

        PostInteractionRow interactions = postQueryRepository
                .findInteractions(postId, memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));

        return assembleResponse(
                memberId,
                member,
                post,
                comment,
                interactions
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommentResponse update(
            Long memberId,
            Long commentId,
            CommentUpdateRequest request
    ) {
        String content = normalizeContent(
                request == null ? null : request.content()
        );
        Member member = findActiveMember(memberId);
        CommentSnapshot snapshot = findCommentSnapshot(commentId);
        validateCommentNotDeletedForUpdate(snapshot);

        Post post = findCommentablePost(snapshot.postId());
        validateUpdateAuthor(snapshot, memberId);

        CommentSnapshot lockedSnapshot = commentQueryRepository
                .findSnapshotForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.COMMENT_NOT_FOUND
                ));
        validateLockedUpdateSnapshot(
                snapshot,
                lockedSnapshot,
                memberId
        );

        Instant updatedAt = clock.instant().truncatedTo(
                ChronoUnit.MICROS
        );
        CommentUpdateRow updateRow = commentCommandRepository
                .updateContentIfActive(
                        commentId,
                        memberId,
                        content,
                        updatedAt
                )
                .orElseGet(() -> classifyUpdateFailure(
                        commentId,
                        memberId
                ));

        return assembleCommentResponse(
                memberId,
                member,
                post,
                updateRow.commentId(),
                updateRow.postId(),
                updateRow.content(),
                updateRow.createdAt(),
                updateRow.updatedAt()
        );
    }

    @Transactional
    public CommentDeleteResponse delete(
            Long memberId,
            Long commentId
    ) {
        findActiveMember(memberId);
        CommentSnapshot snapshot = findCommentSnapshot(commentId);
        validateDeletableComment(snapshot, memberId);

        Instant deletedAt = clock.instant().truncatedTo(
                ChronoUnit.MICROS
        );
        int updatedRows = commentCommandRepository.softDelete(
                commentId,
                memberId,
                deletedAt
        );
        if (updatedRows != 1) {
            classifyDeleteFailure(commentId, memberId);
        }

        CommentPostMetricsRow metricRow = commentQueryRepository
                .findPostMetrics(snapshot.postId())
                .orElseThrow(() -> new IllegalStateException(
                        "댓글 원문 게시글 지표를 조회할 수 없습니다."
                ));
        CommentPostMetricsResponse postMetrics =
                new CommentPostMetricsResponse(
                        metricRow.commentCount(),
                        metricRow.likeCount(),
                        metricRow.viewCount(),
                        Boolean.TRUE.equals(metricRow.hot()),
                        metricRow.hotScore()
                );

        return CommentDeleteResponse.from(
                commentId,
                snapshot.postId(),
                deletedAt,
                metricRow.postAvailable(),
                postMetrics
        );
    }

    private void validateCommentNotDeletedForUpdate(
            CommentSnapshot snapshot
    ) {
        if (snapshot.deletedAt() != null) {
            throw alreadyDeletedForUpdate();
        }
    }

    private void validateUpdateAuthor(
            CommentSnapshot snapshot,
            Long memberId
    ) {
        if (!Objects.equals(snapshot.authorId(), memberId)) {
            throw new BusinessException(
                    ErrorCode.COMMENT_UPDATE_FORBIDDEN
            );
        }
    }

    private void validateLockedUpdateSnapshot(
            CommentSnapshot initial,
            CommentSnapshot locked,
            Long memberId
    ) {
        validateCommentNotDeletedForUpdate(locked);
        if (!Objects.equals(initial.postId(), locked.postId())
                || !Objects.equals(
                initial.authorId(),
                locked.authorId()
        )) {
            throw new IllegalStateException(
                    "댓글의 불변 참조 정보가 변경되었습니다."
            );
        }
        validateUpdateAuthor(locked, memberId);
    }

    private CommentUpdateRow classifyUpdateFailure(
            Long commentId,
            Long memberId
    ) {
        CommentSnapshot current = findCommentSnapshot(commentId);
        validateCommentNotDeletedForUpdate(current);
        validateUpdateAuthor(current, memberId);
        throw new IllegalStateException(
                "댓글 수정 상태를 변경할 수 없습니다."
        );
    }

    private CommentSnapshot findCommentSnapshot(Long commentId) {
        return commentQueryRepository.findSnapshot(commentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.COMMENT_NOT_FOUND
                ));
    }

    private void validateDeletableComment(
            CommentSnapshot snapshot,
            Long memberId
    ) {
        if (snapshot.deletedAt() != null) {
            throw alreadyDeleted(snapshot.commentId());
        }
        if (!Objects.equals(snapshot.authorId(), memberId)) {
            throw new BusinessException(
                    ErrorCode.COMMENT_DELETE_FORBIDDEN
            );
        }
    }

    private void classifyDeleteFailure(
            Long commentId,
            Long memberId
    ) {
        CommentSnapshot current = findCommentSnapshot(commentId);
        validateDeletableComment(current, memberId);
        throw new IllegalStateException(
                "댓글 삭제 상태를 변경할 수 없습니다."
        );
    }

    private BusinessException alreadyDeleted(Long commentId) {
        return new BusinessException(
                ErrorCode.COMMENT_ALREADY_DELETED,
                Map.of("commentId", commentId)
        );
    }

    private BusinessException alreadyDeletedForUpdate() {
        return new BusinessException(
                ErrorCode.COMMENT_ALREADY_DELETED,
                "삭제된 댓글은 수정할 수 없습니다."
        );
    }

    private Member findActiveMember(Long memberId) {
        return memberRepository.findByIdForShare(memberId)
                .filter(member ->
                        member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    private Post findCommentablePost(Long postId) {
        return postRepository.findByIdForShare(postId)
                .filter(post -> post.getStatus() == PostStatus.ACTIVE)
                .filter(post -> !post.isDeleted())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));
    }

    private String normalizeContent(String value) {
        String normalized = value == null
                ? ""
                : stripExtendedWhitespace(value);
        if (normalized.indexOf('\0') >= 0
                || hasUnpairedSurrogate(normalized)
                || isVisuallyBlank(normalized)
                || normalized.codePointCount(
                        0,
                        normalized.length()
                ) > MAX_CONTENT_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field",
                            "content",
                            "reason",
                            CommentCreateRequest.CONTENT_INVALID_REASON
                    )
            );
        }
        return normalized;
    }

    private String stripExtendedWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isExtendedWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isExtendedWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(
                        value.charAt(index + 1)
                )) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVisuallyBlank(String value) {
        return value.isEmpty()
                || value.codePoints().allMatch(
                        this::isVisuallyBlankCodePoint
                );
    }

    private boolean isVisuallyBlankCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return isExtendedWhitespace(codePoint)
                || type == Character.CONTROL
                || type == Character.FORMAT;
    }

    private boolean isExtendedWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0x200B
                || codePoint == 0xFEFF;
    }

    private CommentCreateResponse assembleResponse(
            Long memberId,
            Member member,
            Post post,
            PostComment comment,
            PostInteractionRow interactions
    ) {
        boolean isHot = Boolean.TRUE.equals(interactions.hot());
        CommentResponse commentResponse = assembleCommentResponse(
                memberId,
                member,
                post,
                comment.getId(),
                comment.getPostId(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
        CommentPostMetricsResponse metrics =
                new CommentPostMetricsResponse(
                        interactions.commentCount(),
                        interactions.likeCount(),
                        post.getViewCount(),
                        isHot,
                        interactions.hotScore()
                );
        return new CommentCreateResponse(commentResponse, metrics);
    }

    private CommentResponse assembleCommentResponse(
            Long memberId,
            Member member,
            Post post,
            Long commentId,
            Long postId,
            String content,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new CommentResponse(
                commentId,
                postId,
                content,
                new CommentAuthorResponse(
                        member.getId(),
                        member.getNickname(),
                        member.getProfileImageUrl(),
                        member.getSelectedCharacterId()
                ),
                true,
                Objects.equals(post.getAuthorId(), memberId),
                true,
                true,
                toSeoulDateTime(createdAt),
                toSeoulDateTime(updatedAt)
        );
    }

    private OffsetDateTime toSeoulDateTime(Instant instant) {
        return Objects.requireNonNull(instant)
                .atZone(SEOUL_ZONE_ID)
                .toOffsetDateTime();
    }
}
