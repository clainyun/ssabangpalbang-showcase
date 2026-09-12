package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldRecordCreateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * 동일 clientRequestId 병렬 POST가 UNIQUE로 한 건만 저장되고
 * DataIntegrityViolationException이 500으로 새지 않는지 검증한다(BE-017).
 */
@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
class FieldVisitBe017CreateConcurrencyPostgresTest {

    private static final int CONCURRENT_REQUESTS = 5;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-postgres-test", false
        ).withDockerfile(Path.of("..", "infra", "postgres", "Dockerfile"));
        String imageId = image.get();
        return new PostgreSQLContainer<>(
                DockerImageName.parse(imageId).asCompatibleSubstituteFor("postgres")
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
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 6379);
        registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:9092");
    }

    @Autowired
    private FieldRecordService fieldRecordService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SttDispatchPort sttDispatchPort;

    @MockitoBean
    private SttAudioDeletionPort sttAudioDeletionPort;

    @MockitoBean
    private GatewayMediaAccessUrlProvider mediaAccessUrlProvider;

    private Long studyId;
    private Long memberId;
    private Long checklistItemId;

    @BeforeEach
    void setUp() {
        cleanup();
        when(mediaAccessUrlProvider.issueAll(anyCollection())).thenReturn(java.util.Map.of());
        seed();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void 동일_clientRequestId_병렬_POST는_한_건만_저장한다() throws Exception {
        String clientRequestId = UUID.randomUUID().toString();
        String text = "BE017 동시 재전송 메모";
        FieldRecordCreateRequest request = new FieldRecordCreateRequest(
                checklistItemId,
                "TEXT",
                text,
                null,
                clientRequestId
        );

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        List<Future<FieldRecordService.CreateResult>> futures = new ArrayList<>();
        AtomicInteger unexpectedErrors = new AtomicInteger();

        try {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    await(start);
                    try {
                        return fieldRecordService.create(studyId, memberId, request);
                    } catch (Throwable ex) {
                        unexpectedErrors.incrementAndGet();
                        throw ex;
                    }
                }));
            }

            start.countDown();

            List<FieldRecordService.CreateResult> results = new ArrayList<>();
            for (Future<FieldRecordService.CreateResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(unexpectedErrors.get()).isZero();
            assertThat(results).hasSize(CONCURRENT_REQUESTS);

            long created = results.stream()
                    .filter(r -> r.httpStatus() == HttpStatus.CREATED)
                    .count();
            long replayed = results.stream()
                    .filter(r -> r.httpStatus() == HttpStatus.OK)
                    .count();
            assertThat(created).isEqualTo(1);
            assertThat(replayed).isEqualTo(CONCURRENT_REQUESTS - 1);
            assertThat(results).allMatch(r ->
                    r.httpStatus() == HttpStatus.CREATED || r.httpStatus() == HttpStatus.OK
            );

            Long sourceId = results.get(0).body().record().sourceId();
            assertThat(results).allSatisfy(r -> {
                assertThat(r.body().record().sourceId()).isEqualTo(sourceId);
                assertThat(r.body().record().textContent()).isEqualTo(text);
                assertThat(r.body().record().clientRequestId()).isEqualTo(clientRequestId);
            });

            Integer rowCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM field_record WHERE client_request_id = ?",
                    Integer.class,
                    clientRequestId
            );
            assertThat(rowCount).isEqualTo(1);

            String storedText = jdbcTemplate.queryForObject(
                    "SELECT text_content FROM field_record WHERE client_request_id = ?",
                    String.class,
                    clientRequestId
            );
            assertThat(storedText).isEqualTo(text);
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("executor did not terminate");
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private void seed() {
        Long apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE017-CONC', 127.0, 37.5) RETURNING id
                """, Long.class, "BE017C-" + UUID.randomUUID());
        memberId = insertMember("be017-conc");
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (apartment_id, leader_id, goal, capacity, title, status)
                VALUES (?, ?, 'be017-conc-goal', 5, 'be017-conc', 'RECRUITING') RETURNING id
                """, Long.class, apartmentId, memberId);
        jdbcTemplate.update("""
                INSERT INTO study_member (study_id, member_id, status) VALUES (?, ?, 'ACTIVE')
                """, studyId, memberId);
        Long sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO field_session (study_id, status) VALUES (?, 'IN_PROGRESS') RETURNING id
                """, Long.class, studyId);
        jdbcTemplate.update("""
                INSERT INTO field_participant (session_id, member_id, status)
                VALUES (?, ?, 'IN_PROGRESS')
                """, sessionId, memberId);
        Long checklistId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist (session_id, member_id, is_fallback)
                VALUES (?, ?, false) RETURNING id
                """, Long.class, sessionId, memberId);
        checklistItemId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist_item (checklist_id, category, title, display_order)
                VALUES (?, '교통', '역', 1) RETURNING id
                """, Long.class, checklistId);
    }

    private Long insertMember(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (
                    email, nickname, age_group_public_agreed,
                    service_notification_agreed, ad_notification_agreed
                ) VALUES (?, ?, false, true, false) RETURNING id
                """, Long.class, prefix + "-" + suffix + "@test.local", prefix + suffix);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM field_record");
        jdbcTemplate.update("DELETE FROM checklist_answer");
        jdbcTemplate.update("DELETE FROM checklist_item");
        jdbcTemplate.update("DELETE FROM checklist");
        jdbcTemplate.update("DELETE FROM field_participant");
        jdbcTemplate.update("DELETE FROM field_session");
        jdbcTemplate.update("DELETE FROM report");
        jdbcTemplate.update("DELETE FROM file_meta");
        jdbcTemplate.update("DELETE FROM study_member");
        jdbcTemplate.update("DELETE FROM study WHERE title = 'be017-conc'");
        jdbcTemplate.update("DELETE FROM apartment WHERE name = 'BE017-CONC'");
        jdbcTemplate.update("DELETE FROM member WHERE email LIKE 'be017-conc%@test.local'");
    }
}
