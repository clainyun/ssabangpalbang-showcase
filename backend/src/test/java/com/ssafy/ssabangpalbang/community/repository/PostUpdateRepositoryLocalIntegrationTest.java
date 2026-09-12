package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.domain.Post;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(
        named = "RUN_LOCAL_INFRA_TESTS",
        matches = "true"
)
@Import(PostCommandRepositoryImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostUpdateRepositoryLocalIntegrationTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostCommandRepository postCommandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentPatchesAreSerializedByPostLock() throws Exception {
        Long postId = insertPost();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> inTransaction(() -> {
                Post post = lockedPost(postId);
                firstLocked.countDown();
                await(releaseFirst);
                post.updateTitle("첫 번째 수정");
                post.markUpdated(Instant.now());
            }));
            Future<?> second = executor.submit(() -> {
                await(firstLocked);
                secondStarted.countDown();
                inTransaction(() -> {
                    Post post = lockedPost(postId);
                    post.updateTitle("두 번째 수정");
                    post.markUpdated(Instant.now());
                });
            });

            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);

            assertThat(currentTitle(postId)).isEqualTo("두 번째 수정");
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void atomicDetailIncrementIsNotLostDuringPatch() throws Exception {
        Long postId = insertPost();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch postLocked = new CountDownLatch(1);
        CountDownLatch incrementStarted = new CountDownLatch(1);
        CountDownLatch releasePatch = new CountDownLatch(1);

        try {
            Future<?> patch = executor.submit(() -> inTransaction(() -> {
                Post post = lockedPost(postId);
                postLocked.countDown();
                await(releasePatch);
                post.updateContent("수정 본문");
                post.markUpdated(Instant.now());
            }));
            Future<Long> increment = executor.submit(() -> {
                await(postLocked);
                incrementStarted.countDown();
                return postCommandRepository
                        .incrementViewCountIfVisible(postId)
                        .orElseThrow();
            });

            assertThat(incrementStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(increment.isDone()).isFalse();
            releasePatch.countDown();
            patch.get(30, TimeUnit.SECONDS);
            assertThat(increment.get(30, TimeUnit.SECONDS)).isEqualTo(1L);

            assertThat(currentContent(postId)).isEqualTo("수정 본문");
            assertThat(currentViewCount(postId)).isEqualTo(1L);
        } finally {
            releasePatch.countDown();
            executor.shutdownNow();
        }
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> action.run());
    }

    private Post lockedPost(Long postId) {
        return postRepository.findByIdForUpdate(postId).orElseThrow();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Long insertPost() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Long memberId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO member (
                            email, password_hash, nickname, status
                        )
                        VALUES (?, 'encoded-password', ?, 'ACTIVE')
                        RETURNING id
                        """,
                Long.class,
                "post-update-" + suffix + "@example.com",
                "update-" + suffix
        );
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO post (
                            board_type, author_id, title, content,
                            status, is_auto_report, view_count
                        )
                        VALUES (
                            'FREE', ?, '기존 제목', '기존 본문',
                            'ACTIVE', FALSE, 0
                        )
                        RETURNING id
                        """,
                Long.class,
                memberId
        );
    }

    private String currentTitle(Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT title FROM post WHERE id = ?",
                String.class,
                postId
        );
    }

    private String currentContent(Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT content FROM post WHERE id = ?",
                String.class,
                postId
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
}
