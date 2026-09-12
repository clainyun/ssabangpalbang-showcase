package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.repository.projection.CommentUpdateRow;

import java.time.Instant;
import java.util.Optional;

public interface CommentCommandRepository {

    Optional<CommentUpdateRow> updateContentIfActive(
            Long commentId,
            Long memberId,
            String content,
            Instant updatedAt
    );

    int softDelete(
            Long commentId,
            Long memberId,
            Instant deletedAt
    );
}
