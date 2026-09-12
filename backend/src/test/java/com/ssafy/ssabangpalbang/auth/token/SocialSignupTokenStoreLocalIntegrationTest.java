package com.ssafy.ssabangpalbang.auth.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(
        named = "RUN_LOCAL_INFRA_TESTS",
        matches = "true"
)
class SocialSignupTokenStoreLocalIntegrationTest {

    @Autowired
    private SocialSignupTokenStore socialSignupTokenStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void 임시_토큰은_해시_키와_TTL로_저장되고_동시에_한_번만_소비된다()
            throws Exception {
        String token = "social-signup-token-" + UUID.randomUUID();
        String redisKey = "auth:social-signup:" + sha256(token);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            socialSignupTokenStore.save(token, Duration.ofMinutes(5));

            assertThat(redisKey).doesNotContain(token);
            assertThat(redisTemplate.opsForValue().get(redisKey))
                    .isEqualTo("available");
            assertThat(redisTemplate.getExpire(redisKey, TimeUnit.SECONDS))
                    .isBetween(1L, 300L);

            Future<Boolean> first = executor.submit(() -> consumeAfterStart(
                    ready,
                    start,
                    token
            ));
            Future<Boolean> second = executor.submit(() -> consumeAfterStart(
                    ready,
                    start,
                    token
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(true, false);
            assertThat(redisTemplate.hasKey(redisKey)).isFalse();
        } finally {
            start.countDown();
            executor.shutdownNow();
            redisTemplate.delete(redisKey);
        }
    }

    private boolean consumeAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            String token
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return false;
        }
        return socialSignupTokenStore.consume(token);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
