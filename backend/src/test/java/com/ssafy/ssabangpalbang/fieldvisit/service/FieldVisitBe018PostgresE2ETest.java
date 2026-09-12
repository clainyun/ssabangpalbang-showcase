package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitCloseRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitFinishRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
class FieldVisitBe018PostgresE2ETest {

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
    private FieldVisitFinishService finishService;

    @Autowired
    private FieldVisitCloseService closeService;

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

    private Long studyId;
    private Long sessionId;
    private Long leaderId;
    private List<Long> memberIds;

    @BeforeEach
    void setUp() {
        cleanup();
        clearInvocations(reportRequestPort);
        when(mediaAccessUrlProvider.issueAll(anyCollection()))
                .thenReturn(java.util.Map.of());
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void 참여자_세_명이_순차_종료하면_세_번째에서만_세션이_종료된다() {
        seed(3, false);

        finish(memberIds.get(0), "finish-1");
        assertThat(sessionStatus()).isEqualTo("IN_PROGRESS");
        finish(memberIds.get(1), "finish-2");
        assertThat(sessionStatus()).isEqualTo("IN_PROGRESS");
        finish(memberIds.get(2), "finish-3");

        assertThat(sessionStatus()).isEqualTo("ENDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT end_reason FROM field_session WHERE id = ?",
                String.class,
                sessionId
        )).isEqualTo("ALL_ENDED");
        verify(reportRequestPort, times(1)).request(any());
    }

    @Test
    void 마지막_finish와_close가_동시에_실행돼도_이벤트는_한_번이다()
            throws Exception {
        seed(2, true);
        Long finishingMemberId = memberIds.get(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> finishFuture = pool.submit(() -> {
                await(ready, start);
                finish(finishingMemberId, "finish-concurrent");
            });
            Future<?> closeFuture = pool.submit(() -> {
                await(ready, start);
                close("close-concurrent");
            });

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            finishFuture.get(30, TimeUnit.SECONDS);
            closeFuture.get(30, TimeUnit.SECONDS);
        } finally {
            shutdown(pool);
        }

        assertThat(sessionStatus()).isEqualTo("ENDED");
        verify(reportRequestPort, times(1)).request(any());
    }

