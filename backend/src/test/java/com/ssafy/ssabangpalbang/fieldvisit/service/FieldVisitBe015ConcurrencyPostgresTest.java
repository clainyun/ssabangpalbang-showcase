package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldRecordUpdateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrl;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * PATCH와 DELETE가 동일 field_record를 동시에 변경해도 soft-delete가 되살아나지 않는지 검증한다.
 */
@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
class FieldVisitBe015ConcurrencyPostgresTest {

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
    private Long recordId;
    private String clientRequestId;
    private String fingerprint;

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
    void PATCH와_DELETE_동시_요청_후_deletedAt이_유지된다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> patchError = new AtomicReference<>();
        AtomicReference<Throwable> deleteError = new AtomicReference<>();

        Future<?> patchFuture = pool.submit(() -> {
            await(start);
            try {
                fieldRecordService.update(
                        studyId,
                        memberId,
                        recordId,
                        new FieldRecordUpdateRequest(null, "동시성 수정", null)
                );
            } catch (Throwable ex) {
                patchError.set(ex);
            }
        });
        Future<?> deleteFuture = pool.submit(() -> {
            await(start);
            try {
                fieldRecordService.delete(studyId, memberId, recordId);
            } catch (Throwable ex) {
                deleteError.set(ex);
            }
        });

        start.countDown();
        patchFuture.get(30, TimeUnit.SECONDS);
        deleteFuture.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        Instant deletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM field_record WHERE id = ?",
                Instant.class,
                recordId
        );
        assertThat(deletedAt).as("동시 PATCH 후에도 soft-delete가 유지되어야 한다").isNotNull();

        String text = jdbcTemplate.queryForObject(
                "SELECT text_content FROM field_record WHERE id = ?",
                String.class,
                recordId
        );
        Long authorId = jdbcTemplate.queryForObject(
                "SELECT author_id FROM field_record WHERE id = ?",
                Long.class,
                recordId
        );
        String sourceType = jdbcTemplate.queryForObject(
                "SELECT source_type FROM field_record WHERE id = ?",
                String.class,
                recordId
        );
        String storedClientRequestId = jdbcTemplate.queryForObject(
                "SELECT client_request_id FROM field_record WHERE id = ?",
                String.class,
                recordId
        );
        String storedFingerprint = jdbcTemplate.queryForObject(
                "SELECT request_fingerprint FROM field_record WHERE id = ?",
                String.class,
                recordId
        );

        assertThat(authorId).isEqualTo(memberId);
        assertThat(sourceType).isEqualTo("TEXT");
        assertThat(storedClientRequestId).isEqualTo(clientRequestId);
        assertThat(storedFingerprint).isEqualTo(fingerprint);
        assertThat(text).isIn("동시성", "동시성 수정");

        // 두 번째 DELETE는 멱등
        var secondDelete = fieldRecordService.delete(studyId, memberId, recordId);
        Instant deletedAtAfter = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM field_record WHERE id = ?",
                Instant.class,
                recordId
        );
        assertThat(deletedAtAfter).isEqualTo(deletedAt);
        assertThat(secondDelete.body().deleted()).isTrue();

        // 동시 DELETE 멱등성
        Long secondRecordId = insertTextRecord("동시삭제");
        CountDownLatch deleteStart = new CountDownLatch(1);
        List<Future<?>> deletes = new ArrayList<>();
        ExecutorService deletePool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 2; i++) {
            deletes.add(deletePool.submit(() -> {
                await(deleteStart);
                fieldRecordService.delete(studyId, memberId, secondRecordId);
            }));
        }
        deleteStart.countDown();
        for (Future<?> future : deletes) {
            future.get(30, TimeUnit.SECONDS);
        }
        deletePool.shutdown();
        Integer deletedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_record WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                secondRecordId
        );
        assertThat(deletedCount).isEqualTo(1);
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
                VALUES (?, 'BE015-CONC', 127.0, 37.5) RETURNING id
                """, Long.class, "BE015C-" + UUID.randomUUID());
        memberId = insertMember("be015-conc");
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (apartment_id, leader_id, goal, capacity, title, status)
                VALUES (?, ?, 'be015-conc-goal', 5, 'be015-conc', 'RECRUITING') RETURNING id
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
        recordId = insertTextRecord("동시성");
    }

    private Long insertTextRecord(String text) {
        clientRequestId = UUID.randomUUID().toString();
        fingerprint = FieldRecordFingerprint.ofText(checklistItemId, text);
        return jdbcTemplate.queryForObject("""
                INSERT INTO field_record(
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, client_request_id, request_fingerprint
                )
                SELECT fs.id, ?, ?, 'TEXT', ?, ?, ?
                FROM field_session fs WHERE fs.study_id = ?
                RETURNING id
                """, Long.class, checklistItemId, memberId, text, clientRequestId, fingerprint, studyId);
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
        jdbcTemplate.update("DELETE FROM study WHERE title = 'be015-conc'");
        jdbcTemplate.update("DELETE FROM apartment WHERE name = 'BE015-CONC'");
        jdbcTemplate.update("DELETE FROM member WHERE email LIKE 'be015-conc%@test.local'");
    }
}
