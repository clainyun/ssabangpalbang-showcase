package com.ssafy.ssabangpalbang.auth.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 비밀번호 재설정 코드와 재발송 쿨다운을 Redis에 저장한다.
 * 코드 원문은 노출하지 않기 위해 SHA-256 해시로만 저장하고 상수 시간 비교로 검증한다.
 */
@Component
@RequiredArgsConstructor
public class PasswordResetCodeStore {

    private static final String CODE_KEY_PREFIX = "auth:password-reset:code:";
    private static final String COOLDOWN_KEY_PREFIX =
            "auth:password-reset:cooldown:";
    private static final String ATTEMPT_KEY_PREFIX =
            "auth:password-reset:attempt:";

    private final StringRedisTemplate redisTemplate;

    public void saveCode(String email, String code, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "비밀번호 재설정 코드 TTL은 0보다 커야 합니다."
            );
        }

        redisTemplate.opsForValue().set(
                codeKey(email),
                sha256(code),
                ttl
        );
    }

    public boolean matches(String email, String code) {
        String storedHash = redisTemplate.opsForValue().get(codeKey(email));
        if (storedHash == null) {
            return false;
        }

        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                sha256(code).getBytes(StandardCharsets.UTF_8)
        );
    }

    public void deleteCode(String email) {
        redisTemplate.delete(codeKey(email));
    }

    public boolean isWithinCooldown(String email) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(cooldownKey(email))
        );
    }

    public void markCooldown(String email, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "비밀번호 재설정 쿨다운 TTL은 0보다 커야 합니다."
            );
        }

        redisTemplate.opsForValue().set(
                cooldownKey(email),
                "1",
                ttl
        );
    }

    /**
     * confirm 실패 횟수를 이메일 기준으로 누적하고 누적값을 반환한다.
     * 무차별 대입을 제한하기 위해 사용하며, 카운터 TTL은 코드 TTL과 함께 정리되도록
     * 최초 실패 시에만 만료 시간을 설정한다.
     */
    public long recordFailedAttempt(String email, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "비밀번호 재설정 시도 카운터 TTL은 0보다 커야 합니다."
            );
        }

        Long attempts = redisTemplate.opsForValue().increment(
                attemptKey(email)
        );
        long value = attempts == null ? 0L : attempts;
        if (value == 1L) {
            redisTemplate.expire(attemptKey(email), ttl);
        }
        return value;
    }

    public void clearAttempts(String email) {
        redisTemplate.delete(attemptKey(email));
    }

    private String codeKey(String email) {
        return CODE_KEY_PREFIX + email;
    }

    private String cooldownKey(String email) {
        return COOLDOWN_KEY_PREFIX + email;
    }

    private String attemptKey(String email) {
        return ATTEMPT_KEY_PREFIX + email;
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
