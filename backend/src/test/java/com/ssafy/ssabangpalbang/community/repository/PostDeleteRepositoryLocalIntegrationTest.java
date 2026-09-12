package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.domain.Post;
import org.junit.jupiter.api.AfterEach;
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
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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
class PostDeleteRepositoryLocalIntegrationTest {

    private static final Instant DELETE_TIME =
            Instant.parse("2026-07-29T08:00:00Z");

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostCommandRepository postCommandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUpFixtures() {
        jdbcTemplate.update(
                """
                        DELETE FROM post
                        WHERE author_id IN (
                            SELECT id
                            FROM member
                            WHERE email LIKE 'post-delete-%@example.com'
                        )
                        """
        );
        jdbcTemplate.update(
                """
                        DELETE FROM member
                        WHERE email LIKE 'post-delete-%@example.com'
                        """
        );
    }

    @Test
    void concurrentDeleteIsSerializedAndSecondSeesDeletion()
            throws Exception {
        Long postId = insertPost();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger secondBackendPid = new AtomicInteger();

        try {
            Future<?> first = executor.submit(() -> inTransaction(() -> {
                Post post = lockedPost(postId);
                firstLocked.countDown();
                await(releaseFirst);
                post.delete(DELETE_TIME);
            }));
            Future<Boolean> second = executor.submit(() -> {
                await(firstLocked);
                return inTransactionResult(() -> {
                    secondBackendPid.set(currentBackendPid());
                    secondStarted.countDown();
                    return lockedPost(postId).isDeleted();
                });
            });

            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitLockWait(secondBackendPid.get());
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();
            first.get(30, TimeUnit.SECONDS);

            assertThat(second.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(currentInstant("deleted_at", postId))
                    .isEqualTo(DELETE_TIME);
            assertThat(currentInstant("updated_at", postId))
                    .isEqualTo(DELETE_TIME);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void patchAfterDeleteObservesDeletedStateWithoutChangingContent()
            throws Exception {
        Long postId = insertPost();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch deleteLocked = new CountDownLatch(1);
        CountDownLatch patchStarted = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        AtomicInteger patchBackendPid = new AtomicInteger();

        try {
            Future<?> delete = executor.submit(() -> inTransaction(() -> {
                Post post = lockedPost(postId);
                deleteLocked.countDown();
                await(releaseDelete);
                post.delete(DELETE_TIME);
            }));
            Future<Boolean> patch = executor.submit(() -> {
                await(deleteLocked);
                return inTransactionResult(() -> {
                    patchBackendPid.set(currentBackendPid());
                    patchStarted.countDown();
                    Post post = lockedPost(postId);
                    if (post.isDeleted()) {
                        return false;
                    }
                    post.updateTitle("경쟁 수정");
                    return true;
                });
            });

            assertThat(patchStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitLockWait(patchBackendPid.get());
            assertThat(patch.isDone()).isFalse();
            releaseDelete.countDown();
            delete.get(30, TimeUnit.SECONDS);

            assertThat(patch.get(30, TimeUnit.SECONDS)).isFalse();
            assertThat(currentString("title", postId))
                    .isEqualTo("기존 제목");
        } finally {
            releaseDelete.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void detailIncrementWaitsForDeleteAndThenSkipsDeletedPost()
            throws Exception {
        Long postId = insertPost();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch deleteLocked = new CountDownLatch(1);
        CountDownLatch incrementStarted = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        AtomicInteger incrementBackendPid = new AtomicInteger();

        try {
            Future<?> delete = executor.submit(() -> inTransaction(() -> {
                Post post = lockedPost(postId);
                deleteLocked.countDown();
                await(releaseDelete);
                post.delete(DELETE_TIME);
            }));
            Future<OptionalLong> increment = executor.submit(() -> {
                await(deleteLocked);
                return inTransactionResult(() -> {
                    incrementBackendPid.set(currentBackendPid());
                    incrementStarted.countDown();
                    return postCommandRepository
                            .incrementViewCountIfVisible(postId);
                });
            });

            assertThat(incrementStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitLockWait(incrementBackendPid.get());
            assertThat(increment.isDone()).isFalse();
            releaseDelete.countDown();
            delete.get(30, TimeUnit.SECONDS);

            assertThat(increment.get(30, TimeUnit.SECONDS)).isEmpty();
            assertThat(currentLong("view_count", postId)).isZero();
        } finally {
            releaseDelete.countDown();
            executor.shutdownNow();
        }
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> action.run());
    }

    private <T> T inTransactionResult(Supplier<T> action) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> action.get());
    }

    private Post lockedPost(Long postId) {
        return postRepository.findByIdForUpdate(postId).orElseThrow();
    }

    private int currentBackendPid() {
        Integer backendPid = jdbcTemplate.queryForObject(
                "SELECT pg_backend_pid()",
                Integer.class
        );
        if (backendPid == null) {
            throw new IllegalStateException(
                    "PostgreSQL backend PID를 확인할 수 없습니다."
            );
        }
        return backendPid;
    }

    private void awaitLockWait(int backendPid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Boolean waitingForLock = jdbcTemplate.queryForObject(
                    """
                            SELECT wait_event_type = 'Lock'
                            FROM pg_stat_activity
                            WHERE pid = ?
                            """,
                    Boolean.class,
                    backendPid
            );
            if (Boolean.TRUE.equals(waitingForLock)) {
                return;
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        throw new IllegalStateException(
                "경쟁 트랜잭션의 행 잠금 대기를 확인하지 못했습니다."
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "동시성 테스트 대기 시간 초과"
                );
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
                "post-delete-" + suffix + "@example.com",
                "delete-" + suffix
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

    private Instant currentInstant(String column, Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM post WHERE id = ?",
                Instant.class,
                postId
        );
    }

    private String currentString(String column, Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM post WHERE id = ?",
                String.class,
                postId
        );
    }

    private long currentLong(String column, Long postId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM post WHERE id = ?",
                Long.class,
                postId
        );
        return value == null ? 0L : value;
    }
}
