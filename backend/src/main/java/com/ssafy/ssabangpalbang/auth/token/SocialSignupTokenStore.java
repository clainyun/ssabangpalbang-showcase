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
public class SocialSignupTokenStore {

    private static final String KEY_PREFIX = "auth:social-signup:";
    private static final RedisScript<Long> CONSUME_SCRIPT = RedisScript.of(
            """
                    if not redis.call('GET', KEYS[1]) then
                        return 0
                    end
                    return redis.call('DEL', KEYS[1])
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public void save(String token, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "소셜 회원가입 임시 토큰 TTL은 0보다 커야 합니다."
            );
        }

        redisTemplate.opsForValue().set(
                key(token),
                "available",
                ttl
        );
    }

    public boolean consume(String token) {
        Long result = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(key(token))
        );

        return Long.valueOf(1L).equals(result);
    }

    private String key(String token) {
        return KEY_PREFIX + sha256(token);
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
