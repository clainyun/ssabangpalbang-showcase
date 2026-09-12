package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.report.dto.request.ReportAcquireRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportFailRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportWorkerErrorCode;
import com.ssafy.ssabangpalbang.report.dto.request.ReportWorkerProgressStage;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false",
        "ssabangpalbang.report.internal.token=postgres-test-token",
        "ssabangpalbang.report.internal.lease-duration=30m"
})
@ActiveProfiles("test")
@Testcontainers
class ReportRetryPostgresTest {

    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.of(
            2026,
            8,
            2,
            14,
            30,
            0,
            0,
            ZoneOffset.ofHours(9)
    );

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-postgres-test",
                false
        ).withDockerfile(Path.of("..", "infra", "postgres", "Dockerfile"));
        String imageId = image.get();
        return new PostgreSQLContainer<>(
                DockerImageName.parse(imageId)
                        .asCompatibleSubstituteFor("postgres")
        )
                .withDatabaseName("ssabangpalbang_test")
                .withUsername("test")
                .withPassword("test");
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver"
        );
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration"
        );
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 6379);
        registry.add(
                "spring.kafka.bootstrap-servers",
                () -> "127.0.0.1:9092"
        );
    }

    @Autowired
    private ReportAcquireService reportAcquireService;

    @Autowired
    private ReportFailService reportFailService;

    @Autowired
    private ReportRetryService reportRetryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ReportRequestPort reportRequestPort;

    @MockitoBean
    private SttDispatchPort sttDispatchPort;

    @MockitoBean
    private SttAudioDeletionPort sttAudioDeletionPort;

    @MockitoBean
    private GatewayMediaAccessUrlProvider mediaAccessUrlProvider;

    private Long apartmentId;
    private Long memberId;
    private Long studyId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        cleanup();
        clearInvocations(reportRequestPort);
        seed();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void V22를_적용하고_실패한_Report를_재시도_대기_상태로_저장한다() {
        ReportAcquireResponse acquired = acquireAndFail();

        ReportRetryService.RetryResult result = reportRetryService.retry(
                memberId,
                acquired.reportId()
        );
        StoredRetry stored = storedRetry(acquired.reportId());

        Integer v22Applied = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '22'
                  AND success = TRUE
                """, Integer.class);
        Integer v22ColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'report'
                  AND column_name = 'retry_requested_at'
                """, Integer.class);

        assertThat(v22Applied).isEqualTo(1);
        assertThat(v22ColumnCount).isEqualTo(1);
        assertThat(result.httpStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(stored.status()).isEqualTo("PENDING");
        assertThat(stored.progressStage()).isEqualTo("RECORD_COLLECTION");
        assertThat(stored.retryRequestedAt()).isNotNull();
        assertThat(stored.processingAttempt())
                .isEqualTo(acquired.processingAttempt());
        assertThat(stored.processingTokenHash()).isNull();
        assertThat(stored.processingLeaseExpiresAt()).isNull();
        assertThat(stored.failCode()).isNull();
        assertThat(stored.failReason()).isNull();
        assertThat(stored.retryable()).isFalse();
        assertThat(stored.failPayloadHash()).isNull();
        assertThat(stored.failedAt()).isNull();
        verify(reportRequestPort, times(1)).request(any());
    }

    @Test
    void 두_재시도_요청이_동시에_와도_상태_전이와_이벤트는_한_번이다()
            throws Exception {
        ReportAcquireResponse acquired = acquireAndFail();
        clearInvocations(reportRequestPort);

        List<ReportRetryService.RetryResult> results = invokeConcurrently(
                acquired.reportId()
        );

        assertThat(results)
                .extracting(ReportRetryService.RetryResult::httpStatus)
                .containsExactlyInAnyOrder(HttpStatus.ACCEPTED, HttpStatus.OK);
        StoredRetry stored = storedRetry(acquired.reportId());
        assertThat(stored.status()).isEqualTo("PENDING");
        assertThat(stored.retryRequestedAt()).isNotNull();
        assertThat(stored.processingAttempt())
                .isEqualTo(acquired.processingAttempt());
        verify(reportRequestPort, times(1)).request(any());
    }

    @Test
    void 재시도_후_Worker가_다시_acquire하면_processingAttempt가_증가한다() {
        ReportAcquireRequest acquireRequest = request();
        ReportAcquireResponse first = reportAcquireService.acquire(
                acquireRequest
        );
        fail(first);
        reportRetryService.retry(memberId, first.reportId());

        ReportAcquireResponse reacquired = reportAcquireService.acquire(
                acquireRequest
        );

        assertThat(reacquired.status()).isEqualTo(ReportAcquireStatus.ACQUIRED);
        assertThat(reacquired.reportId()).isEqualTo(first.reportId());
        assertThat(reacquired.processingAttempt())
                .isEqualTo(first.processingAttempt() + 1);
        StoredRetry stored = storedRetry(first.reportId());
        assertThat(stored.status()).isEqualTo("IN_PROGRESS");
        assertThat(stored.processingAttempt())
                .isEqualTo(reacquired.processingAttempt());
        assertThat(stored.processingTokenHash()).hasSize(64);
        assertThat(stored.processingLeaseExpiresAt()).isNotNull();
        assertThat(stored.retryRequestedAt()).isNotNull();
    }

    private List<ReportRetryService.RetryResult> invokeConcurrently(
            Long reportId
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ReportRetryService.RetryResult> first = pool.submit(
                    () -> invokeRetry(reportId, ready, start)
            );
            Future<ReportRetryService.RetryResult> second = pool.submit(
                    () -> invokeRetry(reportId, ready, start)
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            );
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("executor did not terminate");
            }
        }
    }

    private ReportRetryService.RetryResult invokeRetry(
            Long reportId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return reportRetryService.retry(memberId, reportId);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private ReportAcquireResponse acquireAndFail() {
        ReportAcquireResponse acquired = reportAcquireService.acquire(
                request()
        );
        fail(acquired);
        return acquired;
    }

    private void fail(ReportAcquireResponse acquired) {
        reportFailService.fail(
                acquired.reportId(),
                new ReportFailRequest(
                        acquired.processingToken(),
                        acquired.processingAttempt(),
                        ReportWorkerProgressStage.NORMALIZATION,
                        ReportWorkerErrorCode.NORMALIZATION_FAILED,
                        "리포트 입력 정규화에 실패했습니다.",
                        true
                )
        );
    }

    private ReportAcquireRequest request() {
        return new ReportAcquireRequest(
                studyId,
                sessionId,
                apartmentId,
                OCCURRED_AT
        );
    }

    private StoredRetry storedRetry(Long reportId) {
        return jdbcTemplate.queryForObject("""
                SELECT status,
                       progress_stage,
                       retry_requested_at,
                       processing_attempt,
                       processing_token_hash,
                       processing_lease_expires_at,
                       fail_code,
                       fail_reason,
                       is_retryable,
                       fail_payload_hash,
                       failed_at
                FROM report
                WHERE id = ?
                """, (resultSet, rowNum) -> new StoredRetry(
                resultSet.getString("status"),
                resultSet.getString("progress_stage"),
                resultSet.getObject("retry_requested_at"),
                resultSet.getInt("processing_attempt"),
                resultSet.getString("processing_token_hash"),
                resultSet.getObject("processing_lease_expires_at"),
                resultSet.getString("fail_code"),
                resultSet.getString("fail_reason"),
                resultSet.getBoolean("is_retryable"),
                resultSet.getString("fail_payload_hash"),
                resultSet.getObject("failed_at")
        ), reportId);
    }

    private void seed() {
        apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE019-RETRY', 127.0, 37.5)
                RETURNING id
                """, Long.class, "BE019-" + UUID.randomUUID());
        memberId = insertMember();
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (
                    apartment_id, leader_id, goal, capacity, title, status
                ) VALUES (
                    ?, ?, 'be019-retry-goal', 5,
                    'be019-retry', 'COMPLETED'
                )
                RETURNING id
                """, Long.class, apartmentId, memberId);
        sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO field_session (
                    study_id, status, started_at, ended_at,
                    ended_by_id, end_reason
                ) VALUES (
                    ?, 'ENDED', ? - INTERVAL '1 hour', ?,
                    ?, 'ALL_COMPLETED'
                )
                RETURNING id
                """, Long.class,
                studyId,
                OCCURRED_AT,
                OCCURRED_AT,
                memberId);
        jdbcTemplate.update("""
                INSERT INTO field_participant (
                    session_id, member_id, status, started_at,
                    ended_at, end_reason
                ) VALUES (
                    ?, ?, 'ENDED', ? - INTERVAL '1 hour',
                    ?, 'SELF_FINISH'
                )
                """, sessionId, memberId, OCCURRED_AT, OCCURRED_AT);
        Long checklistId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist (session_id, member_id, is_fallback)
                VALUES (?, ?, false)
                RETURNING id
                """, Long.class, sessionId, memberId);
        Long checklistItemId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist_item (
                    checklist_id, category, title, display_order
                ) VALUES (
                    ?, 'TRANSPORT', '대중교통 접근성을 확인했나요?', 1
                )
                RETURNING id
                """, Long.class, checklistId);
        jdbcTemplate.update("""
                INSERT INTO checklist_answer (
                    checklist_item_id, is_completed, completed_at
                ) VALUES (?, true, ?)
                """, checklistItemId, OCCURRED_AT);
        jdbcTemplate.update("""
                INSERT INTO field_record (
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, client_request_id, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, 'TEXT', '역이 가깝습니다.', ?, ?, ?
                )
                """,
                sessionId,
                checklistItemId,
                memberId,
                UUID.randomUUID().toString(),
                OCCURRED_AT,
                OCCURRED_AT);
    }

    private Long insertMember() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (
                    email, nickname, age_group_public_agreed,
                    service_notification_agreed, ad_notification_agreed
                ) VALUES (?, ?, false, true, false)
                RETURNING id
                """, Long.class,
                "be019-retry-" + suffix + "@test.local",
                "retry" + suffix);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM report_evidence");
        jdbcTemplate.update("DELETE FROM report");
        jdbcTemplate.update("DELETE FROM field_record");
        jdbcTemplate.update("DELETE FROM checklist_answer");
        jdbcTemplate.update("DELETE FROM checklist_item");
        jdbcTemplate.update("DELETE FROM checklist");
        jdbcTemplate.update("DELETE FROM field_participant");
        jdbcTemplate.update("DELETE FROM field_session");
        jdbcTemplate.update("DELETE FROM study_member");
        jdbcTemplate.update(
                "DELETE FROM study WHERE title = 'be019-retry'"
        );
        jdbcTemplate.update(
                "DELETE FROM apartment WHERE name = 'BE019-RETRY'"
        );
        jdbcTemplate.update("""
                DELETE FROM member
                WHERE email LIKE 'be019-retry-%@test.local'
                """);
    }

    private record StoredRetry(
            String status,
            String progressStage,
            Object retryRequestedAt,
            int processingAttempt,
            String processingTokenHash,
            Object processingLeaseExpiresAt,
            String failCode,
            String failReason,
            boolean retryable,
            String failPayloadHash,
            Object failedAt
    ) {
    }
}
