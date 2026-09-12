package com.ssafy.ssabangpalbang.auth.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialSignupTokenStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SocialSignupTokenStore socialSignupTokenStore;

    @BeforeEach
    void setUp() {
        socialSignupTokenStore = new SocialSignupTokenStore(redisTemplate);
    }

    @Test
    void 임시_토큰_원문_대신_SHA256_키를_TTL과_저장한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        socialSignupTokenStore.save(
                "social-signup-token",
                Duration.ofMinutes(10)
        );

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(
                String.class
        );
        verify(valueOperations).set(
                keyCaptor.capture(),
                eq("available"),
                eq(Duration.ofMinutes(10))
        );
        assertThat(keyCaptor.getValue())
                .startsWith("auth:social-signup:")
                .doesNotContain("social-signup-token")
                .matches("auth:social-signup:[0-9a-f]{64}");
    }

    @Test
    void 등록된_임시_토큰을_원자적으로_한_번_소비한다() {
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(
                List.class
        );
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.<String>anyList()
        )).thenReturn(1L, 0L);

        assertThat(socialSignupTokenStore.consume("social-signup-token"))
                .isTrue();
        assertThat(socialSignupTokenStore.consume("social-signup-token"))
                .isFalse();

        verify(redisTemplate, org.mockito.Mockito.times(2)).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                keysCaptor.capture()
        );
        assertThat(keysCaptor.getAllValues().get(0))
                .isEqualTo(keysCaptor.getAllValues().get(1));
    }
}
