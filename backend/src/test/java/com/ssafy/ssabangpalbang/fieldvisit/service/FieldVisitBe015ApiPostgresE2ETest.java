package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.dto.request.ChecklistAnswerSaveRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldRecordCreateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldRecordUpdateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistDetailResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrl;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * BE-015 공개 API 5개를 실제 PostgreSQL(Testcontainers) 위에서 서비스 계층으로 검증한다.
 */
@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
class FieldVisitBe015ApiPostgresE2ETest {

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

    @MockitoBean
    private GatewayMediaAccessUrlProvider mediaAccessUrlProvider;

    @MockitoBean
    private SttDispatchPort sttDispatchPort;

    @MockitoBean
    private SttAudioDeletionPort sttAudioDeletionPort;

    @Autowired
    private ChecklistAnswerService checklistAnswerService;

    @Autowired
    private FieldRecordService fieldRecordService;

    @Autowired
    private ChecklistService checklistService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long studyId;
    private Long memberId;
    private Long otherMemberId;
    private Long checklistItemId;
    private Long otherItemId;
    private Long photoFileId;

    @BeforeEach
    void setUp() {
        when(mediaAccessUrlProvider.issueAll(anyCollection())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var ids = (java.util.Collection<Long>) inv.getArgument(0);
            Map<Long, MediaAccessUrl> map = new java.util.HashMap<>();
            for (Long id : ids) {
                map.put(id, new MediaAccessUrl(
                        "https://example.test/photo/" + id,
                        Instant.now().plusSeconds(600)
                ));
            }
            return map;
        });

