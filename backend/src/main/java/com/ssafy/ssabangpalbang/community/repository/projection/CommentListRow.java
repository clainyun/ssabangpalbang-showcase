package com.ssafy.ssabangpalbang.community.repository.projection;

import com.ssafy.ssabangpalbang.member.domain.MemberStatus;

import java.time.Instant;

public record CommentListRow(
        Long commentId,
        String content,
        Long authorId,
        String authorNickname,
        String authorProfileImageUrl,
        String authorSelectedCharacterId,
        MemberStatus authorStatus,
        Instant authorDeletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
