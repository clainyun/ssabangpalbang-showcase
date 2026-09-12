package com.ssafy.ssabangpalbang.quality;

import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrl;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@Tag("postgres")
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "management.health.redis.enabled=false",
                "ssabangpalbang.fieldvisit.stt.reliability.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class Be033AuthorizationRestAssuredTest {

    private static final String RAW_TEXT = "BE033 권한 없는 사용자에게 숨길 현장 원문";
    private static final String DELETED_RAW_TEXT = "BE033 삭제된 현장 원문";
    private static final String STT_TEXT = "BE033 음성에서 변환된 현장 기록";
    private static final String PHOTO_URL = "https://media.example.test/be033-secret-photo";
    private static final String AUDIO_OBJECT_KEY = "be033/secret-audio.webm";
    private static final String PASSWORD_HASH = "be033-password-secret";
    private static final String FINGERPRINT_SENTINEL = "be033-fingerprint-secret";

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
        registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:9092");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SttDispatchPort sttDispatchPort;

    @MockitoBean
    private SttAudioDeletionPort sttAudioDeletionPort;

    @MockitoBean
    private GatewayMediaAccessUrlProvider mediaAccessUrlProvider;

    private Long studyId;
    private Long leaderId;
    private Long participantId;
    private Long nonParticipantId;
    private Long removedMemberId;
    private Long outsiderId;
    private Long checklistItemId;

    @BeforeEach
    void setUp() {
        cleanup();
        seed();
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        when(mediaAccessUrlProvider.issueAll(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            HashMap<Long, MediaAccessUrl> urls = new HashMap<>();
            ids.forEach(id -> urls.put(
                    id,
                    new MediaAccessUrl(PHOTO_URL, Instant.now().plusSeconds(600))
            ));
            return urls;
        });
    }

    @AfterEach
    void tearDown() {
        cleanup();
        RestAssured.reset();
    }

    @Test
    void 핵심_도메인은_무토큰_요청을_원본_없이_401로_거부한다() {
        List.of(
                "/api/v1/studies/" + studyId,
                recordsPath(),
                "/api/v1/reports/1/status",
                "/api/v1/posts",
                "/api/v1/apartments/1/chatbot/conversations/1/messages"
        ).forEach(path -> assertDeniedWithoutSecrets(
                given().when().get(path),
                401,
                "AUTH_ACCESS_TOKEN_INVALID"
        ));
    }

    @Test
    void 위변조_토큰은_응답과_로그에_토큰과_원본을_남기지_않고_401이다(
            CapturedOutput output
    ) {
        String tamperedToken = "be033-tampered-token-secret";
        Response response = given()
                .header("Authorization", "Bearer " + tamperedToken)
                .when()
                .get(recordsPath());

        assertDeniedWithoutSecrets(response, 401, "AUTH_ACCESS_TOKEN_INVALID");
        assertThat(response.asString()).doesNotContain(tamperedToken);
        assertLogsDoNotContain(output, tamperedToken, RAW_TEXT, PASSWORD_HASH);
    }

    @Test
    void 임장_참여자와_스터디장만_전체_원본을_조회한다() {
        assertAllowedWithOriginal(participantId);
        assertAllowedWithOriginal(leaderId);
    }

    @Test
    void 승인됐지만_임장에_참여하지_않은_멤버는_원본_없이_403이다() {
        assertDeniedWithoutSecrets(
                authorized(nonParticipantId).when().get(recordsPath()),
                403,
                "FIELD_RECORD_ACCESS_DENIED"
        );
    }

    @Test
    void 강퇴된_멤버는_원본_없이_403이다() {
        assertDeniedWithoutSecrets(
                authorized(removedMemberId).when().get(recordsPath()),
                403,
                "FIELD_RECORD_ACCESS_DENIED"
        );
    }

    @Test
    void 스터디_가입_이력이_없는_비멤버는_원본_없이_403이다() {
        assertDeniedWithoutSecrets(
                authorized(outsiderId).when().get(recordsPath()),
                403,
                "FIELD_RECORD_ACCESS_DENIED"
        );
    }

    @Test
    void 오프라인_동일_요청은_기존_ID를_반환하고_다른_payload는_원본을_보존한다(
            CapturedOutput output
    ) {
        String clientRequestId = UUID.randomUUID().toString();
        String originalText = "BE033 오프라인 원본 메모";
        String mismatchedText = "BE033 변조된 재전송 메모";
        String originalRequest = recordCreateBody(clientRequestId, originalText);

        Response created = authorized(participantId)
                .contentType("application/json")
                .body(originalRequest)
                .when()
                .post(recordsPathWithoutQuery());
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.jsonPath().getString("code"))
                .isEqualTo("FIELD_RECORD_CREATE_SUCCESS");
        Long sourceId = created.jsonPath().getLong("data.record.sourceId");

        Response replayed = authorized(participantId)
                .contentType("application/json")
                .body(originalRequest)
                .when()
                .post(recordsPathWithoutQuery());
        assertThat(replayed.statusCode()).isEqualTo(200);
        assertThat(replayed.jsonPath().getString("code"))
                .isEqualTo("FIELD_RECORD_ALREADY_CREATED");
        assertThat(replayed.jsonPath().getLong("data.record.sourceId"))
                .isEqualTo(sourceId);

        Response mismatched = authorized(participantId)
                .contentType("application/json")
                .body(recordCreateBody(clientRequestId, mismatchedText))
                .when()
                .post(recordsPathWithoutQuery());
        assertThat(mismatched.statusCode()).isEqualTo(400);
        assertThat(mismatched.jsonPath().getString("code"))
                .isEqualTo("FIELD_RECORD_IDEMPOTENCY_KEY_REUSED");
        assertThat(mismatched.asString()).doesNotContain(originalText);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_record WHERE client_request_id = ?",
                Long.class,
                clientRequestId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT text_content FROM field_record WHERE client_request_id = ?",
                String.class,
                clientRequestId
        )).isEqualTo(originalText);
        assertLogsDoNotContain(
                output,
                originalText,
                mismatchedText,
                PASSWORD_HASH,
                FINGERPRINT_SENTINEL
        );
    }

    private void assertAllowedWithOriginal(Long memberId) {
        Response response = authorized(memberId)
                .when()
                .get(recordsPath());
        response.then()
                .statusCode(200)
                .body("code", equalTo("FIELD_RECORD_LIST_SUCCESS"))
                .body("data.content.size()", equalTo(3));
        String body = response.asString();
        assertThat(body)
                .contains(RAW_TEXT)
                .contains(STT_TEXT)
                .contains(PHOTO_URL)
                .doesNotContain(DELETED_RAW_TEXT)
                .doesNotContain(AUDIO_OBJECT_KEY)
                .doesNotContain(PASSWORD_HASH)
                .doesNotContain("passwordHash")
                .doesNotContain("password_hash");
    }

    private void assertDeniedWithoutSecrets(
            Response response,
            int expectedStatus,
            String expectedCode
    ) {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        assertThat(response.jsonPath().getString("code")).isEqualTo(expectedCode);
        assertThat(response.asString())
                .doesNotContain(RAW_TEXT)
                .doesNotContain(DELETED_RAW_TEXT)
                .doesNotContain(STT_TEXT)
                .doesNotContain(PHOTO_URL)
                .doesNotContain(AUDIO_OBJECT_KEY)
                .doesNotContain(PASSWORD_HASH)
                .doesNotContain("passwordHash")
                .doesNotContain("password_hash");
    }

    private void assertLogsDoNotContain(
            CapturedOutput output,
            String... secrets
    ) {
        assertThat(output.getAll()).doesNotContain(secrets);
    }

    private io.restassured.specification.RequestSpecification authorized(Long memberId) {
        return given().header(
                "Authorization",
                "Bearer " + jwtTokenProvider.issue(memberId).accessToken()
        );
    }

    private String recordsPath() {
        return recordsPathWithoutQuery() + "?mineOnly=false&size=20";
    }

    private String recordsPathWithoutQuery() {
        return "/api/v1/studies/" + studyId + "/field-visit/records";
    }

    private String recordCreateBody(String clientRequestId, String textContent) {
        return """
                {
                  "checklistItemId": %d,
                  "sourceType": "TEXT",
                  "textContent": "%s",
                  "clientRequestId": "%s"
                }
                """.formatted(checklistItemId, textContent, clientRequestId);
    }

    private void seed() {
        leaderId = insertMember("be033-leader");
        participantId = insertMember("be033-participant");
        nonParticipantId = insertMember("be033-non-participant");
        removedMemberId = insertMember("be033-removed");
        outsiderId = insertMember("be033-outsider");
        Long apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE033-AUTH', 127.0, 37.5) RETURNING id
                """, Long.class, "BE033-AUTH-" + UUID.randomUUID());
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (apartment_id, leader_id, goal, capacity, title, status)
                VALUES (?, ?, 'BE033 권한 검증', 5, 'be033-auth', 'IN_PROGRESS')
                RETURNING id
                """, Long.class, apartmentId, leaderId);
        jdbcTemplate.update("""
                INSERT INTO study_member (study_id, member_id, role, status)
                VALUES (?, ?, 'LEADER', 'ACTIVE'),
                       (?, ?, 'MEMBER', 'ACTIVE'),
                       (?, ?, 'MEMBER', 'ACTIVE'),
                       (?, ?, 'MEMBER', 'REMOVED')
                """,
                studyId, leaderId,
                studyId, participantId,
                studyId, nonParticipantId,
                studyId, removedMemberId);
        Long sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO field_session (study_id, status)
                VALUES (?, 'IN_PROGRESS') RETURNING id
                """, Long.class, studyId);
        jdbcTemplate.update("""
                INSERT INTO field_participant (session_id, member_id, status)
                VALUES (?, ?, 'IN_PROGRESS')
                """, sessionId, participantId);
        Long checklistId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist (session_id, member_id)
                VALUES (?, ?) RETURNING id
                """, Long.class, sessionId, participantId);
        checklistItemId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist_item (checklist_id, category, title, display_order)
                VALUES (?, 'BE033', '원본 권한', 1) RETURNING id
                """, Long.class, checklistId);
        Long photoFileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_meta (
                    owner_id, study_id, file_usage, original_name, s3_key,
                    content_type, size_bytes, upload_status
                ) VALUES (?, ?, 'FIELD_PHOTO', 'secret.jpg', ?, 'image/jpeg', 10, 'COMPLETED')
                RETURNING id
                """, Long.class, participantId, studyId, "be033/" + UUID.randomUUID());
        Long audioFileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_meta (
                    owner_id, study_id, file_usage, original_name, s3_key,
                    content_type, size_bytes, upload_status
                ) VALUES (?, ?, 'STT_AUDIO', 'secret-audio.webm', ?,
                          'audio/webm', 100, 'COMPLETED')
                RETURNING id
                """, Long.class, participantId, studyId, AUDIO_OBJECT_KEY);
        jdbcTemplate.update("""
                INSERT INTO field_record (
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, client_request_id, request_fingerprint
                ) VALUES (?, ?, ?, 'TEXT', ?, ?, ?)
                """, sessionId, checklistItemId, participantId, RAW_TEXT,
                UUID.randomUUID().toString(), FINGERPRINT_SENTINEL);
        jdbcTemplate.update("""
                INSERT INTO field_record (
                    session_id, checklist_item_id, author_id, source_type,
                    photo_file_id, client_request_id, request_fingerprint
                ) VALUES (?, ?, ?, 'PHOTO', ?, ?, 'be033-photo')
                """, sessionId, checklistItemId, participantId,
                photoFileId, UUID.randomUUID().toString());
        jdbcTemplate.update("""
                INSERT INTO field_record (
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, client_request_id, request_fingerprint,
                    deleted_at
                ) VALUES (?, ?, ?, 'TEXT', ?, ?, 'be033-deleted', now())
                """, sessionId, checklistItemId, participantId,
                DELETED_RAW_TEXT, UUID.randomUUID().toString());
        Long sttRecordId = jdbcTemplate.queryForObject("""
                INSERT INTO field_record (
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, stt_status, client_request_id
                ) VALUES (?, ?, ?, 'STT', ?, 'DONE', ?)
                RETURNING id
                """, Long.class, sessionId, checklistItemId, participantId,
                STT_TEXT, "STT:be033-auth-stt");
        jdbcTemplate.update("""
                INSERT INTO stt_job (
                    stt_id, member_id, study_id, session_id, audio_file_id,
                    checklist_item_id, field_record_id,
                    initial_client_request_id, status,
                    requested_at, completed_at, version
                ) VALUES (
                    'be033-auth-stt', ?, ?, ?, ?, ?, ?, ?, 'DONE',
                    now(), now(), 0
                )
                """, participantId, studyId, sessionId, audioFileId,
                checklistItemId, sttRecordId, UUID.randomUUID());
    }

    private Long insertMember(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (
                    email, password_hash, nickname, age_group_public_agreed,
                    service_notification_agreed, ad_notification_agreed
                ) VALUES (?, 'be033-password-secret', ?, false, true, false)
                RETURNING id
                """, Long.class, prefix + "-" + suffix + "@test.local", prefix + suffix);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM stt_job_attempt WHERE stt_job_id IN "
                + "(SELECT id FROM stt_job WHERE stt_id LIKE 'be033-auth-%')");
        jdbcTemplate.update("DELETE FROM stt_job WHERE stt_id LIKE 'be033-auth-%'");
        jdbcTemplate.update("DELETE FROM field_record WHERE session_id IN "
                + "(SELECT id FROM field_session WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be033-auth'))");
        jdbcTemplate.update("DELETE FROM checklist_item WHERE category = 'BE033'");
        jdbcTemplate.update("DELETE FROM checklist WHERE session_id IN "
                + "(SELECT id FROM field_session WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be033-auth'))");
        jdbcTemplate.update("DELETE FROM field_participant WHERE session_id IN "
                + "(SELECT id FROM field_session WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be033-auth'))");
        jdbcTemplate.update("DELETE FROM field_session WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be033-auth')");
        jdbcTemplate.update("DELETE FROM file_meta WHERE s3_key LIKE 'be033/%'");
        jdbcTemplate.update("DELETE FROM study_member WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be033-auth')");
        jdbcTemplate.update("DELETE FROM study WHERE title = 'be033-auth'");
        jdbcTemplate.update("DELETE FROM apartment WHERE name = 'BE033-AUTH'");
        jdbcTemplate.update("DELETE FROM member WHERE email LIKE 'be033-%@test.local'");
    }
}
