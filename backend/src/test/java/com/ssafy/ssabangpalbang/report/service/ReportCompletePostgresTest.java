package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.report.dto.request.ReportAcquireRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportCompleteRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportEvidenceResultRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import com.ssafy.ssabangpalbang.report.dto.response.ReportAcquireResponse;
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
class ReportCompletePostgresTest {

    private static final OffsetDateTime RECORDED_AT = OffsetDateTime.of(
            2026,
            8,
            2,
            12,
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
    private ReportCompleteService reportCompleteService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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
    void V17_JSONB와_근거를_원자_저장하고_동일_완료_요청을_멱등_처리한다()
            throws Exception {
        ReportAcquireResponse acquired = reportAcquireService.acquire(
                new ReportAcquireRequest(
                        studyId,
                        sessionId,
                        apartmentId,
                        RECORDED_AT
                )
        );
        ReportCompleteRequest request = request(
                acquired.processingToken(),
                acquired.processingAttempt(),
                "교통 접근성이 좋습니다."
        );

        reportCompleteService.complete(acquired.reportId(), request);
        reportCompleteService.complete(acquired.reportId(), request);

        Integer v17Applied = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '17'
                  AND success = TRUE
                """, Integer.class);
        StoredReport stored = jdbcTemplate.queryForObject("""
                SELECT status,
                       progress_stage,
                       result_json::text,
                       complete_payload_hash,
                       processing_attempt,
                       processing_token_hash,
                       processing_lease_expires_at,
                       completed_at
                FROM report
                WHERE id = ?
                """, (resultSet, rowNum) -> new StoredReport(
                resultSet.getString("status"),
                resultSet.getString("progress_stage"),
                resultSet.getString("result_json"),
                resultSet.getString("complete_payload_hash"),
                resultSet.getInt("processing_attempt"),
                resultSet.getString("processing_token_hash"),
                resultSet.getObject("processing_lease_expires_at"),
                resultSet.getObject("completed_at")
        ), acquired.reportId());
        Integer evidenceCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM report_evidence
                WHERE report_id = ?
                  AND field_record_id = ?
                  AND claim_key = 'feature.transport'
                  AND display_order = 1
                """, Integer.class, acquired.reportId(), fieldRecordId);
        StoredAutomaticPost automaticPost = jdbcTemplate.queryForObject("""
                SELECT author_id,
                       board_type,
                       title,
                       content,
                       is_auto_report,
                       report_id,
                       apartment_id
                FROM post
                WHERE report_id = ?
                  AND is_auto_report = TRUE
                """, (resultSet, rowNum) -> new StoredAutomaticPost(
                resultSet.getObject("author_id", Long.class),
                resultSet.getString("board_type"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getBoolean("is_auto_report"),
                resultSet.getLong("report_id"),
                resultSet.getLong("apartment_id")
        ), acquired.reportId());
        String completedStudyStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM study
                WHERE id = ?
                """, String.class, studyId);
        JsonNode resultJson = objectMapper.readTree(stored.resultJson());

        assertThat(v17Applied).isEqualTo(1);
        Integer v20Applied = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '20'
                  AND success = TRUE
                """, Integer.class);
        assertThat(v20Applied).isEqualTo(1);
        assertThat(stored.status()).isEqualTo("DONE");
        assertThat(stored.progressStage()).isEqualTo("COMPLETED");
        assertThat(resultJson.path("title").asText()).isEqualTo("교통 리포트");
        assertThat(resultJson.has("generationResult")).isFalse();
        assertThat(stored.completePayloadHash()).hasSize(64);
        assertThat(stored.processingAttempt())
                .isEqualTo(acquired.processingAttempt());
        assertThat(stored.processingTokenHash()).hasSize(64);
        assertThat(stored.processingLeaseExpiresAt()).isNull();
        assertThat(stored.completedAt()).isNotNull();
        assertThat(evidenceCount).isEqualTo(1);
        assertThat(automaticPost.authorId()).isNull();
        assertThat(automaticPost.boardType()).isEqualTo("INFORMATION");
        assertThat(automaticPost.title()).isEqualTo("교통 리포트");
        assertThat(automaticPost.autoReport()).isTrue();
        assertThat(automaticPost.reportId()).isEqualTo(acquired.reportId());
        assertThat(automaticPost.apartmentId()).isEqualTo(apartmentId);
        assertThat(automaticPost.content()).isEqualTo(
                "아파트: BE019-COMPLETE\n"
                        + "임장일: 2026-08-02\n"
                        + "요약: 교통 접근성이 좋습니다.\n\n"
                        + "리포트 상세: /api/v1/reports/"
                        + acquired.reportId()
        );
        assertThat(completedStudyStatus).isEqualTo("COMPLETED");

        jdbcTemplate.update("""
                UPDATE post
                SET deleted_at = NOW()
                WHERE report_id = ?
                  AND is_auto_report = TRUE
                """, acquired.reportId());
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO post (
                    board_type,
                    author_id,
                    title,
                    content,
                    status,
                    is_auto_report,
                    report_id,
                    apartment_id,
                    view_count
                )
                VALUES ('INFORMATION', NULL, '중복 리포트 글', '중복 생성 검증',
                        'ACTIVE', TRUE, ?, ?, 0)
                """, acquired.reportId(), apartmentId))
                .isInstanceOf(RuntimeException.class);

        ReportCompleteRequest changed = request(
                acquired.processingToken(),
                acquired.processingAttempt(),
                "변경된 결과입니다."
        );
        assertThatThrownBy(() -> reportCompleteService.complete(
                acquired.reportId(),
                changed
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.COMPLETE_PAYLOAD_CONFLICT)
        );
    }

    @Test
    void 자동_게시글_삽입에_실패하면_리포트_완료와_근거_저장을_모두_롤백한다()
            throws Exception {
        ReportAcquireResponse acquired = reportAcquireService.acquire(
                new ReportAcquireRequest(
                        studyId,
                        sessionId,
                        apartmentId,
                        RECORDED_AT
                )
        );
        ReportCompleteRequest request = request(
                acquired.processingToken(),
                acquired.processingAttempt(),
                "게시글 삽입 실패를 검증합니다."
        );
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_be023_auto_report_post()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'forced automatic post insert failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_be023_auto_report_post_trigger
                BEFORE INSERT ON post
                FOR EACH ROW
                WHEN (NEW.is_auto_report)
                EXECUTE FUNCTION reject_be023_auto_report_post()
                """);

