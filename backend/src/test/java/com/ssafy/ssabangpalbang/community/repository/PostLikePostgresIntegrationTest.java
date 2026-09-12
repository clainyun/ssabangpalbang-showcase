package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("postgres")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({
        PostCommandRepositoryImpl.class,
        PostQueryRepository.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostLikePostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-post-like-test",
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
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
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
    private PostCommandRepository postCommandRepository;

    @Autowired
    private PostQueryRepository postQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void repeatedLikeKeepsOneRowAndOriginalTimestamp() {
        Long memberId = insertMember();
        Long postId = insertPost(memberId, "now()", 0L);

        Instant firstLikedAt = postCommandRepository
                .insertLikeIfAbsent(postId, memberId)
                .orElseThrow();
        assertThat(postCommandRepository.insertLikeIfAbsent(
                postId,
                memberId
        )).isEmpty();

        assertThat(likeCount(postId, memberId)).isEqualTo(1L);
        assertThat(postCommandRepository.findLikedAt(postId, memberId))
                .contains(firstLikedAt);
    }

    @Test
    void concurrentLikeRequestsCreateExactlyOneRelation()
            throws Exception {
        Long memberId = insertMember();
        Long postId = insertPost(memberId, "now()", 0L);
        int requestCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<LikeAttempt>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    java.util.Optional<Instant> insertedAt =
                            postCommandRepository.insertLikeIfAbsent(
                                    postId,
                                    memberId
                            );
                    Instant likedAt = insertedAt.orElseGet(() ->
                            postCommandRepository.findLikedAt(
                                    postId,
                                    memberId
                            ).orElseThrow()
                    );
                    return new LikeAttempt(
                            insertedAt.isPresent(),
                            likedAt
                    );
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int inserted = 0;
            Set<Instant> likedAtValues = new HashSet<>();
            for (Future<LikeAttempt> future : futures) {
                LikeAttempt attempt = future.get(15, TimeUnit.SECONDS);
                if (attempt.inserted()) {
                    inserted++;
                }
                likedAtValues.add(attempt.likedAt());
            }
            assertThat(inserted).isEqualTo(1);
            assertThat(likedAtValues).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(likeCount(postId, memberId)).isEqualTo(1L);
        assertThat(postCommandRepository.findLikedAt(postId, memberId))
                .isPresent();
    }

    @Test
    void likeCanEnterHotWhileOldPostRemainsOutsideHotView() {
        Long memberId = insertMember();
        Long recentPostId = insertPost(memberId, "now()", 0L);
        Long oldPostId = insertPost(
                memberId,
                "now() - interval '8 days'",
                100L
        );

        postCommandRepository
                .insertLikeIfAbsent(recentPostId, memberId)
                .orElseThrow();
        postCommandRepository
                .insertLikeIfAbsent(oldPostId, memberId)
                .orElseThrow();

        PostInteractionRow recent = postQueryRepository
                .findInteractions(recentPostId, memberId)
                .orElseThrow();
        PostInteractionRow old = postQueryRepository
                .findInteractions(oldPostId, memberId)
                .orElseThrow();

        assertThat(recent.likeCount()).isEqualTo(1L);
        assertThat(recent.hot()).isTrue();
        assertThat(recent.hotScore()).isNotNull();
        assertThat(recent.hotRank()).isNotNull();
        assertThat(old.likeCount()).isEqualTo(1L);
        assertThat(old.hot()).isNull();
        assertThat(old.hotScore()).isNull();
        assertThat(old.hotRank()).isNull();
    }

    @Test
    void laterFailureRollsBackInsertedLike() {
        Long memberId = insertMember();
        Long postId = insertPost(memberId, "now()", 0L);
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    postCommandRepository
                            .insertLikeIfAbsent(postId, memberId)
                            .orElseThrow();
                    throw new IllegalStateException(
                            "metrics lookup failed"
                    );
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(likeCount(postId, memberId)).isZero();
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
                "post-like-" + suffix + "@example.com",
                "like-" + suffix
        );
    }

    private Long insertPost(
            Long memberId,
            String createdAtExpression,
            long viewCount
    ) {
        String sql = """
                INSERT INTO post (
                    board_type,
                    author_id,
                    title,
                    content,
                    status,
                    is_auto_report,
                    view_count,
                    created_at,
                    updated_at
                )
                VALUES (
                    'FREE',
                    ?,
                    '좋아요 테스트',
                    '좋아요 테스트 본문',
                    'ACTIVE',
                    FALSE,
                    ?,
                    %s,
                    %s
                )
                RETURNING id
                """.formatted(
                createdAtExpression,
                createdAtExpression
        );
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                memberId,
                viewCount
        );
    }

    private long likeCount(Long postId, Long memberId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM post_like
                        WHERE post_id = ?
                          AND member_id = ?
                        """,
                Long.class,
                postId,
                memberId
        );
        return count == null ? 0L : count;
    }

    private record LikeAttempt(boolean inserted, Instant likedAt) {
    }
}
