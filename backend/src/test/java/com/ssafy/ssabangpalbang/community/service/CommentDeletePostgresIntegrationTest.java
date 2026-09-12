package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.community.dto.response.CommentDeleteResponse;
import com.ssafy.ssabangpalbang.community.repository.CommentCommandRepositoryImpl;
import com.ssafy.ssabangpalbang.community.repository.CommentQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Tag("postgres")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
@Import({
        CommentCommandService.class,
        CommentCommandRepositoryImpl.class,
        CommentQueryRepository.class,
        PostQueryRepository.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommentDeletePostgresIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T08:25:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-comment-delete-test",
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
    private CommentCommandService commentCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    @Test
    void softDeletePreservesCommentAndRecomputesPostMetrics() {
        Long memberId = insertMember();
        Long postId = insertPost(memberId);
        Long commentId = insertComment(
                postId,
                memberId,
                "삭제할 댓글"
        );
        insertComment(postId, memberId, "남길 댓글");
        BigDecimal scoreBefore = hotScore(postId);
        when(clock.instant()).thenReturn(NOW);

        CommentDeleteResponse response = commentCommandService.delete(
                memberId,
                commentId
        );

        assertThat(response.postAvailable()).isTrue();
        assertThat(response.postMetrics().commentCount()).isEqualTo(1L);
        assertThat(response.postMetrics().likeCount()).isZero();
        assertThat(response.postMetrics().viewCount()).isEqualTo(1L);
        assertThat(scoreBefore.subtract(
                response.postMetrics().hotScore()
        )).isCloseTo(
                new BigDecimal("3.00"),
                org.assertj.core.data.Offset.offset(
                        new BigDecimal("0.02")
                )
        );
        assertThat(currentInstant("deleted_at", commentId))
                .isEqualTo(NOW);
        assertThat(currentInstant("updated_at", commentId))
                .isEqualTo(NOW);
        assertThat(currentString("content", commentId))
                .isEqualTo("삭제할 댓글");
        assertThat(currentLong("author_id", commentId))
                .isEqualTo(memberId);
        assertThat(currentLong("post_id", commentId))
                .isEqualTo(postId);
    }

    @Test
    void concurrentRequestsProduceOneSuccessAndOneConflict()
            throws Exception {
        Long memberId = insertMember();
        Long postId = insertPost(memberId);
        Long commentId = insertComment(
                postId,
                memberId,
                "동시 삭제 댓글"
        );
        when(clock.instant()).thenReturn(NOW);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() ->
                    deleteOutcome(memberId, commentId, start)
            );
            Future<String> second = executor.submit(() ->
                    deleteOutcome(memberId, commentId, start)
            );

            start.countDown();
            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(
                            "SUCCESS",
                            ErrorCode.COMMENT_ALREADY_DELETED.getCode()
                    );
        } finally {
            executor.shutdownNow();
        }

        assertThat(currentInstant("deleted_at", commentId))
                .isEqualTo(NOW);
    }

    @Test
    void hiddenOriginalStillAllowsDeletionWithUnavailableMetrics() {
        assertUnavailableOriginalDeletion(true);
    }

    @Test
    void deletedOriginalStillAllowsDeletionWithUnavailableMetrics() {
        assertUnavailableOriginalDeletion(false);
    }

    private void assertUnavailableOriginalDeletion(boolean hidden) {
        Long memberId = insertMember();
        Long postId = insertPost(memberId);
        Long commentId = insertComment(
                postId,
                memberId,
                "접근 불가 원문의 댓글"
        );
        if (hidden) {
            jdbcTemplate.update(
                    "UPDATE post SET status = 'HIDDEN' WHERE id = ?",
                    postId
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE post SET deleted_at = NOW() WHERE id = ?",
                    postId
            );
        }
        when(clock.instant()).thenReturn(NOW);

        CommentDeleteResponse response = commentCommandService.delete(
                memberId,
                commentId
        );

        assertThat(response.postAvailable()).isFalse();
        assertThat(response.postMetrics().commentCount()).isZero();
        assertThat(response.postMetrics().isHot()).isFalse();
        assertThat(response.postMetrics().hotScore()).isNull();
        assertThat(currentInstant("deleted_at", commentId))
                .isEqualTo(NOW);
    }

    private String deleteOutcome(
            Long memberId,
            Long commentId,
            CountDownLatch start
    ) throws InterruptedException {
        start.await();
        try {
            commentCommandService.delete(memberId, commentId);
            return "SUCCESS";
        } catch (BusinessException exception) {
            return exception.getErrorCode().getCode();
        }
    }

    private Long insertMember() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO member (email, nickname)
                        VALUES (?, ?)
                        RETURNING id
                        """,
                Long.class,
                "comment-delete-" + suffix + "@example.com",
                "delete-" + suffix
        );
    }

    private Long insertPost(Long memberId) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO post (
                            board_type,
                            author_id,
                            title,
                            content,
                            status,
                            is_auto_report,
                            view_count
                        )
                        VALUES (
                            'FREE',
                            ?,
                            '댓글 삭제 통합 테스트',
                            '본문',
                            'ACTIVE',
                            FALSE,
                            1
                        )
                        RETURNING id
                        """,
                Long.class,
                memberId
        );
    }

    private Long insertComment(
            Long postId,
            Long memberId,
            String content
    ) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO post_comment (
                            post_id,
                            author_id,
                            content
                        )
                        VALUES (?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                postId,
                memberId,
                content
        );
    }

    private BigDecimal hotScore(Long postId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT hot_score
                        FROM post_hot_metric
                        WHERE post_id = ?
                        """,
                BigDecimal.class,
                postId
        );
    }

    private Instant currentInstant(String column, Long commentId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM post_comment WHERE id = ?",
                (resultSet, rowNumber) ->
                        resultSet.getTimestamp(column).toInstant(),
                commentId
        );
    }

    private Long currentLong(String column, Long commentId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM post_comment WHERE id = ?",
                Long.class,
                commentId
        );
    }

    private String currentString(String column, Long commentId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM post_comment WHERE id = ?",
                String.class,
                commentId
        );
    }
}
