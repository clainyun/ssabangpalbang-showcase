package com.ssafy.ssabangpalbang.community.dto.response;

import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PostDetailResponse(
        Long postId,
        BoardType boardType,
        String title,
        String content,
        PostStatus status,
        boolean originalAvailable,
        PostAuthorResponse author,
        boolean isAutoReport,
        PostApartmentDetailResponse apartment,
        PostReportResponse report,
        List<PostAttachmentResponse> attachments,
        long viewCount,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        boolean isMine,
        boolean isHot,
        BigDecimal hotScore,
        Long hotRank,
        PostPermissionsResponse permissions,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
