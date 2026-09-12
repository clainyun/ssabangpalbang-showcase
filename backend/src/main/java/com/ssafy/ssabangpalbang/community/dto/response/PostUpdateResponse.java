package com.ssafy.ssabangpalbang.community.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record PostUpdateResponse(
        Long postId,
        String boardType,
        String title,
        String content,
        String status,
        PostAuthorResponse author,
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
            int displayOrder
    ) {
    }
}
