package com.ssafy.ssabangpalbang.community.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

@Repository
@RequiredArgsConstructor
public class PostCommandRepositoryImpl implements PostCommandRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public OptionalLong incrementViewCountIfVisible(Long postId) {
        List<Long> updatedCounts = jdbcTemplate.query(
                """
                        UPDATE post
                        SET view_count = view_count + 1
                        WHERE id = ?
                          AND status = 'ACTIVE'
                          AND deleted_at IS NULL
                        RETURNING view_count
                        """,
                (resultSet, rowNumber) ->
                        resultSet.getLong("view_count"),
                postId
        );

        if (updatedCounts.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(updatedCounts.get(0));
    }

    @Override
    public Optional<Instant> insertLikeIfAbsent(
            Long postId,
            Long memberId
    ) {
        return jdbcTemplate.query(
                """
                        INSERT INTO post_like (
                            post_id,
                            member_id,
                            created_at
                        )
                        VALUES (?, ?, NOW())
                        ON CONFLICT (post_id, member_id) DO NOTHING
                        RETURNING created_at
                        """,
                (resultSet, rowNumber) ->
                        resultSet.getTimestamp("created_at").toInstant(),
                postId,
                memberId
        ).stream().findFirst();
    }

    @Override
    public Optional<Instant> findLikedAt(
            Long postId,
            Long memberId
    ) {
        return jdbcTemplate.query(
                """
                        SELECT created_at
                        FROM post_like
                        WHERE post_id = ?
                          AND member_id = ?
                        """,
                (resultSet, rowNumber) ->
                        resultSet.getTimestamp("created_at").toInstant(),
                postId,
                memberId
        ).stream().findFirst();
    }

    @Override
    public int deleteLike(Long postId, Long memberId) {
        return jdbcTemplate.update(
                """
                        DELETE FROM post_like
                        WHERE post_id = ?
                          AND member_id = ?
                        """,
                postId,
                memberId
        );
    }
}
