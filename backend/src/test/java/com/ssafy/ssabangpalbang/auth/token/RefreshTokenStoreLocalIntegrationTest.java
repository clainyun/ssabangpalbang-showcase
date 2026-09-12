package com.ssafy.ssabangpalbang.auth.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(
        named = "RUN_LOCAL_INFRA_TESTS",
        matches = "true"
)
class RefreshTokenStoreLocalIntegrationTest {

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void 동일한_Refresh_Token의_동시_회전은_하나만_성공한다() throws Exception {
        Long memberId = ThreadLocalRandom.current().nextLong(
                1_000_000L,
                Long.MAX_VALUE
        );
        String redisKey = "auth:refresh:" + memberId;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            refreshTokenStore.save(
                    memberId,
                    "old-refresh-token",
                    Duration.ofMinutes(5)
            );

            Future<Boolean> first = executor.submit(() -> rotateAfterStart(
                    ready,
                    start,
                    memberId,
                    "new-refresh-token-1"
            ));
            Future<Boolean> second = executor.submit(() -> rotateAfterStart(
                    ready,
                    start,
                    memberId,
                    "new-refresh-token-2"
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> results = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(
                    refreshTokenStore.matches(
                            memberId,
                            "new-refresh-token-1"
                    ) || refreshTokenStore.matches(
                            memberId,
                            "new-refresh-token-2"
                    )
            ).isTrue();
            assertThat(refreshTokenStore.matches(
                    memberId,
                    "old-refresh-token"
            )).isFalse();
        } finally {
            start.countDown();
            executor.shutdownNow();
            redisTemplate.delete(redisKey);
        }
    }

    @Test
    void 일치하는_Refresh_Token만_삭제하고_반복_폐기는_멱등_처리한다() {
        Long memberId = ThreadLocalRandom.current().nextLong(
                1_000_000L,
                Long.MAX_VALUE
        );
        String redisKey = "auth:refresh:" + memberId;

        try {
            refreshTokenStore.save(
                    memberId,
                    "active-refresh-token",
                    Duration.ofMinutes(5)
            );

            assertThat(refreshTokenStore.revoke(
                    memberId,
                    "different-refresh-token"
            )).isFalse();
            assertThat(refreshTokenStore.matches(
                    memberId,
                    "active-refresh-token"
            )).isTrue();

            assertThat(refreshTokenStore.revoke(
                    memberId,
                    "active-refresh-token"
            )).isTrue();
            assertThat(refreshTokenStore.matches(
                    memberId,
                    "active-refresh-token"
            )).isFalse();
            assertThat(refreshTokenStore.revoke(
                    memberId,
                    "active-refresh-token"
            )).isFalse();
        } finally {
            redisTemplate.delete(redisKey);
        }
    }

    private boolean rotateAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            Long memberId,
            String newRefreshToken
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return false;
        }

        return refreshTokenStore.rotate(
                memberId,
                "old-refresh-token",
                newRefreshToken,
                Duration.ofMinutes(5)
        );
    }
}