        cleanup();
        seed();
    }

    @Test
    void 다섯_개_공개_API와_AI002_집계를_검증한다() {
        // PUT answers
        var answerResult = checklistAnswerService.saveAnswers(
                studyId,
                memberId,
                new ChecklistAnswerSaveRequest(List.of(
                        new ChecklistAnswerSaveRequest.AnswerItem(checklistItemId, true)
                ))
        );
        assertThat(answerResult.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(answerResult.body().completedCount()).isEqualTo(1);

        ChecklistDetailResponse checklist = checklistService.getChecklist(studyId, memberId);
        assertThat(checklist.checklist().completedCount()).isEqualTo(1);

        // POST TEXT
        String clientRequestId = UUID.randomUUID().toString();
        var created = fieldRecordService.create(
                studyId,
                memberId,
                new FieldRecordCreateRequest(
                        checklistItemId,
                        "TEXT",
                        "E2E 메모",
                        null,
                        clientRequestId
                )
        );
        assertThat(created.httpStatus()).isEqualTo(HttpStatus.CREATED);
        Long sourceId = created.body().record().sourceId();
        assertThat(sourceId).isNotNull();
        assertThat(created.body().record().canEdit()).isTrue();
        assertThat(created.body().record().canDelete()).isTrue();
        assertThat(created.body().record().isMine()).isTrue();
        assertThat(created.body().record().author()).isNotNull();
        assertThat(created.body().itemRecordCount()).isGreaterThanOrEqualTo(1);

        // 멱등 재요청
        var replay = fieldRecordService.create(
                studyId,
                memberId,
                new FieldRecordCreateRequest(
                        checklistItemId,
                        "TEXT",
                        "E2E 메모",
                        null,
                        clientRequestId
                )
        );
        assertThat(replay.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(replay.body().record().sourceId()).isEqualTo(sourceId);

        // POST PHOTO
        var photo = fieldRecordService.create(
                studyId,
                memberId,
                new FieldRecordCreateRequest(
                        checklistItemId,
                        "PHOTO",
                        null,
                        photoFileId,
                        UUID.randomUUID().toString()
                )
        );
        assertThat(photo.httpStatus()).isEqualTo(HttpStatus.CREATED);

        // STT fixture row
        jdbcTemplate.update("""
                INSERT INTO field_record(
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, stt_status, client_request_id
                )
                SELECT fs.id, ?, ?, 'STT', 'stt text', 'DONE', ?
                FROM field_session fs WHERE fs.study_id = ?
                """, checklistItemId, memberId, "STT:" + UUID.randomUUID(), studyId);

        checklist = checklistService.getChecklist(studyId, memberId);
        var item = checklist.checklist().categories().get(0).items().stream()
                .filter(i -> i.checklistItemId().equals(checklistItemId))
                .findFirst()
                .orElseThrow();
        assertThat(item.recordCount()).isEqualTo(3);
        assertThat(item.recordSummary().textCount()).isEqualTo(1);
        assertThat(item.recordSummary().photoCount()).isEqualTo(1);
        assertThat(item.recordSummary().sttCount()).isEqualTo(1);

        // GET mineOnly=false
        var listAll = fieldRecordService.list(
                studyId, memberId, null, null, false, null, 20
        );
        assertThat(listAll.content().size()).isGreaterThanOrEqualTo(3);

        // 타인 기록 seed
        Long otherRecordId = jdbcTemplate.queryForObject("""
                INSERT INTO field_record(
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, client_request_id, request_fingerprint
                )
                SELECT fs.id, ci.id, ?, 'TEXT', 'other', ?, 'fp-other'
                FROM field_session fs
                JOIN checklist c ON c.session_id = fs.id AND c.member_id = ?
                JOIN checklist_item ci ON ci.checklist_id = c.id
                WHERE fs.study_id = ?
                LIMIT 1
                RETURNING id
                """, Long.class, otherMemberId, UUID.randomUUID().toString(),
                otherMemberId, studyId);

        var listShared = fieldRecordService.list(
                studyId, memberId, null, null, false, null, 50
        );
        var other = listShared.content().stream()
                .filter(r -> r.sourceId().equals(otherRecordId))
                .findFirst()
                .orElseThrow();
        assertThat(other.isMine()).isFalse();
        assertThat(other.canEdit()).isFalse();
        assertThat(other.canDelete()).isFalse();

        // PATCH TEXT + item move
        var patched = fieldRecordService.update(
                studyId,
                memberId,
                sourceId,
                new FieldRecordUpdateRequest(otherItemId, "교정된 메모", null)
        );
        assertThat(patched.checklistItemId()).isEqualTo(otherItemId);
        assertThat(patched.textContent()).isEqualTo("교정된 메모");
        assertThat(patched.sourceId()).isEqualTo(sourceId);

        checklist = checklistService.getChecklist(studyId, memberId);
        Map<Long, Integer> counts = new java.util.HashMap<>();
        checklist.checklist().categories().forEach(cat ->
                cat.items().forEach(i -> counts.put(i.checklistItemId(), i.recordCount())));
        assertThat(counts.get(checklistItemId)).isEqualTo(2); // photo + stt
        assertThat(counts.get(otherItemId)).isEqualTo(1); // moved text

        // DELETE
        var deleted = fieldRecordService.delete(studyId, memberId, sourceId);
        assertThat(deleted.httpStatus()).isEqualTo(HttpStatus.OK);
        var listAfterDelete = fieldRecordService.list(
                studyId, memberId, null, "TEXT", true, null, 20
        );
        assertThat(listAfterDelete.content().stream()
                .noneMatch(r -> r.sourceId().equals(sourceId))).isTrue();

        // report lock
        jdbcTemplate.update("""
                INSERT INTO report (
                    study_id, apartment_id, field_session_id, status
                )
                SELECT study.id, study.apartment_id, session.id, 'PENDING'
                FROM study
                JOIN field_session session ON session.study_id = study.id
                WHERE study.id = ?
                """, studyId);
        assertThatThrownBy(() -> checklistAnswerService.saveAnswers(
                studyId,
                memberId,
                new ChecklistAnswerSaveRequest(List.of(
                        new ChecklistAnswerSaveRequest.AnswerItem(checklistItemId, false)
                ))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_REPORT_LOCKED);
    }

    private void seed() {
        Long apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE015-E2E', 127.0, 37.5) RETURNING id
                """, Long.class, "BE015-" + UUID.randomUUID());
        memberId = insertMember("be015-e2e");
        otherMemberId = insertMember("be015-other");
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (apartment_id, leader_id, goal, capacity, title, status)
                VALUES (?, ?, 'be015-e2e-goal', 5, 'be015-e2e', 'RECRUITING') RETURNING id
                """, Long.class, apartmentId, memberId);
        jdbcTemplate.update("""
                INSERT INTO study_member (study_id, member_id, status)
                VALUES (?, ?, 'ACTIVE'), (?, ?, 'ACTIVE')
                """, studyId, memberId, studyId, otherMemberId);
        Long sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO field_session (study_id, status) VALUES (?, 'IN_PROGRESS') RETURNING id
                """, Long.class, studyId);
        jdbcTemplate.update("""
                INSERT INTO field_participant (session_id, member_id, status)
                VALUES (?, ?, 'IN_PROGRESS'), (?, ?, 'IN_PROGRESS')
                """, sessionId, memberId, sessionId, otherMemberId);
        Long checklistId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist (session_id, member_id, is_fallback)
                VALUES (?, ?, false) RETURNING id
                """, Long.class, sessionId, memberId);
        Long otherChecklistId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist (session_id, member_id, is_fallback)
                VALUES (?, ?, false) RETURNING id
                """, Long.class, sessionId, otherMemberId);
        checklistItemId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist_item (checklist_id, category, title, display_order)
                VALUES (?, '교통', '역', 1) RETURNING id
                """, Long.class, checklistId);
        otherItemId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist_item (checklist_id, category, title, display_order)
                VALUES (?, '교통', '버스', 2) RETURNING id
                """, Long.class, checklistId);
        jdbcTemplate.update("""
                INSERT INTO checklist_item (checklist_id, category, title, display_order)
                VALUES (?, '교통', '타인의역', 1)
                """, otherChecklistId);
        photoFileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_meta (
                    owner_id, study_id, file_usage, original_name, s3_key,
                    content_type, size_bytes, upload_status
                ) VALUES (?, ?, 'FIELD_PHOTO', 'p.jpg', ?, 'image/jpeg', 10, 'COMPLETED')
                RETURNING id
                """, Long.class, memberId, studyId, "field/" + UUID.randomUUID() + ".jpg");
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
        jdbcTemplate.update("DELETE FROM study WHERE title = 'be015-e2e'");
        jdbcTemplate.update("DELETE FROM apartment WHERE name = 'BE015-E2E'");
        jdbcTemplate.update("DELETE FROM member WHERE email LIKE 'be015-%@test.local'");
    }
}
