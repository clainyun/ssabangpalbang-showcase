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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RefreshTokenStore refreshTokenStore;

    @BeforeEach
    void setUp() {
        refreshTokenStore = new RefreshTokenStore(redisTemplate);
    }

    @Test
    void Refresh_Token_원문_대신_SHA256_해시를_TTL과_저장한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        refreshTokenStore.save(
                1L,
                "refresh-token",
                Duration.ofDays(30)
        );

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(
                String.class
        );
        verify(valueOperations).set(
                eq("auth:refresh:1"),
                hashCaptor.capture(),
                eq(Duration.ofDays(30))
        );

        assertThat(hashCaptor.getValue())
                .isNotEqualTo("refresh-token")
                .matches("[0-9a-f]{64}");
    }

    @Test
    void 저장된_해시와_Refresh_Token이_일치하는지_확인한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:refresh:1"))
                .thenReturn("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120");

        assertThat(refreshTokenStore.matches(1L, "refresh-token"))
                .isTrue();
        assertThat(refreshTokenStore.matches(1L, "different-token"))
                .isFalse();
    }

    @Test
    void Redis의_기존_해시가_일치할_때만_원자적으로_회전한다() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("auth:refresh:1")),
                anyString(),
                anyString(),
                eq("2592000000")
        )).thenReturn(1L);

        boolean rotated = refreshTokenStore.rotate(
                1L,
                "old-refresh-token",
                "new-refresh-token",
                Duration.ofDays(30)
        );

        assertThat(rotated).isTrue();
        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("auth:refresh:1")),
                anyString(),
                anyString(),
                eq("2592000000")
        );
    }

    @Test
    void 일치하는_Refresh_Token_해시만_원자적으로_폐기한다() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("auth:refresh:1")),
                anyString()
        )).thenReturn(1L);

        boolean revoked = refreshTokenStore.revoke(
                1L,
                "refresh-token"
        );

        assertThat(revoked).isTrue();
        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("auth:refresh:1")),
                anyString()
        );
    }

    @Test
    void 회원의_Refresh_Token_키_전체를_폐기한다() {
        refreshTokenStore.revokeAll(1L);

        verify(redisTemplate).delete("auth:refresh:1");
    }
}
