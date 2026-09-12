package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.report.dto.request.ReportAcquireRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportProgressRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportWorkerProgressStage;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireResponse;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false",
        "ssabangpalbang.report.internal.token=postgres-test-token",
        "ssabangpalbang.report.internal.lease-duration=30m"
})
@ActiveProfiles("test")
@Testcontainers
class ReportAcquireConcurrencyPostgresTest {

    private static final int CONCURRENT_REQUESTS = 5;

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
    private ReportProgressService reportProgressService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SttDispatchPort sttDispatchPort;

    @MockitoBean
    private SttAudioDeletionPort sttAudioDeletionPort;

    @MockitoBean
    private GatewayMediaAccessUrlProvider mediaAccessUrlProvider;

    private Long studyId;
    private Long sessionId;
    private Long apartmentId;

    @BeforeEach
    void setUp() {
        cleanup();
        seed();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void 신규_Report_병렬_acquire는_처리권을_한_번만_발급한다()
            throws Exception {
        List<ReportAcquireResponse> responses = invokeConcurrently();

        assertSingleAcquired(responses, 1);
        assertStoredReport(1);
    }

    @Test
    void 만료된_Lease_병렬_재선점은_attempt를_한_번만_증가시킨다()
            throws Exception {
        ReportAcquireResponse first = reportAcquireService.acquire(request());
        assertThat(first.status()).isEqualTo(ReportAcquireStatus.ACQUIRED);

        jdbcTemplate.update("""
                UPDATE report
                SET processing_lease_expires_at = now() - INTERVAL '1 minute'
                WHERE id = ?
                """, first.reportId());

        List<ReportAcquireResponse> responses = invokeConcurrently();

        assertSingleAcquired(responses, 2);
        assertStoredReport(2);
    }

    @Test
    void 만료된_이전_Token의_progress와_재선점은_직렬화된다()
            throws Exception {
        ReportAcquireResponse first = reportAcquireService.acquire(request());
        assertThat(first.status()).isEqualTo(ReportAcquireStatus.ACQUIRED);
        jdbcTemplate.update("""
                UPDATE report
                SET processing_lease_expires_at = now() - INTERVAL '1 minute'
                WHERE id = ?
                """, first.reportId());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ReportAcquireResponse> reacquire = pool.submit(() -> {
                await(start);
                return reportAcquireService.acquire(request());
            });
            Future<ErrorCode> staleProgress = pool.submit(() -> {
                await(start);
                try {
                    reportProgressService.updateProgress(
                            first.reportId(),
                            new ReportProgressRequest(
                                    first.processingToken(),
                                    first.processingAttempt(),
                                    ReportWorkerProgressStage.STT_VALIDATION
                            )
                    );
                    return null;
                } catch (BusinessException exception) {
                    return exception.getErrorCode();
                }
            });
            start.countDown();

            ReportAcquireResponse reclaimed = reacquire.get(
                    30,
                    TimeUnit.SECONDS
            );
            assertThat(reclaimed.status())
                    .isEqualTo(ReportAcquireStatus.ACQUIRED);
            assertThat(reclaimed.processingAttempt()).isEqualTo(2);
            assertThat(staleProgress.get(30, TimeUnit.SECONDS))
                    .isEqualTo(ErrorCode.STALE_PROCESSING_TOKEN);

            reportProgressService.updateProgress(
                    reclaimed.reportId(),
                    new ReportProgressRequest(
                        reclaimed.processingToken(),
                        reclaimed.processingAttempt(),
                        ReportWorkerProgressStage.STT_VALIDATION
                    )
            );

            String stage = jdbcTemplate.queryForObject(
                    "SELECT progress_stage FROM report WHERE id = ?",
                    String.class,
                    reclaimed.reportId()
            );
            assertThat(stage).isEqualTo("STT_VALIDATION");
            assertStoredReport(2);
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("executor did not terminate");
            }
        }
    }

    private List<ReportAcquireResponse> invokeConcurrently() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        List<Future<ReportAcquireResponse>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < CONCURRENT_REQUESTS; index++) {
                futures.add(pool.submit(() -> {
                    await(start);
                    return reportAcquireService.acquire(request());
                }));
            }
            start.countDown();

            List<ReportAcquireResponse> responses = new ArrayList<>();
            for (Future<ReportAcquireResponse> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("executor did not terminate");
            }
        }
    }

    private void assertSingleAcquired(
            List<ReportAcquireResponse> responses,
            int expectedAttempt
    ) {
        assertThat(responses).hasSize(CONCURRENT_REQUESTS);
        assertThat(responses.stream()
                .filter(response -> response.status()
                        == ReportAcquireStatus.ACQUIRED))
                .hasSize(1);
        assertThat(responses.stream()
                .filter(response -> response.status()
                        == ReportAcquireStatus.ALREADY_PROCESSING))
                .hasSize(CONCURRENT_REQUESTS - 1);

        ReportAcquireResponse acquired = responses.stream()
                .filter(response -> response.status()
                        == ReportAcquireStatus.ACQUIRED)
                .findFirst()
                .orElseThrow();
        assertThat(acquired.processingToken()).isNotBlank();
        assertThat(acquired.processingAttempt()).isEqualTo(expectedAttempt);
        assertThat(responses).filteredOn(response -> response.status()
                        != ReportAcquireStatus.ACQUIRED)
                .allSatisfy(response ->
                        assertThat(response.processingToken()).isNull()
                );
        assertThat(responses).extracting(ReportAcquireResponse::reportId)
                .containsOnly(acquired.reportId());
    }

    private void assertStoredReport(int expectedAttempt) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report WHERE study_id = ?",
                Integer.class,
                studyId
        );
        Integer attempt = jdbcTemplate.queryForObject(
                "SELECT processing_attempt FROM report WHERE study_id = ?",
                Integer.class,
                studyId
        );
        Integer hashLength = jdbcTemplate.queryForObject(
                "SELECT length(processing_token_hash) FROM report WHERE study_id = ?",
                Integer.class,
                studyId
        );

        assertThat(count).isEqualTo(1);
        assertThat(attempt).isEqualTo(expectedAttempt);
        assertThat(hashLength).isEqualTo(64);
    }

    private ReportAcquireRequest request() {
        return new ReportAcquireRequest(
                studyId,
                sessionId,
                apartmentId,
                OffsetDateTime.of(
                        2026,
                        8,
                        2,
                        12,
                        30,
                        0,
                        0,
                        ZoneOffset.ofHours(9)
                )
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void seed() {
        apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE019-ACQUIRE', 127.0, 37.5)
                RETURNING id
                """, Long.class, "BE019-" + UUID.randomUUID());
        Long memberId = insertMember();
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (
                    apartment_id, leader_id, goal, capacity, title, status
                ) VALUES (?, ?, 'be019-acquire-goal', 5, 'be019-acquire', 'COMPLETED')
                RETURNING id
                """, Long.class, apartmentId, memberId);
        sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO field_session (
                    study_id, status, ended_at, ended_by_id, end_reason
                ) VALUES (?, 'ENDED', now(), ?, 'ALL_COMPLETED')
                RETURNING id
                """, Long.class, studyId, memberId);
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
                "be019-acquire-" + suffix + "@test.local",
                "be019" + suffix);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM report");
        jdbcTemplate.update("DELETE FROM field_session");
        jdbcTemplate.update("DELETE FROM study_member");
        jdbcTemplate.update("DELETE FROM study WHERE title = 'be019-acquire'");
        jdbcTemplate.update("DELETE FROM apartment WHERE name = 'BE019-ACQUIRE'");
        jdbcTemplate.update(
                "DELETE FROM member WHERE email LIKE 'be019-acquire-%@test.local'"
        );
    }
}
