package com.ssafy.ssabangpalbang.community.repository.projection;

import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;

import java.time.Instant;

public record PostDetailRow(
        Long postId,
        BoardType boardType,
        String title,
        String content,
        PostStatus status,
        boolean autoReport,
        Long authorId,
        String authorNickname,
        String authorProfileImageUrl,
        String authorSelectedCharacterId,
        MemberStatus authorStatus,
        Instant authorDeletedAt,
        Long apartmentId,
        String apartmentName,
        String apartmentAddress,
        Long reportId,
        ReportStatus reportStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
