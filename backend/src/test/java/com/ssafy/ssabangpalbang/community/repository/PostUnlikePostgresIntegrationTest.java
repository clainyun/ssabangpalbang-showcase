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
import java.util.ArrayList;
import java.util.List;
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
class PostUnlikePostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-post-unlike-test",
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
    void repeatedUnlikeDeletesOnlyMyRelation() {
        Long memberId = insertMember();
        Long otherMemberId = insertMember();
        Long postId = insertPost(memberId, "now()", 0L);
        postCommandRepository
                .insertLikeIfAbsent(postId, memberId)
                .orElseThrow();
        postCommandRepository
                .insertLikeIfAbsent(postId, otherMemberId)
                .orElseThrow();

        assertThat(postCommandRepository.deleteLike(postId, memberId))
                .isEqualTo(1);
        assertThat(postCommandRepository.deleteLike(postId, memberId))
                .isZero();

        assertThat(likeCount(postId, memberId)).isZero();
        assertThat(likeCount(postId, otherMemberId)).isEqualTo(1L);
        PostInteractionRow interactions = postQueryRepository
                .findInteractions(postId, memberId)
                .orElseThrow();
        assertThat(interactions.likedByMe()).isFalse();
        assertThat(interactions.likeCount()).isEqualTo(1L);
    }

    @Test
    void concurrentUnlikeRequestsDeleteExactlyOnce() throws Exception {
        Long memberId = insertMember();
        Long postId = insertPost(memberId, "now()", 0L);
        postCommandRepository
                .insertLikeIfAbsent(postId, memberId)
                .orElseThrow();
        int requestCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return postCommandRepository.deleteLike(
                            postId,
                            memberId
                    );
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int deleted = 0;
            for (Future<Integer> future : futures) {
                deleted += future.get(15, TimeUnit.SECONDS);
            }
            assertThat(deleted).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(likeCount(postId, memberId)).isZero();
    }

    @Test
    void unlikeRecalculatesHotWhileOldPostRemainsOutsideHotView() {
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

        PostInteractionRow recentBefore = postQueryRepository
                .findInteractions(recentPostId, memberId)
                .orElseThrow();
        assertThat(recentBefore.hot()).isTrue();
        assertThat(recentBefore.likeCount()).isEqualTo(1L);

        assertThat(postCommandRepository.deleteLike(
                recentPostId,
                memberId
        )).isEqualTo(1);
        assertThat(postCommandRepository.deleteLike(
                oldPostId,
                memberId
        )).isEqualTo(1);

        PostInteractionRow recentAfter = postQueryRepository
                .findInteractions(recentPostId, memberId)
                .orElseThrow();
        PostInteractionRow oldAfter = postQueryRepository
                .findInteractions(oldPostId, memberId)
                .orElseThrow();

        assertThat(recentAfter.likeCount()).isZero();
        assertThat(recentAfter.likedByMe()).isFalse();
        assertThat(recentAfter.hot()).isFalse();
        assertThat(recentAfter.hotScore())
                .isLessThan(recentBefore.hotScore());
        assertThat(oldAfter.likeCount()).isZero();
        assertThat(oldAfter.hot()).isNull();
        assertThat(oldAfter.hotScore()).isNull();
        assertThat(oldAfter.hotRank()).isNull();
    }

    @Test
    void laterFailureRollsBackDeletedLike() {
        Long memberId = insertMember();
        Long postId = insertPost(memberId, "now()", 0L);
        postCommandRepository
                .insertLikeIfAbsent(postId, memberId)
                .orElseThrow();
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    int deleted = postCommandRepository.deleteLike(
                            postId,
                            memberId
                    );
                    assertThat(deleted).isEqualTo(1);
                    throw new IllegalStateException(
                            "metrics lookup failed"
                    );
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(likeCount(postId, memberId)).isEqualTo(1L);
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
                "post-unlike-" + suffix + "@example.com",
                "unlike-" + suffix
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
                    '좋아요 해제 테스트',
                    '좋아요 해제 테스트 본문',
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
}
