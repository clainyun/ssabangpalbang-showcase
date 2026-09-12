package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.report.dto.response.ReportInputResponse;
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
class ReportInputPostgresTest {

    private static final String PROCESSING_TOKEN_HASH =
            "0123456789abcdef0123456789abcdef"
                    + "0123456789abcdef0123456789abcdef";
    private static final String PHOTO_OBJECT_KEY =
            "private/be019/report-photo-secret.jpg";
    private static final String AUDIO_A_OBJECT_KEY =
            "private/be019/report-audio-a-secret.m4a";
    private static final String AUDIO_Z_OBJECT_KEY =
            "private/be019/report-audio-z-secret.m4a";

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
    private ReportInputService reportInputService;

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
    private Long leaderId;
    private Long participantId;
    private Long studyId;
    private Long sessionId;
    private Long reportId;
    private Long participantChecklistItemId;
    private Long leaderChecklistItemOneId;
    private Long leaderChecklistItemTwoId;
    private List<Long> fieldRecordIds;

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
    void V16_권위_세션에서_AI004용_전체_raw_스냅샷을_안전하게_조회한다()
            throws JsonProcessingException {
        Integer v16Applied = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '16'
                  AND success = TRUE
                """, Integer.class);
        String fieldSessionNullable = jdbcTemplate.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'report'
                  AND column_name = 'field_session_id'
                """, String.class);

        ReportInputResponse response = reportInputService.getInput(reportId);

