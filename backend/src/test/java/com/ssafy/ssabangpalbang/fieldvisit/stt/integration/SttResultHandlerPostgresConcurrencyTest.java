package com.ssafy.ssabangpalbang.fieldvisit.stt.integration;

import com.ssafy.ssabangpalbang.auth.token.RefreshTokenStore;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttResultHandler;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttSuccessResult;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.quality.Be033PostgresContainerFactory;
import com.ssafy.ssabangpalbang.study.service.port.StudyNotificationPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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

@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "management.health.redis.enabled=false",
        "ssabangpalbang.fieldvisit.stt.reliability.enabled=false",
        "ssabangpalbang.fieldvisit.stt.kafka.enabled=true",
        "ssabangpalbang.fieldvisit.stt.kafka.result-topic=field-visit.stt.result.v1",
        "ssabangpalbang.fieldvisit.stt.kafka.result-dlt-topic=field-visit.stt.result.dlt.v1",
        "ssabangpalbang.fieldvisit.stt.kafka.consumer-group=be033-stt-result",
        "ssabangpalbang.fieldvisit.stt.kafka.result-retry-backoff=10ms",
        "ssabangpalbang.fieldvisit.stt.kafka.result-max-retries=1"
})
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {
                SttResultHandlerPostgresConcurrencyTest.RESULT_TOPIC,
                SttResultHandlerPostgresConcurrencyTest.RESULT_DLT_TOPIC
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class SttResultHandlerPostgresConcurrencyTest {

    private static final String STT_ID = "be033-concurrent-done";
    static final String RESULT_TOPIC = "field-visit.stt.result.v1";
    static final String RESULT_DLT_TOPIC = "field-visit.stt.result.dlt.v1";
    private static final OffsetDateTime COMPLETED_AT = OffsetDateTime.of(
            2026, 8, 2, 16, 0, 0, 0, ZoneOffset.ofHours(9)
    );

    @Container
    static final PostgreSQLContainer<?> POSTGRES = Be033PostgresContainerFactory.create();

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
    }

    @Autowired
    private SttResultHandler resultHandler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private SttDispatchPort sttDispatchPort;

    @MockitoBean
    private SttAudioDeletionPort sttAudioDeletionPort;

    @MockitoBean
    private GatewayMediaAccessUrlProvider mediaAccessUrlProvider;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @MockitoBean
    private StudyNotificationPort studyNotificationPort;

    @BeforeEach
    void setUp() {
        cleanup();
        seedPendingJob();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void 동일_DONE을_동시에_처리해도_기록과_cleanup은_각_한_건이다() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<SttResultHandler.HandlingOutcome> first = pool.submit(
                    () -> handleSuccess(ready, start)
            );
            Future<SttResultHandler.HandlingOutcome> second = pool.submit(
                    () -> handleSuccess(ready, start)
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<SttResultHandler.HandlingOutcome> outcomes = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            );

            assertThat(outcomes).containsExactlyInAnyOrder(
                    SttResultHandler.HandlingOutcome.APPLIED,
                    SttResultHandler.HandlingOutcome.IGNORED_DUPLICATE
            );
            assertThat(count("field_record", "client_request_id = 'STT:' || ?", STT_ID))
                    .isEqualTo(1L);
            assertThat(count("stt_audio_cleanup_job", "object_key = ?", "be033/concurrent.webm"))
                    .isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM stt_job WHERE stt_id = ?",
                    String.class,
                    STT_ID
            )).isEqualTo("DONE");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT upload_status FROM file_meta WHERE s3_key = ?",
                    String.class,
                    "be033/concurrent.webm"
            )).isEqualTo("DELETED");
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void 실제_Kafka가_동일_DONE을_중복_전달해도_DB_부수효과는_한_번이다() throws Exception {
        String payload = """
                {
                  "schemaVersion": 1,
                  "sttId": "be033-concurrent-done",
                  "attemptNo": 1,
                  "status": "DONE",
                  "textContent": "Kafka 중복 DONE 최종 반영",
                  "completedAt": "2026-08-02T16:00:00+09:00"
                }
                """;

        kafkaTemplate.send(RESULT_TOPIC, STT_ID, payload)
                .get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(RESULT_TOPIC, STT_ID, payload)
                .get(10, TimeUnit.SECONDS);

        awaitCount("field_record", "client_request_id = 'STT:' || ?", STT_ID, 1L);
        awaitCount(
                "stt_audio_cleanup_job",
                "object_key = ?",
                "be033/concurrent.webm",
                1L
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM stt_job WHERE stt_id = ?",
                String.class,
                STT_ID
        )).isEqualTo("DONE");
    }

    private SttResultHandler.HandlingOutcome handleSuccess(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        return resultHandler.handleSuccess(new SttSuccessResult(
                STT_ID,
                1,
                "동시에 도착한 DONE 중 한 건만 반영",
                COMPLETED_AT
        ));
    }

    private void awaitCount(
            String table,
            String where,
            Object value,
            long expected
    ) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        long actual;
        do {
            actual = count(table, where, value);
            if (actual == expected) {
                return;
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        assertThat(actual).isEqualTo(expected);
    }

    private Long count(String table, String where, Object value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + where,
                Long.class,
                value
        );
    }

    private void seedPendingJob() {
        Long memberId = jdbcTemplate.queryForObject("""
                INSERT INTO member (
                    email, nickname, age_group_public_agreed,
                    service_notification_agreed, ad_notification_agreed
                ) VALUES ('be033-stt@test.local', 'be033-stt', false, true, false)
                RETURNING id
                """, Long.class);
        Long apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES ('BE033-STT', 'BE033-STT', 127.0, 37.5) RETURNING id
                """, Long.class);
        Long studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (apartment_id, leader_id, goal, capacity, title, status)
                VALUES (?, ?, 'BE033 STT', 2, 'be033-stt', 'IN_PROGRESS') RETURNING id
                """, Long.class, apartmentId, memberId);
        Long sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO field_session (study_id, status)
                VALUES (?, 'IN_PROGRESS') RETURNING id
                """, Long.class, studyId);
        Long checklistId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist (session_id, member_id)
                VALUES (?, ?) RETURNING id
                """, Long.class, sessionId, memberId);
        Long itemId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist_item (checklist_id, category, title, display_order)
                VALUES (?, 'BE033', 'STT 동시성', 1) RETURNING id
                """, Long.class, checklistId);
        Long fileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_meta (
                    owner_id, study_id, file_usage, s3_key, content_type,
                    upload_status
                ) VALUES (?, ?, 'STT_AUDIO', 'be033/concurrent.webm',
                          'audio/webm', 'COMPLETED') RETURNING id
                """, Long.class, memberId, studyId);
        Long jobId = jdbcTemplate.queryForObject("""
                INSERT INTO stt_job (
                    stt_id, member_id, study_id, session_id, audio_file_id,
                    checklist_item_id, initial_client_request_id, status,
                    requested_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', now(), 0)
                RETURNING id
                """, Long.class, STT_ID, memberId, studyId, sessionId, fileId,
                itemId, UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO stt_job_attempt (
                    stt_job_id, member_id, client_request_id, attempt_no,
                    request_type, status, requested_at
                ) VALUES (?, ?, ?, 1, 'INITIAL', 'PENDING', now())
                """, jobId, memberId, UUID.randomUUID());
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM stt_audio_cleanup_job WHERE object_key LIKE 'be033/%'");
        jdbcTemplate.update("DELETE FROM stt_dispatch_outbox WHERE stt_id = ?", STT_ID);
        jdbcTemplate.update("DELETE FROM stt_job_attempt WHERE stt_job_id IN "
                + "(SELECT id FROM stt_job WHERE stt_id = ?)", STT_ID);
        jdbcTemplate.update("DELETE FROM stt_job WHERE stt_id = ?", STT_ID);
        jdbcTemplate.update("DELETE FROM field_record WHERE client_request_id = 'STT:' || ?", STT_ID);
        jdbcTemplate.update("DELETE FROM checklist_item WHERE category = 'BE033'");
        jdbcTemplate.update("DELETE FROM checklist WHERE session_id IN "
                + "(SELECT id FROM field_session WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be033-stt'))");
        jdbcTemplate.update("DELETE FROM field_session WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be033-stt')");
        jdbcTemplate.update("DELETE FROM file_meta WHERE s3_key LIKE 'be033/%'");
        jdbcTemplate.update("DELETE FROM study WHERE title = 'be033-stt'");
        jdbcTemplate.update("DELETE FROM apartment WHERE complex_code = 'BE033-STT'");
        jdbcTemplate.update("DELETE FROM member WHERE email = 'be033-stt@test.local'");
    }
}
