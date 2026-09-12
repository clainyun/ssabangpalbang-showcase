package com.ssafy.ssabangpalbang.community.repository.projection;

import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PostListCardRow(
        Long postId,
        BoardType boardType,
        String title,
        String content,
        boolean autoReport,
        Long authorId,
        String authorNickname,
        String authorProfileImageUrl,
        String authorSelectedCharacterId,
        MemberStatus authorStatus,
        Instant authorDeletedAt,
        Long apartmentId,
        String apartmentName,
        Long reportId,
        ReportStatus reportStatus,
        long viewCount,
        BigDecimal hotScore,
        Boolean hot,
        Long hotRank,
        Instant createdAt,
        Instant updatedAt
) {
}
