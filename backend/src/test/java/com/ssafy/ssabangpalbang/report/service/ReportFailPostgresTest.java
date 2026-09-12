package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.report.dto.request.ReportAcquireRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportCompleteRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportEvidenceResultRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportFailRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false",
        "ssabangpalbang.report.internal.token=postgres-test-token",
        "ssabangpalbang.report.internal.lease-duration=30m"
})
@ActiveProfiles("test")
@Testcontainers
class ReportFailPostgresTest {

    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.of(
            2026,
            8,
            2,
            13,
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
    private ReportCompleteService reportCompleteService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    private Long checklistItemId;
    private Long fieldRecordId;

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
    void V21_실패_메타데이터를_저장하고_동일_명령을_멱등_처리한다() {
        ReportAcquireResponse acquired = reportAcquireService.acquire(
                new ReportAcquireRequest(
                        studyId,
                        sessionId,
                        apartmentId,
                        OCCURRED_AT
                )
        );
        ReportFailRequest request = request(
                acquired.processingToken(),
                acquired.processingAttempt(),
                false
        );

        reportFailService.fail(acquired.reportId(), request);
        StoredFailure first = storedFailure(acquired.reportId());
        reportFailService.fail(acquired.reportId(), request);
        StoredFailure replayed = storedFailure(acquired.reportId());

        Integer v21Applied = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '21'
                  AND success = TRUE
                """, Integer.class);
        Integer v21ColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'report'
                  AND column_name IN (
                      'fail_code', 'failed_at', 'fail_payload_hash'
                  )
                """, Integer.class);

        assertThat(v21Applied).isEqualTo(1);
        assertThat(v21ColumnCount).isEqualTo(3);
        assertThat(first).isEqualTo(replayed);
        assertThat(first.status()).isEqualTo("FAILED");
        assertThat(first.progressStage()).isEqualTo("NORMALIZATION");
        assertThat(first.failCode()).isEqualTo("NORMALIZATION_FAILED");
        assertThat(first.failReason())
                .isEqualTo("리포트 입력 정규화에 실패했습니다.");
        assertThat(first.retryable()).isFalse();
        assertThat(first.failPayloadHash()).hasSize(64);
        assertThat(first.processingAttempt())
                .isEqualTo(acquired.processingAttempt());
        assertThat(first.processingTokenHash())
                .hasSize(64)
                .isNotEqualTo(acquired.processingToken());
        assertThat(first.processingLeaseExpiresAt()).isNull();
        assertThat(first.failedAt()).isNotNull();
        assertThat(first.completedAt()).isNull();
        assertThat(first.completePayloadHash()).isNull();
        assertThat(first.resultJson()).isNull();
        assertThat(studyStatus()).isEqualTo("IN_PROGRESS");

        ReportFailRequest changed = request(
                acquired.processingToken(),
                acquired.processingAttempt(),
                true
        );
        assertThatThrownBy(() -> reportFailService.fail(
                acquired.reportId(),
                changed
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.FAIL_PAYLOAD_CONFLICT)
        );
        assertThat(storedFailure(acquired.reportId())).isEqualTo(replayed);
    }

    @Test
    void 실패한_Report는_PostgreSQL에서도_자동_재선점하지_않는다() {
        ReportAcquireRequest acquireRequest = new ReportAcquireRequest(
                studyId,
                sessionId,
                apartmentId,
                OCCURRED_AT
        );
        ReportAcquireResponse acquired = reportAcquireService.acquire(
                acquireRequest
        );
        reportFailService.fail(
                acquired.reportId(),
                request(
                        acquired.processingToken(),
                        acquired.processingAttempt(),
                        false
                )
        );

        ReportAcquireResponse reacquired = reportAcquireService.acquire(
                acquireRequest
        );

        assertThat(reacquired.status())
                .isEqualTo(ReportAcquireStatus.ALREADY_FAILED);
        assertThat(reacquired.reportId()).isEqualTo(acquired.reportId());
        assertThat(reacquired.processingToken()).isNull();
        assertThat(reacquired.processingAttempt()).isNull();
        assertThat(storedFailure(acquired.reportId()).status())
                .isEqualTo("FAILED");
    }