    @Test
    void 같은_clientRequestId_close_동시_호출은_강제종료_수와_이벤트를_중복시키지_않는다()
            throws Exception {
        seed(3, false);
        String clientRequestId = UUID.randomUUID().toString();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<FieldVisitCloseService.CloseResult>> futures =
                new ArrayList<>();

        try {
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    await(ready, start);
                    return closeService.close(
                            studyId,
                            leaderId,
                            new FieldVisitCloseRequestBody(
                                    true,
                                    clientRequestId
                            )
                    );
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int forcedEndedTotal = 0;
            for (Future<FieldVisitCloseService.CloseResult> future : futures) {
                forcedEndedTotal += future.get(30, TimeUnit.SECONDS)
                        .body()
                        .forcedEndedCount();
            }
            assertThat(forcedEndedTotal).isLessThanOrEqualTo(3);
        } finally {
            shutdown(pool);
        }

        Integer endedParticipants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_participant "
                        + "WHERE session_id = ? AND status = 'ENDED'",
                Integer.class,
                sessionId
        );
        assertThat(endedParticipants).isEqualTo(3);
        verify(reportRequestPort, times(1)).request(any());
    }

    @Test
    void 음수_체류시간은_DB_CHECK_제약이_거부한다() {
        seed(1, false);
        Long participantId = jdbcTemplate.queryForObject(
                "SELECT id FROM field_participant WHERE session_id = ?",
                Long.class,
                sessionId
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE field_participant SET stay_duration_sec = -1 WHERE id = ?",
                participantId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void finish(Long memberId, String clientRequestId) {
        finishService.finish(
                studyId,
                memberId,
                new FieldVisitFinishRequestBody(true, clientRequestId)
        );
    }

    private void close(String clientRequestId) {
        closeService.close(
                studyId,
                leaderId,
                new FieldVisitCloseRequestBody(true, clientRequestId)
        );
    }

    private String sessionStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM field_session WHERE id = ?",
                String.class,
                sessionId
        );
    }

    private void seed(int participantCount, boolean leaderAlreadyEnded) {
        Long apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE018-APT', 127.0, 37.5) RETURNING id
                """, Long.class, "BE018-" + UUID.randomUUID());
        memberIds = new ArrayList<>();
        for (int i = 0; i < participantCount; i++) {
            memberIds.add(insertMember("be018-member-" + i));
        }
        leaderId = memberIds.get(0);
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (
                    apartment_id, leader_id, goal, capacity, title, status
                ) VALUES (
                    ?, ?, 'be018-goal', 5, 'be018-study', 'IN_PROGRESS'
                ) RETURNING id
                """, Long.class, apartmentId, leaderId);

        for (int i = 0; i < memberIds.size(); i++) {
            jdbcTemplate.update("""
                    INSERT INTO study_member (
                        study_id, member_id, role, status
                    ) VALUES (?, ?, ?, 'ACTIVE')
                    """,
                    studyId,
                    memberIds.get(i),
                    i == 0 ? "LEADER" : "MEMBER"
            );
        }

        Instant startedAt = Instant.now().minusSeconds(3600);
        sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO field_session (study_id, status, started_at)
                VALUES (?, 'IN_PROGRESS', ?) RETURNING id
                """, Long.class, studyId, Timestamp.from(startedAt));
        for (Long memberId : memberIds) {
            jdbcTemplate.update("""
                    INSERT INTO field_participant (
                        session_id, member_id, status, started_at
                    ) VALUES (?, ?, 'IN_PROGRESS', ?)
                    """, sessionId, memberId, Timestamp.from(startedAt));
        }
        if (leaderAlreadyEnded) {
            jdbcTemplate.update("""
                    UPDATE field_participant
                    SET status = 'ENDED',
                        ended_at = ?,
                        end_reason = 'SELF_ENDED',
                        stay_duration_sec = 3590
                    WHERE session_id = ? AND member_id = ?
                    """,
                    Timestamp.from(Instant.now().minusSeconds(10)),
                    sessionId,
                    leaderId
            );
        }
    }

    private Long insertMember(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (
                    email, nickname, age_group_public_agreed,
                    service_notification_agreed, ad_notification_agreed
                ) VALUES (?, ?, false, true, false) RETURNING id
                """,
                Long.class,
                prefix + "-" + suffix + "@test.local",
                prefix + suffix
        );
    }

    private static void await(
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("start latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static void shutdown(ExecutorService pool) throws Exception {
        pool.shutdownNow();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("executor did not terminate");
        }
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM report");
        jdbcTemplate.update("DELETE FROM field_visit_close_vote");
        jdbcTemplate.update("DELETE FROM field_visit_start_request");
        jdbcTemplate.update("DELETE FROM field_record");
        jdbcTemplate.update("DELETE FROM checklist_answer");
        jdbcTemplate.update("DELETE FROM checklist_item");
        jdbcTemplate.update("DELETE FROM checklist");
        jdbcTemplate.update("DELETE FROM field_participant");
        jdbcTemplate.update("DELETE FROM field_visit_candidate");
        jdbcTemplate.update("DELETE FROM field_session");
        jdbcTemplate.update("DELETE FROM schedule WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be018-study')");
        jdbcTemplate.update("DELETE FROM study_member WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be018-study')");
        jdbcTemplate.update("DELETE FROM study WHERE title = 'be018-study'");
        jdbcTemplate.update("DELETE FROM apartment WHERE name = 'BE018-APT'");
        jdbcTemplate.update("DELETE FROM member WHERE email LIKE 'be018-%@test.local'");
    }
}
