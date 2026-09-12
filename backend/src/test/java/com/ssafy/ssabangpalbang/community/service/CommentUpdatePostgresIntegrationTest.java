package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.community.dto.request.CommentUpdateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.CommentResponse;
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

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
class CommentUpdatePostgresIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T08:20:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-comment-update-test",
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
    void updatesOnlyContentAndTimestampWithoutChangingMetrics() {
        Long memberId = insertMember();
        Long postId = insertPost(memberId);
        Long commentId = insertComment(
                postId,
                memberId,
                "수정 전 댓글"
        );
        insertLike(postId, memberId);
        Instant createdAt = currentInstant("created_at", commentId);
        long commentCount = commentCount(postId);
        long likeCount = likeCount(postId);
        long viewCount = viewCount(postId);
        when(clock.instant()).thenReturn(NOW);

        CommentResponse response = commentCommandService.update(
                memberId,
                commentId,
                new CommentUpdateRequest(
                        "\u00A0수정된 댓글\u200B"
                )
        );

        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.content()).isEqualTo("수정된 댓글");
        assertThat(response.isMine()).isTrue();
        assertThat(response.isPostAuthor()).isTrue();
        assertThat(response.canEdit()).isTrue();
        assertThat(response.canDelete()).isTrue();
        assertThat(response.createdAt().toInstant())
                .isEqualTo(createdAt);
        assertThat(response.updatedAt().toInstant()).isEqualTo(NOW);

        assertThat(currentString("content", commentId))
                .isEqualTo("수정된 댓글");
        assertThat(currentInstant("updated_at", commentId))
                .isEqualTo(NOW);
        assertThat(currentInstant("created_at", commentId))
                .isEqualTo(createdAt);
        assertThat(currentInstant("deleted_at", commentId)).isNull();
        assertThat(currentLong("post_id", commentId)).isEqualTo(postId);
        assertThat(currentLong("author_id", commentId))
                .isEqualTo(memberId);
        assertThat(commentCount(postId)).isEqualTo(commentCount);
        assertThat(likeCount(postId)).isEqualTo(likeCount);
        assertThat(viewCount(postId)).isEqualTo(viewCount);
    }

    @Test
    void concurrentPatchesUseLastWriteWinsInLockOrder()
            throws Exception {
        Long memberId = insertMember();
        Long postId = insertPost(memberId);
        Long commentId = insertComment(
                postId,
                memberId,
                "동시 수정 전"
        );
        AtomicLong clockSequence = new AtomicLong();
        when(clock.instant()).thenAnswer(invocation ->
                NOW.plus(
                        clockSequence.getAndIncrement(),
                        ChronoUnit.MICROS
                )
        );
        AtomicLong completionSequence = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<CompletedUpdate> completed;
        try {
            Future<CompletedUpdate> first = executor.submit(() ->
                    updateOutcome(
                            memberId,
                            commentId,
                            "첫 번째 수정",
                            start,
                            completionSequence
                    )
            );
            Future<CompletedUpdate> second = executor.submit(() ->
                    updateOutcome(
                            memberId,
                            commentId,
                            "두 번째 수정",
                            start,
                            completionSequence
                    )
            );

            start.countDown();
            completed = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }

        CompletedUpdate last = completed.stream()
                .max(Comparator.comparingLong(
                        CompletedUpdate::completionOrder
                ))
                .orElseThrow();
        assertThat(currentString("content", commentId))
                .isEqualTo(last.response().content());
        assertThat(currentInstant("updated_at", commentId))
                .isEqualTo(last.response().updatedAt().toInstant());
    }

    @Test
    void concurrentPatchAndDeleteNeverReviveComment()
            throws Exception {
        Long memberId = insertMember();
        Long postId = insertPost(memberId);
        Long commentId = insertComment(
                postId,
                memberId,
                "삭제 경쟁 전"
        );
        AtomicLong clockSequence = new AtomicLong();
        when(clock.instant()).thenAnswer(invocation ->
                NOW.plus(
                        clockSequence.getAndIncrement(),
                        ChronoUnit.MICROS
                )
        );
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        String updateOutcome;
        try {
            Future<String> update = executor.submit(() -> {
                start.await();
                try {
                    commentCommandService.update(
                            memberId,
                            commentId,
                            new CommentUpdateRequest("삭제 경쟁 수정")
                    );
                    return "SUCCESS";
                } catch (BusinessException exception) {
                    return exception.getErrorCode().getCode();
                }
            });
            Future<?> delete = executor.submit(() -> {
                start.await();
                commentCommandService.delete(memberId, commentId);
                return null;
            });

            start.countDown();
            updateOutcome = update.get(20, TimeUnit.SECONDS);
            delete.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(updateOutcome).isIn(
                "SUCCESS",
                ErrorCode.COMMENT_ALREADY_DELETED.getCode()
        );
        assertThat(currentInstant("deleted_at", commentId)).isNotNull();
        assertThat(currentString("content", commentId)).isEqualTo(
                "SUCCESS".equals(updateOutcome)
                        ? "삭제 경쟁 수정"
                        : "삭제 경쟁 전"
        );
    }

    private CompletedUpdate updateOutcome(
            Long memberId,
            Long commentId,
            String content,
            CountDownLatch start,
            AtomicLong completionSequence
    ) throws InterruptedException {
        start.await();
        CommentResponse response = commentCommandService.update(
                memberId,
                commentId,
                new CommentUpdateRequest(content)
        );
        return new CompletedUpdate(
                response,
                completionSequence.getAndIncrement()
        );
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
                "comment-update-" + suffix + "@example.com",
                "update-" + suffix
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
                            '댓글 수정 통합 테스트',
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

    private void insertLike(Long postId, Long memberId) {
        jdbcTemplate.update(
                """
                        INSERT INTO post_like (post_id, member_id)
                        VALUES (?, ?)
                        """,
                postId,
                memberId
        );
    }

    private long commentCount(Long postId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT comment_count
                        FROM post_hot_metric
                        WHERE post_id = ?
                        """,
                Long.class,
                postId
        );
    }

    private long likeCount(Long postId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT like_count
                        FROM post_hot_metric
                        WHERE post_id = ?
                        """,
                Long.class,
                postId
        );
    }

    private long viewCount(Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT view_count FROM post WHERE id = ?",
                Long.class,
                postId
        );
    }

    private Instant currentInstant(String column, Long commentId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM post_comment WHERE id = ?",
                (resultSet, rowNumber) -> {
                    java.sql.Timestamp timestamp =
                            resultSet.getTimestamp(column);
                    return timestamp == null
                            ? null
                            : timestamp.toInstant();
                },
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

    private record CompletedUpdate(
            CommentResponse response,
            long completionOrder
    ) {
    }
}
