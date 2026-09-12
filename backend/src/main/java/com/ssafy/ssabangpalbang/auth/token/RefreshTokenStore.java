package com.ssafy.ssabangpalbang.auth.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final RedisScript<Long> ROTATE_SCRIPT = RedisScript.of(
            """
                    local current = redis.call('GET', KEYS[1])
                    if not current or current ~= ARGV[1] then
                        return 0
                    end
                    redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                    return 1
                    """,
            Long.class
    );
    private static final RedisScript<Long> REVOKE_SCRIPT = RedisScript.of(
            """
                    local current = redis.call('GET', KEYS[1])
                    if not current or current ~= ARGV[1] then
                        return 0
                    end
                    return redis.call('DEL', KEYS[1])
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public void save(
            Long memberId,
            String refreshToken,
            Duration ttl
    ) {
        redisTemplate.opsForValue().set(
                key(memberId),
                sha256(refreshToken),
                ttl
        );
    }

    public boolean matches(Long memberId, String refreshToken) {
        String storedHash = redisTemplate.opsForValue().get(key(memberId));
        if (storedHash == null) {
            return false;
        }

        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                sha256(refreshToken).getBytes(StandardCharsets.UTF_8)
        );
    }

    public boolean rotate(
            Long memberId,
            String currentRefreshToken,
            String newRefreshToken,
            Duration ttl
    ) {
        long ttlMillis = ttl.toMillis();
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException(
                    "Refresh Token TTL은 0보다 커야 합니다."
            );
        }

        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(memberId)),
                sha256(currentRefreshToken),
                sha256(newRefreshToken),
                Long.toString(ttlMillis)
        );

        return Long.valueOf(1L).equals(result);
    }

    public boolean revoke(Long memberId, String refreshToken) {
        Long result = redisTemplate.execute(
                REVOKE_SCRIPT,
                List.of(key(memberId)),
                sha256(refreshToken)
        );

        return Long.valueOf(1L).equals(result);
    }

    public void revokeAll(Long memberId) {
        redisTemplate.delete(key(memberId));
    }

    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 알고리즘을 사용할 수 없습니다.",
                    exception
            );
        }
    }
}
