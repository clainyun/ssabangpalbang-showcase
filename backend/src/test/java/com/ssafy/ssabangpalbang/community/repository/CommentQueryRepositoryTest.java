package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.repository.projection.CommentCursorRow;
import com.ssafy.ssabangpalbang.community.repository.projection.CommentListRow;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@ActiveProfiles("test")
@Import(CommentQueryRepository.class)
@Sql(statements = {
        "DROP TABLE IF EXISTS post_comment",
        "DROP TABLE IF EXISTS post",
        "DROP TABLE IF EXISTS member",
        "CREATE TABLE member ("
                + "id BIGINT PRIMARY KEY, "
                + "nickname VARCHAR(50) NOT NULL, "
                + "profile_image_url VARCHAR(500), "
                + "selected_character_id VARCHAR(20) NOT NULL, "
                + "status VARCHAR(20) NOT NULL, "
                + "deleted_at TIMESTAMP WITH TIME ZONE)",
        "CREATE TABLE post ("
                + "id BIGINT PRIMARY KEY, "
                + "author_id BIGINT, "
                + "status VARCHAR(20) NOT NULL, "
                + "deleted_at TIMESTAMP WITH TIME ZONE)",
        "CREATE TABLE post_comment ("
                + "id BIGINT PRIMARY KEY, "
                + "post_id BIGINT NOT NULL, "
                + "author_id BIGINT NOT NULL, "
                + "content TEXT NOT NULL, "
                + "deleted_at TIMESTAMP WITH TIME ZONE, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)"
})
@Sql(
        statements = {
                "DROP TABLE IF EXISTS post_comment",
                "DROP TABLE IF EXISTS post",
                "DROP TABLE IF EXISTS member"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class CommentQueryRepositoryTest {

    @Autowired
    private CommentQueryRepository commentQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsOnlyVisiblePostContextIncludingNullSystemAuthor() {
        insertPost(154L, 7L, "ACTIVE", null);
        insertPost(155L, null, "ACTIVE", null);
        insertPost(156L, 7L, "HIDDEN", null);
        insertPost(
                157L,
                7L,
                "ACTIVE",
                Instant.parse("2026-07-25T08:00:00Z")
        );

        assertThat(commentQueryRepository
                .findVisiblePostContext(154L)
                .orElseThrow()
                .authorId()).isEqualTo(7L);
        assertThat(commentQueryRepository
                .findVisiblePostContext(155L)
                .orElseThrow()
                .authorId()).isNull();
        assertThat(commentQueryRepository
                .findVisiblePostContext(156L)).isEmpty();
        assertThat(commentQueryRepository
                .findVisiblePostContext(157L)).isEmpty();
    }

    @Test
    void pagesByCreatedAtAndIdUsingDeletedCursorAsBoundary() {
        insertMember(7L, "작성자", "ACTIVE", null);
        insertPost(154L, 7L, "ACTIVE", null);
        Instant first = Instant.parse("2026-07-25T07:00:00Z");
        Instant cursorTime = Instant.parse("2026-07-25T08:00:00Z");
        Instant last = Instant.parse("2026-07-25T09:00:00Z");
        insertComment(20L, 154L, 7L, first, null);
        insertComment(5L, 154L, 7L, cursorTime, cursorTime);
        insertComment(10L, 154L, 7L, last, null);

        List<CommentListRow> firstPage =
                commentQueryRepository.findComments(
                        154L,
                        null,
                        10
                );
        CommentCursorRow cursor = commentQueryRepository
                .findCursor(154L, 5L)
                .orElseThrow();
        List<CommentListRow> nextPage =
                commentQueryRepository.findComments(
                        154L,
                        cursor,
                        10
                );

        assertThat(firstPage)
                .extracting(CommentListRow::commentId)
                .containsExactly(20L, 10L);
        assertThat(nextPage)
                .extracting(CommentListRow::commentId)
                .containsExactly(10L);
        assertThat(commentQueryRepository.countActiveComments(154L))
                .isEqualTo(2L);
    }

    @Test
    void returnsAuthorStateInSingleJoinedPageQuery() {
        Instant withdrawnAt =
                Instant.parse("2026-07-25T06:00:00Z");
        insertMember(
                7L,
                "탈퇴 전 닉네임",
                "WITHDRAWN",
                withdrawnAt
        );
        insertPost(154L, 7L, "ACTIVE", null);
        insertComment(
                36L,
                154L,
                7L,
                Instant.parse("2026-07-25T07:00:00Z"),
                null
        );

        CommentListRow row = commentQueryRepository
                .findComments(154L, null, 2)
                .get(0);

        assertThat(row.authorStatus())
                .isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(row.authorDeletedAt()).isEqualTo(withdrawnAt);
        assertThat(row.authorNickname()).isEqualTo("탈퇴 전 닉네임");
    }

    private void insertMember(
            Long id,
            String nickname,
            String status,
            Instant deletedAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO member (
                            id, nickname, profile_image_url,
                            selected_character_id, status, deleted_at
                        )
                        VALUES (?, ?, NULL, 'DURI', ?, ?)
                        """,
                id,
                nickname,
                status,
                timestamp(deletedAt)
        );
    }

    private void insertPost(
            Long id,
            Long authorId,
            String status,
            Instant deletedAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO post (
                            id, author_id, status, deleted_at
                        )
                        VALUES (?, ?, ?, ?)
                        """,
                id,
                authorId,
                status,
                timestamp(deletedAt)
        );
    }

    private void insertComment(
            Long id,
            Long postId,
            Long authorId,
            Instant createdAt,
            Instant deletedAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO post_comment (
                            id, post_id, author_id, content,
                            deleted_at, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                postId,
                authorId,
                "댓글 " + id,
                timestamp(deletedAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
