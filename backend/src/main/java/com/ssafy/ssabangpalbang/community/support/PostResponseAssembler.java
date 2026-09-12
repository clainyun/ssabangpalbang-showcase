package com.ssafy.ssabangpalbang.community.support;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.domain.PostAttachment;
import com.ssafy.ssabangpalbang.community.dto.response.PostApartmentDetailResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostAttachmentResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostAuthorResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostCreateResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostDetailResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostListItemResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostPermissionsResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostReportResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostUpdateResponse;
import com.ssafy.ssabangpalbang.community.repository.projection.PostDetailRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostListCardRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostListKeyRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostReactionCountRow;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrl;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.media.service.MediaFileSnapshot;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PostResponseAssembler {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String SYSTEM_NICKNAME = "싸방팔방 리포트";
    private static final String WITHDRAWN_NICKNAME = "탈퇴한 회원";
    private static final String DEFAULT_CHARACTER_ID = "PALBANG";
    private static final String MEMBER_AUTHOR_TYPE = "MEMBER";
    private static final String SYSTEM_AUTHOR_TYPE = "SYSTEM";

    private final MediaAccessUrlProvider mediaAccessUrlProvider;
    private final Clock clock;
    private final PostContentPreviewer postContentPreviewer;

    public PostCreateResponse assembleCreatedPost(
            Post post,
            Member author,
            Apartment apartment,
            List<MediaFileSnapshot> files
    ) {
        List<PostCreateResponse.Attachment> attachments =
                new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            MediaFileSnapshot file = files.get(index);
            MediaAccessUrl accessUrl =
                    mediaAccessUrlProvider.issue(file.fileId());
            attachments.add(new PostCreateResponse.Attachment(
                    file.fileId(),
                    file.originalName(),
                    file.contentType(),
                    accessUrl.url(),
                    index + 1,
                    toSeoulDateTime(accessUrl.expiresAt())
            ));
        }

        return new PostCreateResponse(
                post.getId(),
                post.getBoardType().name(),
                post.getTitle(),
                post.getContent(),
                post.getStatus().name(),
                new PostCreateResponse.Author(
                        author.getId(),
                        author.getNickname(),
                        author.getProfileImageUrl(),
                        author.getSelectedCharacterId(),
                        "MEMBER"
                ),
                post.isAutoReport(),
                apartment == null
                        ? null
                        : new PostCreateResponse.Apartment(
                                apartment.getId(),
                                apartment.getName()
                        ),
                null,
                List.copyOf(attachments),
                post.getViewCount(),
                0L,
                0L,
                false,
                true,
                false,
                toSeoulDateTime(post.getCreatedAt()),
                toSeoulDateTime(post.getUpdatedAt())
        );
    }

    public PostDetailResponse assembleDetail(
            PostDetailRow row,
            Long loginMemberId,
            long viewCount,
            PostInteractionRow interactions,
            List<PostAttachment> attachmentLinks,
            List<MediaFileSnapshot> files
    ) {
        boolean isMine = !row.autoReport()
                && loginMemberId.equals(row.authorId());
        boolean isHot = Boolean.TRUE.equals(interactions.hot());

        return new PostDetailResponse(
                row.postId(),
                row.boardType(),
                row.title(),
                row.content(),
                row.status(),
                true,
                assembleAuthor(row),
                row.autoReport(),
                assembleApartment(row),
                assembleReport(row),
                assembleAttachments(attachmentLinks, files),
                viewCount,
                interactions.likeCount(),
                interactions.commentCount(),
                interactions.likedByMe(),
                isMine,
                isHot,
                interactions.hotScore(),
                isHot ? interactions.hotRank() : null,
                new PostPermissionsResponse(
                        isMine,
                        isMine,
                        true,
                        true
                ),
                toSeoulDateTime(row.createdAt()),
                toSeoulDateTime(row.updatedAt())
        );
    }

    public List<PostListItemResponse> assembleList(
            List<PostListKeyRow> orderedKeys,
            List<PostListCardRow> rows,
            Long loginMemberId,
            List<PostReactionCountRow> reactions,
            List<PostAttachment> attachmentLinks,
            List<MediaFileSnapshot> files
    ) {
        Map<Long, PostListCardRow> rowByPostId = new HashMap<>();
        rows.forEach(row -> rowByPostId.put(row.postId(), row));

        Map<Long, PostReactionCountRow> reactionByPostId =
                new HashMap<>();
        reactions.forEach(reaction ->
                reactionByPostId.put(reaction.postId(), reaction));

        Map<Long, MediaFileSnapshot> fileById = new HashMap<>();
        files.forEach(file -> fileById.put(file.fileId(), file));

        Map<Long, Long> thumbnailFileIdByPost =
                findThumbnailFileIds(attachmentLinks, fileById);
        Map<Long, MediaAccessUrl> thumbnailUrls =
                mediaAccessUrlProvider.issueAll(
                        thumbnailFileIdByPost.values()
                );

        List<PostListItemResponse> responses = new ArrayList<>();
        for (PostListKeyRow key : orderedKeys) {
            PostListCardRow row = rowByPostId.get(key.postId());
            if (row == null) {
                continue;
            }

            PostReactionCountRow reaction =
                    reactionByPostId.get(row.postId());
            long likeCount = reaction == null
                    ? 0L
                    : reaction.likeCount();
            long commentCount = reaction == null
                    ? 0L
                    : reaction.commentCount();
            boolean likedByMe = reaction != null
                    && reaction.likedByMe();
            boolean isMine = !row.autoReport()
                    && loginMemberId.equals(row.authorId());
            boolean isHot = Boolean.TRUE.equals(row.hot());

            Long thumbnailFileId =
                    thumbnailFileIdByPost.get(row.postId());
            MediaAccessUrl thumbnailUrl = thumbnailFileId == null
                    ? null
                    : thumbnailUrls.get(thumbnailFileId);
            responses.add(new PostListItemResponse(
                    row.postId(),
                    row.boardType(),
                    row.title(),
                    postContentPreviewer.preview(row.content()),
                    assembleAuthor(row),
                    thumbnailUrl == null ? null : thumbnailUrl.url(),
                    row.autoReport(),
                    row.apartmentId() == null
                            ? null
                            : new PostListItemResponse.Apartment(
                                    row.apartmentId(),
                                    row.apartmentName()
                            ),
                    assembleReport(row),
                    row.viewCount(),
                    likeCount,
                    commentCount,
                    likedByMe,
                    isMine,
                    isHot,
                    row.hotScore(),
                    isHot ? row.hotRank() : null,
                    toSeoulDateTime(row.createdAt()),
                    toSeoulDateTime(row.updatedAt())
            ));
        }
        return List.copyOf(responses);
    }

    public PostUpdateResponse assembleUpdatedPost(
            PostDetailRow row,
            Long loginMemberId,
            long viewCount,
            PostInteractionRow interactions,
            List<PostAttachment> attachmentLinks,
            List<MediaFileSnapshot> files
    ) {
        boolean isMine = !row.autoReport()
                && loginMemberId.equals(row.authorId());
        boolean isHot = Boolean.TRUE.equals(interactions.hot());

        return new PostUpdateResponse(
                row.postId(),
                row.boardType().name(),
                row.title(),
                row.content(),
                row.status().name(),
                assembleAuthor(row),
                row.autoReport(),
                row.apartmentId() == null
                        ? null
                        : new PostUpdateResponse.Apartment(
                                row.apartmentId(),
                                row.apartmentName()
                        ),
                null,
                assembleUpdatedAttachments(attachmentLinks, files),
                viewCount,
                interactions.likeCount(),
                interactions.commentCount(),
                interactions.likedByMe(),
                isMine,
                isHot,
                toSeoulDateTime(row.createdAt()),
                toSeoulDateTime(row.updatedAt())
        );
    }

    private PostAuthorResponse assembleAuthor(PostDetailRow row) {
        if (row.autoReport()) {
            return new PostAuthorResponse(
                    null,
                    SYSTEM_NICKNAME,
                    null,
                    DEFAULT_CHARACTER_ID,
                    SYSTEM_AUTHOR_TYPE
            );
        }

        if (row.authorId() == null
                || row.authorStatus() != MemberStatus.ACTIVE
                || row.authorDeletedAt() != null) {
            return new PostAuthorResponse(
                    null,
                    WITHDRAWN_NICKNAME,
                    null,
                    DEFAULT_CHARACTER_ID,
                    MEMBER_AUTHOR_TYPE
            );
        }

        return new PostAuthorResponse(
                row.authorId(),
                row.authorNickname(),
                row.authorProfileImageUrl(),
                row.authorSelectedCharacterId(),
                MEMBER_AUTHOR_TYPE
        );
    }

    private PostAuthorResponse assembleAuthor(PostListCardRow row) {
        if (row.autoReport()) {
            return new PostAuthorResponse(
                    null,
                    SYSTEM_NICKNAME,
                    null,
                    DEFAULT_CHARACTER_ID,
                    SYSTEM_AUTHOR_TYPE
            );
        }

        if (row.authorId() == null
                || row.authorStatus() != MemberStatus.ACTIVE
                || row.authorDeletedAt() != null) {
            return new PostAuthorResponse(
                    null,
                    WITHDRAWN_NICKNAME,
                    null,
                    DEFAULT_CHARACTER_ID,
                    MEMBER_AUTHOR_TYPE
            );
        }

        return new PostAuthorResponse(
                row.authorId(),
                row.authorNickname(),
                row.authorProfileImageUrl(),
                row.authorSelectedCharacterId(),
                MEMBER_AUTHOR_TYPE
        );
    }

    private PostApartmentDetailResponse assembleApartment(
            PostDetailRow row
    ) {
        if (row.apartmentId() == null) {
            return null;
        }
        return new PostApartmentDetailResponse(
                row.apartmentId(),
                row.apartmentName(),
                row.apartmentAddress()
        );
    }

    private PostReportResponse assembleReport(PostDetailRow row) {
        if (row.reportId() == null) {
            return null;
        }
        return new PostReportResponse(
                row.reportId(),
                row.reportStatus(),
                row.reportStatus() == ReportStatus.DONE
        );
    }

    private PostReportResponse assembleReport(PostListCardRow row) {
        if (row.reportId() == null) {
            return null;
        }
        return new PostReportResponse(
                row.reportId(),
                row.reportStatus(),
                row.reportStatus() == ReportStatus.DONE
        );
    }

    private Map<Long, Long> findThumbnailFileIds(
            List<PostAttachment> attachmentLinks,
            Map<Long, MediaFileSnapshot> fileById
    ) {
        Instant now = clock.instant();
        Map<Long, Long> thumbnailFileIdByPost = new HashMap<>();
        for (PostAttachment attachment : attachmentLinks) {
            if (thumbnailFileIdByPost.containsKey(
                    attachment.getPostId()
            )) {
                continue;
            }

            MediaFileSnapshot file = fileById.get(
                    attachment.getFileId()
            );
            if (isAvailable(file, now)
                    && file.contentType() != null
                    && file.contentType()
                    .toLowerCase(Locale.ROOT)
                    .startsWith("image/")) {
                thumbnailFileIdByPost.put(
                        attachment.getPostId(),
                        file.fileId()
                );
            }
        }
        return thumbnailFileIdByPost;
    }

    private List<PostAttachmentResponse> assembleAttachments(
            List<PostAttachment> attachmentLinks,
            List<MediaFileSnapshot> files
    ) {
        Map<Long, MediaFileSnapshot> fileById = new HashMap<>();
        files.forEach(file -> fileById.put(file.fileId(), file));

        Instant now = clock.instant();
        List<PostAttachmentResponse> responses =
                new ArrayList<>(attachmentLinks.size());
        for (PostAttachment attachment : attachmentLinks) {
            MediaFileSnapshot file = fileById.get(attachment.getFileId());
            responses.add(assembleAttachment(attachment, file, now));
        }
        return List.copyOf(responses);
    }

    private List<PostUpdateResponse.Attachment> assembleUpdatedAttachments(
            List<PostAttachment> attachmentLinks,
            List<MediaFileSnapshot> files
    ) {
        Map<Long, MediaFileSnapshot> fileById = new HashMap<>();
        files.forEach(file -> fileById.put(file.fileId(), file));

        Instant now = clock.instant();
        List<PostUpdateResponse.Attachment> responses =
                new ArrayList<>(attachmentLinks.size());
        for (PostAttachment attachment : attachmentLinks) {
            PostAttachmentResponse detail = assembleAttachment(
                    attachment,
                    fileById.get(attachment.getFileId()),
                    now
            );
            responses.add(new PostUpdateResponse.Attachment(
                    detail.fileId(),
                    detail.originalName(),
                    detail.contentType(),
                    detail.fileUrl(),
                    detail.displayOrder()
            ));
        }
        return List.copyOf(responses);
    }

    private PostAttachmentResponse assembleAttachment(
            PostAttachment attachment,
            MediaFileSnapshot file,
            Instant now
    ) {
        if (!isAvailable(file, now)) {
            return unavailableAttachment(attachment, file);
        }

        try {
            MediaAccessUrl accessUrl =
                    mediaAccessUrlProvider.issue(file.fileId());
            return new PostAttachmentResponse(
                    file.fileId(),
                    file.originalName(),
                    file.contentType(),
                    accessUrl.url(),
                    attachment.getDisplayOrder() + 1,
                    true,
                    toSeoulDateTime(accessUrl.expiresAt())
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.MEDIA_FILE_NOT_FOUND
                    && exception.getErrorCode()
                    != ErrorCode.MEDIA_ACCESS_DENIED) {
                throw exception;
            }
            return unavailableAttachment(attachment, file);
        }
    }

    private boolean isAvailable(MediaFileSnapshot file, Instant now) {
        return file != null
                && file.uploadStatus() == UploadStatus.COMPLETED
                && file.deletedAt() == null
                && (file.expiresAt() == null
                || file.expiresAt().isAfter(now));
    }

    private PostAttachmentResponse unavailableAttachment(
            PostAttachment attachment,
            MediaFileSnapshot file
    ) {
        return new PostAttachmentResponse(
                attachment.getFileId(),
                file == null ? null : file.originalName(),
                file == null ? null : file.contentType(),
                null,
                attachment.getDisplayOrder() + 1,
                false,
                null
        );
    }

    private OffsetDateTime toSeoulDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, SEOUL_ZONE_ID);
    }
}
