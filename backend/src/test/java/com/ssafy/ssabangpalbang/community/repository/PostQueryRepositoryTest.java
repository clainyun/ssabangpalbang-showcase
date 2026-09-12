package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.dto.request.PostSort;
import com.ssafy.ssabangpalbang.community.repository.projection.PostDetailRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostListKeyRow;
import com.ssafy.ssabangpalbang.community.support.PostCursor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(PostQueryRepository.class)
@Sql(statements = {
        "DROP TABLE IF EXISTS post_like",
        "DROP TABLE IF EXISTS post_comment",
        "DROP TABLE IF EXISTS post_hot_metric",
        "DROP TABLE IF EXISTS post",
        "DROP TABLE IF EXISTS report",
        "DROP TABLE IF EXISTS apartment",
        "DROP TABLE IF EXISTS member",
        "CREATE TABLE member ("
                + "id BIGINT PRIMARY KEY, "
                + "nickname VARCHAR(50) NOT NULL, "
                + "profile_image_url VARCHAR(500), "
                + "selected_character_id VARCHAR(20) NOT NULL, "
                + "status VARCHAR(20) NOT NULL, "
                + "deleted_at TIMESTAMP WITH TIME ZONE)",
        "CREATE TABLE apartment ("
                + "id BIGINT PRIMARY KEY, "
                + "name VARCHAR(200) NOT NULL, "
                + "address VARCHAR(300))",
        "CREATE TABLE report ("
                + "id BIGINT PRIMARY KEY, "
                + "status VARCHAR(20) NOT NULL)",
        "CREATE TABLE post ("
                + "id BIGINT PRIMARY KEY, "
                + "board_type VARCHAR(20) NOT NULL, "
                + "author_id BIGINT, "
                + "title VARCHAR(200) NOT NULL, "
                + "content TEXT NOT NULL, "
                + "status VARCHAR(20) NOT NULL, "
                + "is_auto_report BOOLEAN NOT NULL, "
                + "report_id BIGINT, "
                + "apartment_id BIGINT, "
                + "view_count BIGINT NOT NULL DEFAULT 0, "
                + "deleted_at TIMESTAMP WITH TIME ZONE, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
        "CREATE TABLE post_hot_metric ("
                + "post_id BIGINT PRIMARY KEY, "
                + "board_type VARCHAR(20) NOT NULL DEFAULT 'FREE', "
                + "view_count BIGINT NOT NULL DEFAULT 0, "
                + "like_count BIGINT NOT NULL DEFAULT 0, "
                + "comment_count BIGINT NOT NULL DEFAULT 0, "
                + "hot_score NUMERIC(20, 2) NOT NULL, "
                + "is_hot BOOLEAN NOT NULL, "
                + "hot_rank BIGINT NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE "
                + "NOT NULL DEFAULT CURRENT_TIMESTAMP)",
        "CREATE TABLE post_like (post_id BIGINT, member_id BIGINT)",
        "CREATE TABLE post_comment ("
                + "post_id BIGINT, "
                + "deleted_at TIMESTAMP WITH TIME ZONE)"
})
@Sql(
        statements = {
                "DROP TABLE IF EXISTS post_like",
                "DROP TABLE IF EXISTS post_comment",
                "DROP TABLE IF EXISTS post_hot_metric",
                "DROP TABLE IF EXISTS post",
                "DROP TABLE IF EXISTS report",
                "DROP TABLE IF EXISTS apartment",
                "DROP TABLE IF EXISTS member"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class PostQueryRepositoryTest {

    @Autowired
    private PostQueryRepository postQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsVisibleProjectionAndPersonalizedReactionCounts() {
        jdbcTemplate.update("""
                INSERT INTO member (
                    id, nickname, selected_character_id, status
                ) VALUES (7, '집보는다람쥐', 'JIPKONG', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO apartment (id, name, address)
                VALUES (15, '래미안 옥수 리버젠', '서울 성동구')
                """);
        jdbcTemplate.update("""
                INSERT INTO post (
                    id, board_type, author_id, title, content, status,
                    is_auto_report, apartment_id, created_at, updated_at
                ) VALUES (
                    154, 'INFORMATION', 7, '제목', '본문', 'ACTIVE',
                    FALSE, 15, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO post_hot_metric (
                    post_id, hot_score, is_hot, hot_rank
                ) VALUES (154, 25.50, TRUE, 2)
                """);
        jdbcTemplate.update(
                "INSERT INTO post_like (post_id, member_id) VALUES (154, 7)"
        );
        jdbcTemplate.update(
                "INSERT INTO post_like (post_id, member_id) VALUES (154, 8)"
        );
        jdbcTemplate.update(
                "INSERT INTO post_comment (post_id) VALUES (154)"
        );
        jdbcTemplate.update("""
                INSERT INTO post_comment (post_id, deleted_at)
                VALUES (154, CURRENT_TIMESTAMP)
                """);

        PostDetailRow row = postQueryRepository
                .findVisibleDetail(154L)
                .orElseThrow();
        PostInteractionRow interactions = postQueryRepository
                .findInteractions(154L, 7L)
                .orElseThrow();

        assertThat(row.authorNickname()).isEqualTo("집보는다람쥐");
        assertThat(row.apartmentAddress()).isEqualTo("서울 성동구");
        assertThat(interactions.hotScore()).isEqualByComparingTo(
                new BigDecimal("25.50")
        );
        assertThat(interactions.hot()).isTrue();
        assertThat(interactions.likeCount()).isEqualTo(2L);
        assertThat(interactions.commentCount()).isEqualTo(1L);
        assertThat(interactions.likedByMe()).isTrue();
        assertThat(postQueryRepository
                .findInteractions(154L, 9L)
                .orElseThrow()
                .likedByMe()).isFalse();
    }

    @Test
    void hidesHiddenAndSoftDeletedPosts() {
        jdbcTemplate.update("""
                INSERT INTO post (
                    id, board_type, title, content, status,
                    is_auto_report, deleted_at, created_at, updated_at
                ) VALUES (
                    154, 'FREE', '숨김', '본문', 'HIDDEN',
                    TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO post (
                    id, board_type, title, content, status,
                    is_auto_report, deleted_at, created_at, updated_at
                ) VALUES (
                    155, 'FREE', '삭제', '본문', 'ACTIVE',
                    TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """);

        assertThat(postQueryRepository.findVisibleDetail(154L)).isEmpty();
        assertThat(postQueryRepository.findVisibleDetail(155L)).isEmpty();
    }

    @Test
    void searchesTitleAndContentWithLiteralLikeCharacters() {
        insertListPost(
                201L,
                "FREE",
                "100%_확인",
                "일반 본문",
                "ACTIVE",
                null,
                "2026-07-29T07:30:00Z"
        );
        insertListPost(
                202L,
                "INFORMATION",
                "일반 제목",
                "본문에도 100%_표기가 있습니다",
                "ACTIVE",
                null,
                "2026-07-29T07:29:00Z"
        );
        insertListPost(
                203L,
                "FREE",
                "100AB확인",
                "와일드카드로 오인하면 안 됩니다",
                "ACTIVE",
                null,
                "2026-07-29T07:28:00Z"
        );
        insertListPost(
                204L,
                "FREE",
                "100%_숨김",
                "검색 제외",
                "HIDDEN",
                null,
                "2026-07-29T07:27:00Z"
        );
        insertListPost(
                205L,
                "FREE",
                "경로 C:\\temp 확인",
                "escape 문자 검색",
                "ACTIVE",
                null,
                "2026-07-29T07:26:00Z"
        );

        List<PostListKeyRow> result =
                postQueryRepository.findListKeys(
                        null,
                        PostSort.LATEST,
                        "%_",
                        null,
                        10
                );

        assertThat(result)
                .extracting(PostListKeyRow::postId)
                .containsExactly(201L, 202L);
        assertThat(postQueryRepository.findListKeys(
                null,
                PostSort.LATEST,
                "C:\\temp",
                null,
                10
        )).extracting(PostListKeyRow::postId)
                .containsExactly(205L);
    }

    @Test
    void appliesLatestLexicographicCursorWithPostIdTieBreak() {
        String sameTime = "2026-07-29T07:30:00Z";
        insertListPost(
                210L, "FREE", "최신 1", "본문",
                "ACTIVE", null, sameTime
        );
        insertListPost(
                209L, "FREE", "최신 2", "본문",
                "ACTIVE", null, sameTime
        );
        insertListPost(
                208L, "FREE", "최신 3", "본문",
                "ACTIVE", null, "2026-07-29T07:29:00Z"
        );

        PostCursor cursor = PostCursor.latest(
                Instant.parse(sameTime),
                209L,
                "hash"
        );
        List<PostListKeyRow> result =
                postQueryRepository.findListKeys(
                        null,
                        PostSort.LATEST,
                        null,
                        cursor,
                        10
                );

        assertThat(result)
                .extracting(PostListKeyRow::postId)
                .containsExactly(208L);
    }

    @Test
    void appliesHotOrderingThresholdAndCursorTieBreak() {
        String sameTime = "2026-07-29T07:30:00Z";
        insertListPost(
                220L, "FREE", "HOT 1", "본문",
                "ACTIVE", null, sameTime
        );
        insertListPost(
                221L, "FREE", "HOT 2", "본문",
                "ACTIVE", null, sameTime
        );
        insertListPost(
                222L, "FREE", "미달", "본문",
                "ACTIVE", null, sameTime
        );
        insertHotMetric(220L, "30.00", true, 2L, sameTime);
        insertHotMetric(221L, "30.00", true, 1L, sameTime);
        insertHotMetric(222L, "19.99", false, 3L, sameTime);

        List<PostListKeyRow> firstPage =
                postQueryRepository.findListKeys(
                        null,
                        PostSort.HOT,
                        null,
                        null,
                        10
                );
        assertThat(firstPage)
                .extracting(PostListKeyRow::postId)
                .containsExactly(221L, 220L);

        List<PostListKeyRow> secondPage =
                postQueryRepository.findListKeys(
                        null,
                        PostSort.HOT,
                        null,
                        PostCursor.hot(
                                new BigDecimal("30.00"),
                                Instant.parse(sameTime),
                                221L,
                                "hash"
                        ),
                        10
                );
        assertThat(secondPage)
                .extracting(PostListKeyRow::postId)
                .containsExactly(220L);
    }

    private void insertListPost(
            Long id,
            String boardType,
            String title,
            String content,
            String status,
            Instant deletedAt,
            String createdAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO post (
                            id, board_type, title, content, status,
                            is_auto_report, deleted_at,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, FALSE, ?, ?, ?)
                        """,
                id,
                boardType,
                title,
                content,
                status,
                deletedAt,
                Instant.parse(createdAt),
                Instant.parse(createdAt)
        );
    }

    private void insertHotMetric(
            Long postId,
            String score,
            boolean hot,
            Long rank,
            String createdAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO post_hot_metric (
                            post_id, hot_score, is_hot,
                            hot_rank, created_at
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                postId,
                new BigDecimal(score),
                hot,
                rank,
                Instant.parse(createdAt)
        );
    }
}
