package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.repository.projection.CommentPostMetricsRow;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentSnapshot;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentCursorRow;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentListRow;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentPostContextRow;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CommentQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<CommentPostContextRow> findVisiblePostContext(
            Long postId
    ) {
        return jdbcTemplate.query(
                """
                        SELECT
                            id,
                            author_id
                        FROM post
                        WHERE id = ?
                          AND status = 'ACTIVE'
                          AND deleted_at IS NULL
                        """,
                (resultSet, rowNumber) ->
                        new CommentPostContextRow(
                                resultSet.getLong("id"),
                                resultSet.getObject(
                                        "author_id",
                                        Long.class
                                )
                        ),
                postId
        ).stream().findFirst();
    }

    public Optional<CommentCursorRow> findCursor(
            Long postId,
            Long commentId
    ) {
        return jdbcTemplate.query(
                """
                        SELECT
                            id,
                            created_at
                        FROM post_comment
                        WHERE post_id = ?
                          AND id = ?
                        """,
                (resultSet, rowNumber) ->
                        new CommentCursorRow(
                                resultSet.getLong("id"),
                                resultSet.getTimestamp("created_at")
                                        .toInstant()
                        ),
                postId,
                commentId
        ).stream().findFirst();
    }

    public List<CommentListRow> findComments(
            Long postId,
            CommentCursorRow cursor,
            int limit
    ) {
        if (cursor == null) {
            return jdbcTemplate.query(
                    """
                            SELECT
                                pc.id AS comment_id,
                                pc.content,
                                author.id AS author_id,
                                author.nickname AS author_nickname,
                                author.profile_image_url,
                                author.selected_character_id,
                                author.status AS author_status,
                                author.deleted_at AS author_deleted_at,
                                pc.created_at,
                                pc.updated_at
                            FROM post_comment pc
                            LEFT JOIN member author
                              ON author.id = pc.author_id
                            WHERE pc.post_id = ?
                              AND pc.deleted_at IS NULL
                            ORDER BY
                                pc.created_at ASC,
                                pc.id ASC
                            LIMIT ?
                            """,
                    commentListRowMapper(),
                    postId,
                    limit
            );
        }

        return jdbcTemplate.query(
                """
                        SELECT
                            pc.id AS comment_id,
                            pc.content,
                            author.id AS author_id,
                            author.nickname AS author_nickname,
                            author.profile_image_url,
                            author.selected_character_id,
                            author.status AS author_status,
                            author.deleted_at AS author_deleted_at,
                            pc.created_at,
                            pc.updated_at
                        FROM post_comment pc
                        LEFT JOIN member author
                          ON author.id = pc.author_id
                        WHERE pc.post_id = ?
                          AND pc.deleted_at IS NULL
                          AND (
                              pc.created_at > ?
                              OR (
                                  pc.created_at = ?
                                  AND pc.id > ?
                              )
                          )
                        ORDER BY
                            pc.created_at ASC,
                            pc.id ASC
                        LIMIT ?
                        """,
                commentListRowMapper(),
                postId,
                Timestamp.from(cursor.createdAt()),
                Timestamp.from(cursor.createdAt()),
                cursor.commentId(),
                limit
        );
    }

    public long countActiveComments(Long postId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM post_comment
                        WHERE post_id = ?
                          AND deleted_at IS NULL
                        """,
                Long.class,
                postId
        );
        return count == null ? 0L : count;
    }

    public Optional<CommentSnapshot> findSnapshot(Long commentId) {
        return findSnapshot(commentId, false);
    }

    public Optional<CommentSnapshot> findSnapshotForUpdate(
            Long commentId
    ) {
        return findSnapshot(commentId, true);
    }

    private Optional<CommentSnapshot> findSnapshot(
            Long commentId,
            boolean forUpdate
    ) {
        String lockClause = forUpdate ? " FOR UPDATE" : "";
        return jdbcTemplate.query(
                """
                        SELECT
                            id,
                            post_id,
                            author_id,
                            deleted_at
                        FROM post_comment
                        WHERE id = ?
                        """ + lockClause,
                (resultSet, rowNumber) -> new CommentSnapshot(
                        resultSet.getLong("id"),
                        resultSet.getLong("post_id"),
                        resultSet.getLong("author_id"),
                        resultSet.getTimestamp("deleted_at") == null
                                ? null
                                : resultSet.getTimestamp("deleted_at")
                                .toInstant()
                ),
                commentId
        ).stream().findFirst();
    }

    public Optional<CommentPostMetricsRow> findPostMetrics(Long postId) {
        return jdbcTemplate.query(
                """
                        SELECT
                            (
                                post.status = 'ACTIVE'
                                AND post.deleted_at IS NULL
                            ) AS post_available,
                            (
                                SELECT COUNT(*)
                                FROM post_comment comment_count
                                WHERE comment_count.post_id = post.id
                                  AND comment_count.deleted_at IS NULL
                            ) AS comment_count,
                            (
                                SELECT COUNT(*)
                                FROM post_like like_count
                                WHERE like_count.post_id = post.id
                            ) AS like_count,
                            post.view_count,
                            metric.is_hot,
                            metric.hot_score
                        FROM post
                        LEFT JOIN post_hot_metric metric
                          ON metric.post_id = post.id
                        WHERE post.id = ?
                        """,
                (resultSet, rowNumber) ->
                        new CommentPostMetricsRow(
                                resultSet.getBoolean("post_available"),
                                resultSet.getLong("comment_count"),
                                resultSet.getLong("like_count"),
                                resultSet.getLong("view_count"),
                                resultSet.getObject(
                                        "is_hot",
                                        Boolean.class
                                ),
                                resultSet.getBigDecimal("hot_score")
                        ),
                postId
        ).stream().findFirst();
    }

    private RowMapper<CommentListRow> commentListRowMapper() {
        return (resultSet, rowNumber) -> {
            Timestamp authorDeletedAt =
                    resultSet.getTimestamp("author_deleted_at");
            String authorStatus =
                    resultSet.getString("author_status");
            return new CommentListRow(
                    resultSet.getLong("comment_id"),
                    resultSet.getString("content"),
                    resultSet.getObject("author_id", Long.class),
                    resultSet.getString("author_nickname"),
                    resultSet.getString("profile_image_url"),
                    resultSet.getString("selected_character_id"),
                    authorStatus == null
                            ? null
                            : MemberStatus.valueOf(authorStatus),
                    toInstant(authorDeletedAt),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );
        };
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
