package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.dto.request.FcmTokenRegisterRequest;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRegistrationLock;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@EnabledIfEnvironmentVariable(
        named = "RUN_LOCAL_INFRA_TESTS",
        matches = "true"
)
@Import({
        FcmTokenService.class,
        FcmTokenRegistrationLock.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FcmTokenServiceLocalIntegrationTest {

    @Autowired
    private FcmTokenService fcmTokenService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private LoginMemberResolver loginMemberResolver;

    private Long memberId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Member member = memberRepository.saveAndFlush(new Member(
                "fcm-" + suffix + "@example.com",
                "encoded-password",
                "fcm-" + suffix
        ));
        memberId = member.getId();
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(memberId, true));
    }

    @AfterEach
    void cleanUp() {
        if (memberId == null) {
            return;
        }

        jdbcTemplate.update(
                "DELETE FROM fcm_token WHERE member_id = ?",
                memberId
        );
        jdbcTemplate.update(
                "DELETE FROM member WHERE id = ?",
                memberId
        );
    }

    @Test
    void concurrentIdenticalRegistrationsLeaveOneRow() throws Exception {
        String deviceId = "device-" + suffix;
        String token = "token-" + suffix;

        runConcurrently(
                deviceId,
                token,
                deviceId,
                token
        );

        assertThat(findTokenRows())
                .containsExactly(new TokenRow(deviceId, token));
    }

    @Test
    void concurrentCrossedMappingsCompleteWithoutConflict() throws Exception {
        String firstDeviceId = "device-1-" + suffix;
        String secondDeviceId = "device-2-" + suffix;
        String firstToken = "token-1-" + suffix;
        String secondToken = "token-2-" + suffix;
        jdbcTemplate.update(
                """
                        INSERT INTO fcm_token
                            (member_id, token, device_id)
                        VALUES (?, ?, ?), (?, ?, ?)
                        """,
                memberId,
                firstToken,
                firstDeviceId,
                memberId,
                secondToken,
                secondDeviceId
        );

        runConcurrently(
                firstDeviceId,
                secondToken,
                secondDeviceId,
                firstToken
        );

        assertThat(findTokenRows())
                .containsExactlyInAnyOrder(
                        new TokenRow(firstDeviceId, secondToken),
                        new TokenRow(secondDeviceId, firstToken)
                );
    }

    @Test
    void concurrentRegistrationAndDeletionAreLinearizedForSameDevice() throws Exception {
        String deviceId = "device-delete-" + suffix;
        String oldToken = "old-token-" + suffix;
        String newToken = "new-token-" + suffix;
        jdbcTemplate.update(
                """
                        INSERT INTO fcm_token
                            (member_id, token, device_id)
                        VALUES (?, ?, ?)
                        """,
                memberId,
                oldToken,
                deviceId
        );

        runRegisterAndDeleteConcurrently(deviceId, newToken);

        List<TokenRow> rows = findTokenRows();
        assertThat(rows).hasSizeLessThanOrEqualTo(1);
        assertThat(rows).allMatch(row ->
                row.deviceId().equals(deviceId)
                        && row.token().equals(newToken)
        );
    }

    @Test
    void advisoryLockContentionReturnsTimeoutErrorWithinBound() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Future<?> lockHolder = executor.submit(() -> {
            TransactionTemplate transactionTemplate =
                    new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.query(
                        """
                                SELECT pg_advisory_xact_lock(
                                    hashtextextended(CAST(? AS text), 0)
                                )
                                """,
                        preparedStatement -> preparedStatement.setString(
                                1,
                                "fcm-registration:global"
                        ),
                        resultSet -> {
                        }
                );
                lockAcquired.countDown();
                awaitConcurrentStart(releaseLock);
            });
        });

        try {
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            long startedAt = System.nanoTime();

            assertThatThrownBy(() -> fcmTokenService.register(
                    "timeout-device-" + suffix,
                    new FcmTokenRegisterRequest("timeout-token-" + suffix)
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.MEMBER_FCM_TOKEN_LOCK_TIMEOUT);

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(5));
        } finally {
            releaseLock.countDown();
            lockHolder.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    private void runConcurrently(
            String firstDeviceId,
            String firstToken,
            String secondDeviceId,
            String secondToken
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> registerAfterStart(
                    ready,
                    start,
                    firstDeviceId,
                    firstToken
            ));
            Future<?> second = executor.submit(() -> registerAfterStart(
                    ready,
                    start,
                    secondDeviceId,
                    secondToken
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    private void registerAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            String deviceId,
            String token
    ) {
        ready.countDown();

        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Concurrent registration start timed out"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Concurrent registration was interrupted",
                    exception
            );
        }

        fcmTokenService.register(
                deviceId,
                new FcmTokenRegisterRequest(token)
        );
    }

    private void runRegisterAndDeleteConcurrently(
            String deviceId,
            String token
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> registration = executor.submit(() -> registerAfterStart(
                    ready,
                    start,
                    deviceId,
                    token
            ));
            Future<?> deletion = executor.submit(() -> {
                ready.countDown();
                awaitConcurrentStart(start);
                fcmTokenService.delete(deviceId);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            registration.get(10, TimeUnit.SECONDS);
            deletion.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    private void awaitConcurrentStart(CountDownLatch start) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Concurrent operation start timed out"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Concurrent operation was interrupted",
                    exception
            );
        }
    }

    private List<TokenRow> findTokenRows() {
        return jdbcTemplate.query(
                """
                        SELECT device_id, token
                          FROM fcm_token
                         WHERE member_id = ?
                        """,
                (resultSet, rowNumber) -> new TokenRow(
                        resultSet.getString("device_id"),
                        resultSet.getString("token")
                ),
                memberId
        );
    }

    private record TokenRow(
            String deviceId,
            String token
    ) {
    }
}