        try {
            assertThatThrownBy(() -> reportCompleteService.complete(
                    acquired.reportId(),
                    request
            )).isInstanceOf(RuntimeException.class);

            String status = jdbcTemplate.queryForObject("""
                    SELECT status
                    FROM report
                    WHERE id = ?
                    """, String.class, acquired.reportId());
            Integer evidenceCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM report_evidence
                    WHERE report_id = ?
                    """, Integer.class, acquired.reportId());
            Integer automaticPostCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM post
                    WHERE report_id = ?
                      AND is_auto_report = TRUE
                    """, Integer.class, acquired.reportId());
            String studyStatus = jdbcTemplate.queryForObject("""
                    SELECT status
                    FROM study
                    WHERE id = ?
                    """, String.class, studyId);

            assertThat(status).isEqualTo("IN_PROGRESS");
            assertThat(evidenceCount).isZero();
            assertThat(automaticPostCount).isZero();
            assertThat(studyStatus).isEqualTo("IN_PROGRESS");
        } finally {
            jdbcTemplate.execute("""
                    DROP TRIGGER IF EXISTS reject_be023_auto_report_post_trigger
                    ON post
                    """);
            jdbcTemplate.execute("""
                    DROP FUNCTION IF EXISTS reject_be023_auto_report_post()
                    """);
        }
    }

    @Test
    void 진행_중이_아닌_스터디는_리포트_완료_전체를_롤백한다() {
        jdbcTemplate.update(
                "UPDATE study SET status = 'CLOSED' WHERE id = ?",
                studyId
        );
        ReportAcquireResponse acquired = reportAcquireService.acquire(
                new ReportAcquireRequest(
                        studyId,
                        sessionId,
                        apartmentId,
                        RECORDED_AT
                )
        );
        ReportCompleteRequest request = request(
                acquired.processingToken(),
                acquired.processingAttempt(),
                "잘못된 스터디 상태의 완료를 검증합니다."
        );

        assertThatThrownBy(() -> reportCompleteService.complete(
                acquired.reportId(),
                request
        )).isInstanceOf(IllegalStateException.class);

        String reportStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM report
                WHERE id = ?
                """, String.class, acquired.reportId());
        String studyStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM study
                WHERE id = ?
                """, String.class, studyId);
        Integer evidenceCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM report_evidence
                WHERE report_id = ?
                """, Integer.class, acquired.reportId());
        Integer automaticPostCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM post
                WHERE report_id = ?
                  AND is_auto_report = TRUE
                """, Integer.class, acquired.reportId());

        assertThat(reportStatus).isEqualTo("IN_PROGRESS");
        assertThat(studyStatus).isEqualTo("CLOSED");
        assertThat(evidenceCount).isZero();
        assertThat(automaticPostCount).isZero();
    }

    private ReportCompleteRequest request(
            String processingToken,
            int processingAttempt,
            String summary
    ) {
        ReportGenerationResultRequest generationResult =
                new ReportGenerationResultRequest(
                        "교통 리포트",
                        summary,
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
                                        RECORDED_AT,
                                        ReportEvidenceResultRequest.EvidenceRole
                                                .SUPPORT
                                )),
                                1
                        )
                ));
        return new ReportCompleteRequest(
                processingToken,
                processingAttempt,
                generationResult,
                evidenceResult
        );
    }

    private void seed() {
        apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE019-COMPLETE', 127.0, 37.5)
                RETURNING id
                """, Long.class, "BE019-" + UUID.randomUUID());
        memberId = insertMember();
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (
                    apartment_id, leader_id, goal, capacity, title, status
                ) VALUES (
                    ?, ?, 'be019-complete-goal', 5,
                    'be019-complete', 'IN_PROGRESS'
                )
                RETURNING id
                """, Long.class, apartmentId, memberId);
        jdbcTemplate.update("""
                INSERT INTO study_member (study_id, member_id, role, status)
                VALUES (?, ?, 'LEADER', 'ACTIVE')
                """, studyId, memberId);
        sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO field_session (
                    study_id, status, started_at, ended_at,
                    ended_by_id, end_reason
                ) VALUES (
                    ?, 'ENDED', ? - INTERVAL '1 hour', ?,
                    ?, 'ALL_COMPLETED'
                )
                RETURNING id
                """, Long.class, studyId, RECORDED_AT, RECORDED_AT, memberId);
        jdbcTemplate.update("""
                INSERT INTO field_participant (
                    session_id, member_id, status, started_at,
                    ended_at, end_reason
                ) VALUES (
                    ?, ?, 'ENDED', ? - INTERVAL '1 hour',
                    ?, 'SELF_FINISH'
                )
                """, sessionId, memberId, RECORDED_AT, RECORDED_AT);
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
                """, checklistItemId, RECORDED_AT);
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
                RECORDED_AT,
                RECORDED_AT);
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
                "be019-complete-" + suffix + "@test.local",
                "be019" + suffix);
    }

    private void cleanup() {
        jdbcTemplate.update("""
                DELETE FROM notification
                WHERE recipient_id IN (
                    SELECT id FROM member
                    WHERE email LIKE 'be019-complete-%@test.local'
                )
                   OR actor_id IN (
                    SELECT id FROM member
                    WHERE email LIKE 'be019-complete-%@test.local'
                )
                """);
        jdbcTemplate.update("DELETE FROM post");
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
                "DELETE FROM study WHERE title = 'be019-complete'"
        );
        jdbcTemplate.update(
                "DELETE FROM apartment WHERE name = 'BE019-COMPLETE'"
        );
        jdbcTemplate.update("""
                DELETE FROM member
                WHERE email LIKE 'be019-complete-%@test.local'
                """);
    }

    private record StoredReport(
            String status,
            String progressStage,
            String resultJson,
            String completePayloadHash,
            int processingAttempt,
            String processingTokenHash,
            Object processingLeaseExpiresAt,
            Object completedAt
    ) {
    }

    private record StoredAutomaticPost(
            Long authorId,
            String boardType,
            String title,
            String content,
            boolean autoReport,
            Long reportId,
            Long apartmentId
    ) {
    }
}
