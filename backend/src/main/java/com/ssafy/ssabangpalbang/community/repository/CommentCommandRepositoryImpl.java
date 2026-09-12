package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.repository.projection.CommentUpdateRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CommentCommandRepositoryImpl
        implements CommentCommandRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<CommentUpdateRow> updateContentIfActive(
            Long commentId,
            Long memberId,
            String content,
            Instant updatedAt
    ) {
        return jdbcTemplate.query(
                """
                        UPDATE post_comment
                        SET content = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND author_id = ?
                          AND deleted_at IS NULL
                        RETURNING
                            id,
                            post_id,
                            author_id,
                            content,
                            created_at,
                            updated_at
                        """,
                (resultSet, rowNumber) -> new CommentUpdateRow(
                        resultSet.getLong("id"),
                        resultSet.getLong("post_id"),
                        resultSet.getLong("author_id"),
                        resultSet.getString("content"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()
                ),
                content,
                Timestamp.from(updatedAt),
                commentId,
                memberId
        ).stream().findFirst();
    }

    @Override
    public int softDelete(
            Long commentId,
            Long memberId,
            Instant deletedAt
    ) {
        Timestamp timestamp = Timestamp.from(deletedAt);
        return jdbcTemplate.update(
                """
                        UPDATE post_comment
                        SET deleted_at = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND author_id = ?
                          AND deleted_at IS NULL
                        """,
                timestamp,
                timestamp,
                commentId,
                memberId
        );
    }
}
