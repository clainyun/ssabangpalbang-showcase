package com.ssafy.ssabangpalbang.member.repository;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

@Repository
public class FcmTokenRegistrationLock {

    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofMillis(25);
    private static final Duration MAX_RETRY_INTERVAL = Duration.ofMillis(200);
    private static final String GLOBAL_REGISTRATION_LOCK =
            "fcm-registration:global";
    private static final String TRY_LOCK_QUERY = """
            SELECT pg_try_advisory_xact_lock(
                hashtextextended(CAST(:lockKey AS text), 0)
            )
            """;

    private final EntityManager entityManager;
    private final Duration lockTimeout;
    private final Duration retryInterval;

    @Autowired
    public FcmTokenRegistrationLock(EntityManager entityManager) {
        this(
                entityManager,
                DEFAULT_LOCK_TIMEOUT,
                DEFAULT_RETRY_INTERVAL
        );
    }

    FcmTokenRegistrationLock(
            EntityManager entityManager,
            Duration lockTimeout,
            Duration retryInterval
    ) {
        this.entityManager = entityManager;
        this.lockTimeout = lockTimeout;
        this.retryInterval = retryInterval;
    }

    public void acquire(String deviceId, String token) {
        acquireLock(GLOBAL_REGISTRATION_LOCK);

        Stream.of(
                        "fcm-device:" + deviceId,
                        "fcm-token:" + token
                )
                .sorted()
                .forEach(this::acquireLock);
    }

    public void acquireDevice(String deviceId) {
        acquireLock(GLOBAL_REGISTRATION_LOCK);
        acquireLock("fcm-device:" + deviceId);
    }

    private void acquireLock(String lockKey) {
        long deadlineNanos = System.nanoTime() + lockTimeout.toNanos();
        long retryNanos = retryInterval.toNanos();

        while (true) {
            long remainingNanos = Math.max(
                    0L,
                    deadlineNanos - System.nanoTime()
            );
            int queryTimeoutMillis = (int) Math.max(
                    1L,
                    Math.min(
                            Integer.MAX_VALUE,
                            (remainingNanos + 999_999L) / 1_000_000L
                    )
            );
            Query query = entityManager.createNativeQuery(TRY_LOCK_QUERY)
                    .setParameter("lockKey", lockKey)
                    .setHint(
                            "jakarta.persistence.query.timeout",
                            queryTimeoutMillis
                    );
            Object acquired = query.getSingleResult();
            if (Boolean.TRUE.equals(acquired)) {
                return;
            }
            if (System.nanoTime() >= deadlineNanos) {
                throw lockTimeoutException();
            }

            long parkNanos = Math.min(
                    retryNanos,
                    Math.max(1L, deadlineNanos - System.nanoTime())
            );
            long jitteredParkNanos = ThreadLocalRandom.current().nextLong(
                    Math.max(1L, parkNanos / 2L),
                    parkNanos + 1L
            );
            LockSupport.parkNanos(jitteredParkNanos);
            if (Thread.currentThread().isInterrupted()) {
                throw lockTimeoutException();
            }
            retryNanos = Math.min(
                    MAX_RETRY_INTERVAL.toNanos(),
                    Math.max(1L, retryNanos * 2L)
            );
        }
    }

    private BusinessException lockTimeoutException() {
        return new BusinessException(
                ErrorCode.MEMBER_FCM_TOKEN_LOCK_TIMEOUT
        );
    }
}