    @Test
    void 완료와_실패가_동시에_요청되어도_최종_상태와_근거는_원자적이다()
            throws Exception {
        ReportAcquireResponse acquired = reportAcquireService.acquire(
                new ReportAcquireRequest(
                        studyId,
                        sessionId,
                        apartmentId,
                        OCCURRED_AT
                )
        );
        ReportCompleteRequest completeRequest = completeRequest(acquired);
        ReportFailRequest failRequest = request(
                acquired.processingToken(),
                acquired.processingAttempt(),
                false
        );

        List<CommandOutcome> outcomes = invokeConcurrently(
                () -> reportCompleteService.complete(
                        acquired.reportId(),
                        completeRequest
                ),
                () -> reportFailService.fail(
                        acquired.reportId(),
                        failRequest
                )
        );

        assertThat(outcomes).filteredOn(CommandOutcome::success).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.success())
                .extracting(CommandOutcome::errorCode)
                .containsExactly(ErrorCode.STALE_PROCESSING_TOKEN);

        StoredFailure stored = storedFailure(acquired.reportId());
        Integer evidenceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_evidence WHERE report_id = ?",
                Integer.class,
                acquired.reportId()
        );
        if ("DONE".equals(stored.status())) {
            assertThat(stored.resultJson()).isNotNull();
            assertThat(stored.completePayloadHash()).hasSize(64);
            assertThat(stored.failCode()).isNull();
            assertThat(stored.failPayloadHash()).isNull();
            assertThat(stored.failedAt()).isNull();
            assertThat(evidenceCount).isEqualTo(1);
            assertThat(studyStatus()).isEqualTo("COMPLETED");
        } else {
            assertThat(stored.status()).isEqualTo("FAILED");
            assertThat(stored.resultJson()).isNull();
            assertThat(stored.completePayloadHash()).isNull();
            assertThat(stored.failCode())
                    .isEqualTo("NORMALIZATION_FAILED");
            assertThat(stored.failPayloadHash()).hasSize(64);
            assertThat(stored.failedAt()).isNotNull();
            assertThat(evidenceCount).isZero();
            assertThat(studyStatus()).isEqualTo("IN_PROGRESS");
        }
    }

    private List<CommandOutcome> invokeConcurrently(
            Runnable complete,
            Runnable fail
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<CommandOutcome> completeOutcome = pool.submit(
                    () -> execute(complete, ready, start)
            );
            Future<CommandOutcome> failOutcome = pool.submit(
                    () -> execute(fail, ready, start)
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    completeOutcome.get(30, TimeUnit.SECONDS),
                    failOutcome.get(30, TimeUnit.SECONDS)
            );
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("executor did not terminate");
            }
        }
    }

    private CommandOutcome execute(
            Runnable command,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            command.run();
            return new CommandOutcome(true, null);
        } catch (BusinessException exception) {
            return new CommandOutcome(false, exception.getErrorCode());
        }
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

    private ReportCompleteRequest completeRequest(
            ReportAcquireResponse acquired
    ) {
        ReportGenerationResultRequest generationResult =
                new ReportGenerationResultRequest(
                        "교통 리포트",
                        "교통 접근성이 좋습니다.",
                        new ReportGenerationResultRequest.Metrics(
                                1,
                                1,
                                100.0,
                                1
                        ),
                        List.of(new ReportGenerationResultRequest.Feature(
                                1,
                                "교통",
                                "교통 접근성이 좋습니다.",
                                1,
                                List.of("P1")
                        )),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new ReportGenerationResultRequest.Category(
                                "TRANSPORT",
                                "교통 기록을 분석했습니다.",
                                0,
                                0,
                                false,
                                List.of()
                        ))
                );
        ReportEvidenceResultRequest evidenceResult =
                new ReportEvidenceResultRequest(List.of(
                        new ReportEvidenceResultRequest.Claim(
                                "feature.transport",
                                ReportEvidenceResultRequest.ClaimType
                                        .FEATURE_POSITIVE,
                                null,
                                "교통",
                                ReportGenerationResultRequest.OpinionType
                                        .POSITIVE,
                                List.of("P1"),
                                List.of(new ReportEvidenceResultRequest.Evidence(
                                        ReportEvidenceResultRequest.SourceType
                                                .TEXT,
                                        fieldRecordId,
                                        "P1",
                                        checklistItemId,
                                        "TRANSPORT",
                                        OCCURRED_AT,
                                        ReportEvidenceResultRequest.EvidenceRole
                                                .SUPPORT
                                )),
                                1
                        )
                ));
        return new ReportCompleteRequest(
                acquired.processingToken(),
                acquired.processingAttempt(),
                generationResult,
                evidenceResult
        );
    }

    private ReportFailRequest request(
            String processingToken,
            int processingAttempt,
            boolean retryable
    ) {
        return new ReportFailRequest(
                processingToken,
                processingAttempt,
                ReportWorkerProgressStage.NORMALIZATION,
                ReportWorkerErrorCode.NORMALIZATION_FAILED,
                "리포트 입력 정규화에 실패했습니다.",
                retryable
        );
    }

    private StoredFailure storedFailure(Long reportId) {
        return jdbcTemplate.queryForObject("""
                SELECT status,
                       progress_stage,
                       fail_code,
                       fail_reason,
                       is_retryable,
                       fail_payload_hash,
                       processing_attempt,
                       processing_token_hash,
                       processing_lease_expires_at,
                       failed_at,
                       completed_at,
                       complete_payload_hash,
                       result_json::text,
                       updated_at
                FROM report
                WHERE id = ?
                """, (resultSet, rowNum) -> new StoredFailure(
                resultSet.getString("status"),
                resultSet.getString("progress_stage"),
                resultSet.getString("fail_code"),
                resultSet.getString("fail_reason"),
                resultSet.getBoolean("is_retryable"),
                resultSet.getString("fail_payload_hash"),
                resultSet.getInt("processing_attempt"),
                resultSet.getString("processing_token_hash"),
                resultSet.getObject("processing_lease_expires_at"),
                resultSet.getObject("failed_at"),
                resultSet.getObject("completed_at"),
                resultSet.getString("complete_payload_hash"),
                resultSet.getString("result_json"),
                resultSet.getObject("updated_at")
        ), reportId);
    }

    private String studyStatus() {
        return jdbcTemplate.queryForObject("""
                SELECT status
                FROM study
                WHERE id = ?
                """, String.class, studyId);
    }

    private void seed() {
        apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE019-FAIL', 127.0, 37.5)
                RETURNING id
                """, Long.class, "BE019-" + UUID.randomUUID());
        memberId = insertMember();
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (
                    apartment_id, leader_id, goal, capacity, title, status
                ) VALUES (
                    ?, ?, 'be019-fail-goal', 5,
                    'be019-fail', 'IN_PROGRESS'
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
        checklistItemId = jdbcTemplate.queryForObject("""
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
        fieldRecordId = jdbcTemplate.queryForObject("""
                INSERT INTO field_record (
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, client_request_id, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, 'TEXT', '역이 가깝습니다.', ?, ?, ?
                )
                RETURNING id
                """, Long.class,
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
                "be019-fail-" + suffix + "@test.local",
                "fail" + suffix);
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
                "DELETE FROM study WHERE title = 'be019-fail'"
        );
        jdbcTemplate.update(
                "DELETE FROM apartment WHERE name = 'BE019-FAIL'"
        );
        jdbcTemplate.update("""
                DELETE FROM member
                WHERE email LIKE 'be019-fail-%@test.local'
                """);
    }

    private record StoredFailure(
            String status,
            String progressStage,
            String failCode,
            String failReason,
            boolean retryable,
            String failPayloadHash,
            int processingAttempt,
            String processingTokenHash,
            Object processingLeaseExpiresAt,
            Object failedAt,
            Object completedAt,
            String completePayloadHash,
            String resultJson,
            Object updatedAt
    ) {
    }

    private record CommandOutcome(boolean success, ErrorCode errorCode) {
    }
}
