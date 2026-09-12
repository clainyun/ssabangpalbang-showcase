package com.ssafy.ssabangpalbang.community.dto.response;

import com.ssafy.ssabangpalbang.community.domain.BoardType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PostListItemResponse(
        Long postId,
        BoardType boardType,
        String title,
        String contentPreview,
        PostAuthorResponse author,
        String thumbnailUrl,
        boolean isAutoReport,
        Apartment apartment,
        PostReportResponse report,
        long viewCount,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        boolean isMine,
        boolean isHot,
        BigDecimal hotScore,
        Long hotRank,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public record Apartment(
            Long apartmentId,
            String name
    ) {
    }
}
