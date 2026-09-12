package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.dto.request.CommentListCondition;
import com.ssafy.ssabangpalbang.community.dto.response.CommentListResponse;
import com.ssafy.ssabangpalbang.community.service.CommentQueryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
@Import({
        CommentQueryService.class,
        CommentQueryRepository.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommentListPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-comment-list-test",
                false
        ).withDockerfile(Path.of(
                "..",
                "infra",
                "postgres",
                "Dockerfile"
        ));
        String imageId = image.get();
        return new PostgreSQLContainer<>(
                DockerImageName.parse(imageId)
                        .asCompatibleSubstituteFor("postgres")
        )
                .withDatabaseName("ssabangpalbang_test")
                .withUsername("test")
                .withPassword("test");
    }

    @DynamicPropertySource
    static void registerDynamicProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
        registry.add(
                "spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver"
        );
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration"
        );
    }

    @Autowired
    private CommentQueryService commentQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void compositeCursorUsesDeletedBoundaryWithoutChangingViews() {
        Long memberId = insertMember();
        Long postId = insertPost(memberId);
        insertComment(
                20L,
                postId,
                memberId,
                Instant.parse("2026-07-25T07:00:00Z"),
                null
        );
        Instant deletedCursorTime =
                Instant.parse("2026-07-25T08:00:00Z");
        insertComment(
                5L,
                postId,
                memberId,
                deletedCursorTime,
                deletedCursorTime
        );
        insertComment(
                10L,
                postId,
                memberId,
                Instant.parse("2026-07-25T09:00:00Z"),
                null
        );

        CommentListResponse first =
                commentQueryService.getComments(
                        memberId,
                        postId,
                        new CommentListCondition(null, 1)
                );
        CommentListResponse afterDeletedCursor =
                commentQueryService.getComments(
                        memberId,
                        postId,
                        new CommentListCondition(5L, 20)
                );

        assertThat(first.content())
                .extracting(item -> item.commentId())
                .containsExactly(20L);
        assertThat(first.nextCursor()).isEqualTo(20L);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.totalCount()).isEqualTo(2L);
        assertThat(afterDeletedCursor.content())
                .extracting(item -> item.commentId())
                .containsExactly(10L);
        assertThat(afterDeletedCursor.totalCount()).isEqualTo(2L);
        assertThat(currentViewCount(postId)).isEqualTo(7L);
        assertThat(indexExists()).isTrue();
    }

    private Long insertMember() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO member (
                            email, password_hash, nickname, status
                        )
                        VALUES (?, 'encoded-password', ?, 'ACTIVE')
                        RETURNING id
                        """,
                Long.class,
                "comment-list-" + suffix + "@example.com",
                "댓글조회-" + suffix
        );
    }

    private Long insertPost(Long memberId) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO post (
                            board_type, author_id, title, content,
                            status, is_auto_report, view_count
                        )
                        VALUES (
                            'FREE', ?, '댓글 목록', '본문',
                            'ACTIVE', FALSE, 7
                        )
                        RETURNING id
                        """,
                Long.class,
                memberId
        );
    }

    private void insertComment(
            Long commentId,
            Long postId,
            Long memberId,
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
                commentId,
                postId,
                memberId,
                "댓글 " + commentId,
                deletedAt == null
                        ? null
                        : Timestamp.from(deletedAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private long currentViewCount(Long postId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT view_count FROM post WHERE id = ?",
                Long.class,
                postId
        );
        return count == null ? 0L : count;
    }

    private boolean indexExists() {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM pg_indexes
                            WHERE indexname =
                              'idx_post_comment_post_created_active'
                        )
                        """,
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }
}
