package com.ssafy.ssabangpalbang.community.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record PostCreateResponse(
        Long postId,
        String boardType,
        String title,
        String content,
        String status,
        Author author,
        boolean isAutoReport,
        Apartment apartment,
        Object report,
        List<Attachment> attachments,
        long viewCount,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        boolean isMine,
        boolean isHot,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public record Author(
            Long memberId,
            String nickname,
            String profileImageUrl,
            String selectedCharacterId,
            String authorType
    ) {
    }

    public record Apartment(
            Long apartmentId,
            String name
    ) {
    }

    public record Attachment(
            Long fileId,
            String originalName,
            String contentType,
            String fileUrl,
            int displayOrder,
            OffsetDateTime expiresAt
    ) {
    }
}