        assertThat(v16Applied).isEqualTo(1);
        assertThat(fieldSessionNullable).isEqualTo("NO");
        assertThat(response.schemaVersion()).isEqualTo(1);
        assertThat(response.reportId()).isEqualTo(reportId);
        assertThat(response.studyId()).isEqualTo(studyId);
        assertThat(response.apartmentId()).isEqualTo(apartmentId);
        assertThat(response.fieldSessionId()).isEqualTo(sessionId);
        assertThat(response.sessionStatus().name()).isEqualTo("ENDED");
        assertThat(response.sessionStartedAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
        assertThat(response.sessionEndedAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
        assertThat(response.snapshotAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
        assertThat(response.snapshotAt())
                .isAfterOrEqualTo(response.sessionEndedAt());

        assertThat(response.participants())
                .extracting(ReportInputResponse.Participant::memberId)
                .containsExactly(participantId, leaderId);
        assertThat(response.participants())
                .allSatisfy(participant -> {
                    assertThat(participant.status().name()).isEqualTo("ENDED");
                    assertThat(participant.endedAt()).isNotNull();
                });

        assertThat(response.checklistItems())
                .extracting(ReportInputResponse.ChecklistItem::checklistItemId)
                .containsExactly(
                        participantChecklistItemId,
                        leaderChecklistItemOneId,
                        leaderChecklistItemTwoId
                );
        ReportInputResponse.ChecklistItem completedItem = response
                .checklistItems()
                .stream()
                .filter(item -> item.checklistItemId()
                        .equals(leaderChecklistItemOneId))
                .findFirst()
                .orElseThrow();
        ReportInputResponse.ChecklistItem unansweredItem = response
                .checklistItems()
                .stream()
                .filter(item -> item.checklistItemId()
                        .equals(leaderChecklistItemTwoId))
                .findFirst()
                .orElseThrow();
        assertThat(completedItem.completed()).isTrue();
        assertThat(completedItem.completedAt()).isNotNull();
        assertThat(unansweredItem.completed()).isFalse();
        assertThat(unansweredItem.completedAt()).isNull();

        assertThat(response.fieldRecords())
                .extracting(ReportInputResponse.FieldRecord::sourceId)
                .containsExactlyElementsOf(fieldRecordIds);
        assertThat(response.authoritativeSourceIds())
                .containsExactlyElementsOf(fieldRecordIds);

        ReportInputResponse.FieldRecord deletedRecord = response
                .fieldRecords()
                .stream()
                .filter(record -> record.sourceId()
                        .equals(fieldRecordIds.get(3)))
                .findFirst()
                .orElseThrow();
        assertThat(deletedRecord.deletedAt()).isNotNull();
        assertThat(deletedRecord.textContent()).isEqualTo("삭제된 원문도 포함");

        ReportInputResponse.FieldRecord validPhoto = response
                .fieldRecords()
                .stream()
                .filter(record -> record.sourceId()
                        .equals(fieldRecordIds.get(1)))
                .findFirst()
                .orElseThrow();
        assertThat(validPhoto.photoFile()).isNotNull();
        assertThat(validPhoto.photoFile().contentType())
                .isEqualTo("image/jpeg");
        assertThat(validPhoto.photoFile().sizeBytes()).isEqualTo(1024L);
        assertThat(validPhoto.photoFile().uploadStatus().name())
                .isEqualTo("COMPLETED");

        ReportInputResponse.FieldRecord invalidOwnerPhoto = response
                .fieldRecords()
                .stream()
                .filter(record -> record.sourceId()
                        .equals(fieldRecordIds.get(4)))
                .findFirst()
                .orElseThrow();
        assertThat(invalidOwnerPhoto.photoFile()).isNull();

        ReportInputResponse.FieldRecord doneStt = response
                .fieldRecords()
                .stream()
                .filter(record -> record.sourceId()
                        .equals(fieldRecordIds.get(2)))
                .findFirst()
                .orElseThrow();
        assertThat(doneStt.sttStatus().name()).isEqualTo("DONE");
        assertThat(doneStt.textContent()).isEqualTo("완료된 STT 원문");

        assertThat(response.incompleteSttJobs())
                .extracting(ReportInputResponse.IncompleteSttJob::sttId)
                .containsExactly("stt-a", "stt-z");
        assertThat(response.incompleteSttJobs())
                .extracting(job -> job.status().name())
                .containsExactly("FAILED", "PENDING");

        String serialized = objectMapper.writeValueAsString(response);
        assertThat(serialized).doesNotContain(
                PHOTO_OBJECT_KEY,
                AUDIO_A_OBJECT_KEY,
                AUDIO_Z_OBJECT_KEY,
                PROCESSING_TOKEN_HASH,
                "s3Key",
                "s3_key",
                "objectKey",
                "downloadUrl",
                "presignedUrl",
                "processingToken",
                "processingTokenHash"
        );
    }

    @Test
    void report의_fieldSessionId가_다른_study를_가리키면_원본_존재를_숨긴다() {
        Long otherStudyId = insertStudy("be019-input-other");
        Long otherSessionId = insertEndedSession(otherStudyId);
        jdbcTemplate.update(
                "UPDATE report SET field_session_id = ? WHERE id = ?",
                otherSessionId,
                reportId
        );

        assertThatThrownBy(() -> reportInputService.getInput(reportId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REPORT_NOT_FOUND)
                );
    }

    private void seed() {
        apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (
                    complex_code, name, longitude, latitude
                ) VALUES (?, 'BE019-INPUT', 127.0, 37.5)
                RETURNING id
                """, Long.class, "BE019-INPUT-" + UUID.randomUUID());
        leaderId = insertMember("leader");
        participantId = insertMember("participant");
        studyId = insertStudy("be019-input");
        sessionId = insertEndedSession(studyId);

        insertParticipant(
                leaderId,
                "now() - INTERVAL '1 hour 50 minutes'",
                "now() - INTERVAL '1 hour 10 minutes'"
        );
        insertParticipant(
                participantId,
                "now() - INTERVAL '1 hour 55 minutes'",
                "now() - INTERVAL '1 hour 5 minutes'"
        );

        Long leaderChecklistId = insertChecklist(leaderId, false);
        Long participantChecklistId = insertChecklist(participantId, true);
        leaderChecklistItemTwoId = insertChecklistItem(
                leaderChecklistId,
                "NOISE",
                "소음 확인",
                2
        );
        leaderChecklistItemOneId = insertChecklistItem(
                leaderChecklistId,
                "TRANSPORT",
                "교통 확인",
                1
        );
        participantChecklistItemId = insertChecklistItem(
                participantChecklistId,
                "PARKING",
                "주차 확인",
                1
        );
        jdbcTemplate.update("""
                INSERT INTO checklist_answer (
                    checklist_item_id, is_completed, completed_at, updated_at
                ) VALUES (
                    ?, TRUE,
                    now() - INTERVAL '1 hour 20 minutes',
                    now() - INTERVAL '1 hour 20 minutes'
                )
                """, leaderChecklistItemOneId);

        Long photoFileId = insertFile(
                leaderId,
                "FIELD_PHOTO",
                PHOTO_OBJECT_KEY,
                "image/jpeg",
                1024L
        );
        Long invalidOwnerPhotoFileId = insertFile(
                participantId,
                "FIELD_PHOTO",
                "private/be019/invalid-owner-photo-secret.jpg",
                "image/png",
                2048L
        );
        Long audioZFileId = insertFile(
                participantId,
                "STT_AUDIO",
                AUDIO_Z_OBJECT_KEY,
                "audio/mp4",
                4096L
        );
        Long audioAFileId = insertFile(
                participantId,
                "STT_AUDIO",
                AUDIO_A_OBJECT_KEY,
                "audio/mp4",
                4096L
        );

        Long textRecordId = insertFieldRecord(
                leaderChecklistItemOneId,
                leaderId,
                "TEXT",
                "현장 텍스트 원문",
                null,
                null,
                false
        );
        Long photoRecordId = insertFieldRecord(
                leaderChecklistItemOneId,
                leaderId,
                "PHOTO",
                null,
                photoFileId,
                null,
                false
        );
        Long sttRecordId = insertFieldRecord(
                leaderChecklistItemTwoId,
                leaderId,
                "STT",
                "완료된 STT 원문",
                null,
                "DONE",
                false
        );
        Long deletedRecordId = insertFieldRecord(
                participantChecklistItemId,
                participantId,
                "TEXT",
                "삭제된 원문도 포함",
                null,
                null,
                true
        );
        Long invalidOwnerPhotoRecordId = insertFieldRecord(
                leaderChecklistItemOneId,
                leaderId,
                "PHOTO",
                null,
                invalidOwnerPhotoFileId,
                null,
                false
        );
        fieldRecordIds = List.of(
                textRecordId,
                photoRecordId,
                sttRecordId,
                deletedRecordId,
                invalidOwnerPhotoRecordId
        );

        insertSttJob(
                "stt-z",
                participantChecklistItemId,
                audioZFileId,
                "PENDING",
                false,
                null
        );
        insertSttJob(
                "stt-a",
                participantChecklistItemId,
                audioAFileId,
                "FAILED",
                true,
                "STT_TIMEOUT"
        );

        reportId = jdbcTemplate.queryForObject("""
                INSERT INTO report (
                    study_id, apartment_id, field_session_id,
                    status, progress_stage, processing_token_hash,
                    processing_attempt, processing_lease_expires_at
                ) VALUES (
                    ?, ?, ?, 'IN_PROGRESS', 'RECORD_COLLECTION', ?, 1,
                    now() + INTERVAL '30 minutes'
                )
                RETURNING id
                """, Long.class,
                studyId,
                apartmentId,
                sessionId,
                PROCESSING_TOKEN_HASH);
    }

    private Long insertMember(String role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (
                    email, nickname, age_group_public_agreed,
                    service_notification_agreed, ad_notification_agreed
                ) VALUES (?, ?, FALSE, TRUE, FALSE)
                RETURNING id
                """, Long.class,
                "be019-input-" + role + "-" + suffix + "@test.local",
                "be019-" + role + "-" + suffix);
    }

    private Long insertStudy(String title) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO study (
                    apartment_id, leader_id, goal, capacity, title, status
                ) VALUES (?, ?, 'be019-input-goal', 5, ?, 'COMPLETED')
                RETURNING id
                """, Long.class, apartmentId, leaderId, title);
    }

    private Long insertEndedSession(Long targetStudyId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO field_session (
                    study_id, status, started_at, ended_at,
                    ended_by_id, end_reason
                ) VALUES (
                    ?, 'ENDED',
                    now() - INTERVAL '2 hours',
                    now() - INTERVAL '1 hour',
                    ?, 'ALL_COMPLETED'
                )
                RETURNING id
                """, Long.class, targetStudyId, leaderId);
    }

    private void insertParticipant(
            Long memberId,
            String startedAtExpression,
            String endedAtExpression
    ) {
        jdbcTemplate.update("""
                INSERT INTO field_participant (
                    session_id, member_id, status, started_at, ended_at,
                    end_reason
                ) VALUES (?, ?, 'ENDED', %s, %s, 'COMPLETED')
                """.formatted(startedAtExpression, endedAtExpression),
                sessionId,
                memberId);
    }

    private Long insertChecklist(Long memberId, boolean fallback) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO checklist (
                    session_id, member_id, is_fallback
                ) VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, sessionId, memberId, fallback);
    }

    private Long insertChecklistItem(
            Long checklistId,
            String category,
            String title,
            int displayOrder
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO checklist_item (
                    checklist_id, category, title, subtitle, display_order
                ) VALUES (?, ?, ?, '테스트 부제', ?)
                RETURNING id
                """, Long.class,
                checklistId,
                category,
                title,
                displayOrder);
    }

    private Long insertFile(
            Long ownerId,
            String fileUsage,
            String objectKey,
            String contentType,
            long sizeBytes
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO file_meta (
                    owner_id, study_id, file_usage, original_name, s3_key,
                    content_type, size_bytes, upload_status, expires_at
                ) VALUES (
                    ?, ?, ?, 'report-input-test-file', ?, ?, ?, 'COMPLETED',
                    now() + INTERVAL '1 day'
                )
                RETURNING id
                """, Long.class,
                ownerId,
                studyId,
                fileUsage,
                objectKey,
                contentType,
                sizeBytes);
    }

    private Long insertFieldRecord(
            Long checklistItemId,
            Long authorId,
            String sourceType,
            String textContent,
            Long photoFileId,
            String sttStatus,
            boolean deleted
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO field_record (
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, photo_file_id, stt_status,
                    client_request_id, deleted_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?,
                    CASE WHEN ? THEN now() - INTERVAL '1 hour 15 minutes'
                         ELSE NULL END,
                    now() - INTERVAL '1 hour 30 minutes',
                    now() - INTERVAL '1 hour 25 minutes'
                )
                RETURNING id
                """, Long.class,
                sessionId,
                checklistItemId,
                authorId,
                sourceType,
                textContent,
                photoFileId,
                sttStatus,
                UUID.randomUUID().toString(),
                deleted);
    }

    private void insertSttJob(
            String sttId,
            Long checklistItemId,
            Long audioFileId,
            String status,
            boolean retryable,
            String failCode
    ) {
        jdbcTemplate.update("""
                INSERT INTO stt_job (
                    stt_id, member_id, study_id, session_id, audio_file_id,
                    checklist_item_id, field_record_id,
                    initial_client_request_id, status, fail_code, retryable,
                    requested_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?,
                    now() - INTERVAL '1 hour 40 minutes'
                )
                """,
                sttId,
                participantId,
                studyId,
                sessionId,
                audioFileId,
                checklistItemId,
                UUID.randomUUID(),
                status,
                failCode,
                retryable);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM report_evidence");
        jdbcTemplate.update("DELETE FROM report_favorite");
        jdbcTemplate.update("DELETE FROM report");
        jdbcTemplate.update("DELETE FROM stt_dispatch_outbox");
        jdbcTemplate.update("DELETE FROM stt_audio_cleanup_job");
        jdbcTemplate.update("DELETE FROM stt_job_attempt");
        jdbcTemplate.update("DELETE FROM stt_job");
        jdbcTemplate.update("DELETE FROM field_record");
        jdbcTemplate.update("DELETE FROM checklist_answer");
        jdbcTemplate.update("DELETE FROM checklist_item");
        jdbcTemplate.update("DELETE FROM checklist");
        jdbcTemplate.update("DELETE FROM field_participant");
        jdbcTemplate.update("DELETE FROM field_session");
        jdbcTemplate.update("DELETE FROM file_meta");
        jdbcTemplate.update("DELETE FROM study_member");
        jdbcTemplate.update("DELETE FROM study");
        jdbcTemplate.update("DELETE FROM apartment");
        jdbcTemplate.update("""
                DELETE FROM member
                WHERE email LIKE 'be019-input-%@test.local'
                """);
    }
}
