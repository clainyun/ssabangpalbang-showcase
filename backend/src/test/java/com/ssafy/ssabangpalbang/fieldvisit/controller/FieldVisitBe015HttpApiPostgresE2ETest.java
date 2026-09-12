package com.ssafy.ssabangpalbang.fieldvisit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * BE-015 공개 API 5개를 실제 HTTP(Controller→Service→Repository→PostgreSQL)로 검증한다.
 * Swagger UI 브라우저 자동 조작 대신 /swagger-ui와 /v3/api-docs HTTP 200 및 동일 서버 API 호출로 대체한다.
 */
@Tag("postgres")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "management.health.redis.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class FieldVisitBe015HttpApiPostgresE2ETest {

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

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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
    private Long memberId;
    private Long otherMemberId;
    private Long checklistItemId;
    private Long otherItemId;
    private Long photoFileId;
    private String accessToken;
    private String otherAccessToken;

    @BeforeEach
    void setUp() {
        cleanup();
        seed();
        when(mediaAccessUrlProvider.issueAll(anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            java.util.Map<Long, MediaAccessUrl> map = new java.util.HashMap<>();
            for (Long id : ids) {
                map.put(id, new MediaAccessUrl(
                        "https://example.test/" + id,
                        Instant.now().plusSeconds(600)
                ));
            }
            return map;
        });
        accessToken = jwtTokenProvider.issue(memberId).accessToken();
        otherAccessToken = jwtTokenProvider.issue(otherMemberId).accessToken();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void swagger_ui와_api_docs_및_BE015_HTTP_API를_검증한다() throws Exception {
        assertThat(restTemplate.getForEntity(url("/swagger-ui/index.html"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> docs = restTemplate.getForEntity(url("/v3/api-docs"), String.class);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody())
                .contains("/api/v1/studies/{studyId}/field-visit/checklist/answers")
                .contains("/api/v1/studies/{studyId}/field-visit/records")
                .contains("bearerAuth");

        // JWT 없음 → 401
        ResponseEntity<String> unauthorized = restTemplate.exchange(
                url("/api/v1/studies/" + studyId + "/field-visit/records"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(null)),
                String.class
        );
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 잘못된 JWT → 401
        ResponseEntity<String> badJwt = restTemplate.exchange(
                url("/api/v1/studies/" + studyId + "/field-visit/records"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders("not-a-jwt")),
                String.class
        );
        assertThat(badJwt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // PUT answers
        ResponseEntity<String> answers = exchange(
                HttpMethod.PUT,
                "/api/v1/studies/" + studyId + "/field-visit/checklist/answers",
                accessToken,
                """
                        {"answers":[{"checklistItemId":%d,"isCompleted":true}]}
                        """.formatted(checklistItemId)
        );
        assertThat(answers.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode answersBody = objectMapper.readTree(answers.getBody());
        assertThat(answersBody.path("code").asText()).isEqualTo("CHECKLIST_COMPLETION_SAVE_SUCCESS");
        assertThat(answersBody.path("data").path("completedCount").asInt()).isEqualTo(1);

        // POST TEXT → 201
        String clientRequestId = UUID.randomUUID().toString();
        ResponseEntity<String> created = exchange(
                HttpMethod.POST,
                "/api/v1/studies/" + studyId + "/field-visit/records",
                accessToken,
                """
                        {
                          "checklistItemId":%d,
                          "sourceType":"TEXT",
                          "textContent":"HTTP E2E 메모",
                          "clientRequestId":"%s"
                        }
                        """.formatted(checklistItemId, clientRequestId)
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createdBody = objectMapper.readTree(created.getBody());
        assertThat(createdBody.path("code").asText()).isEqualTo("FIELD_RECORD_CREATE_SUCCESS");
        long sourceId = createdBody.path("data").path("record").path("sourceId").asLong();
        assertThat(createdBody.path("data").path("record").path("canEdit").asBoolean()).isTrue();
        assertThat(createdBody.path("data").path("record").path("canDelete").asBoolean()).isTrue();
        assertThat(createdBody.path("data").path("record").path("author").path("memberId").asLong())
                .isEqualTo(memberId);

        // 멱등 재요청 → 200
        ResponseEntity<String> replay = exchange(
                HttpMethod.POST,
                "/api/v1/studies/" + studyId + "/field-visit/records",
                accessToken,
                """
                        {
                          "checklistItemId":%d,
                          "sourceType":"TEXT",
                          "textContent":"HTTP E2E 메모",
                          "clientRequestId":"%s"
                        }
                        """.formatted(checklistItemId, clientRequestId)
        );
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(replay.getBody()).path("code").asText())
                .isEqualTo("FIELD_RECORD_ALREADY_CREATED");

        // STT POST 거절
        ResponseEntity<String> sttRejected = exchange(
                HttpMethod.POST,
                "/api/v1/studies/" + studyId + "/field-visit/records",
                accessToken,
                """
                        {
                          "checklistItemId":%d,
                          "sourceType":"STT",
                          "textContent":"x",
                          "clientRequestId":"%s"
                        }
                        """.formatted(checklistItemId, UUID.randomUUID())
        );
        assertThat(sttRejected.getStatusCode().is4xxClientError()).isTrue();

        // PHOTO POST
        ResponseEntity<String> photo = exchange(
                HttpMethod.POST,
                "/api/v1/studies/" + studyId + "/field-visit/records",
                accessToken,
                """
                        {
                          "checklistItemId":%d,
                          "sourceType":"PHOTO",
                          "photoFileId":%d,
                          "clientRequestId":"%s"
                        }
                        """.formatted(checklistItemId, photoFileId, UUID.randomUUID())
        );
        assertThat(photo.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // GET mineOnly=false
        ResponseEntity<String> list = exchange(
                HttpMethod.GET,
                "/api/v1/studies/" + studyId + "/field-visit/records?mineOnly=false&size=20",
                accessToken,
                null
        );
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listBody = objectMapper.readTree(list.getBody());
        assertThat(listBody.path("code").asText()).isEqualTo("FIELD_RECORD_LIST_SUCCESS");
        assertThat(listBody.path("data").path("readOnly").asBoolean()).isFalse();

        // PATCH
        ResponseEntity<String> patched = exchange(
                HttpMethod.PATCH,
                "/api/v1/studies/" + studyId + "/field-visit/records/" + sourceId,
                accessToken,
                """
                        {"checklistItemId":%d,"textContent":"  교정된 메모  "}
                        """.formatted(otherItemId)
        );
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode patchedBody = objectMapper.readTree(patched.getBody());
        assertThat(patchedBody.path("data").path("sourceId").asLong()).isEqualTo(sourceId);
        assertThat(patchedBody.path("data").path("textContent").asText()).isEqualTo("교정된 메모");
        assertThat(patchedBody.path("data").path("clientRequestId").asText()).isEqualTo(clientRequestId);

        // 공백 PATCH → 400
        ResponseEntity<String> blankPatch = exchange(
                HttpMethod.PATCH,
                "/api/v1/studies/" + studyId + "/field-visit/records/" + sourceId,
                accessToken,
                "{\"textContent\":\"   \"}"
        );
        assertThat(blankPatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 타인 PATCH → 403
        Long otherRecordId = jdbcTemplate.queryForObject("""
                INSERT INTO field_record(
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, client_request_id, request_fingerprint
                )
                SELECT fs.id, ci.id, ?, 'TEXT', 'other', ?, 'fp-other-http'
                FROM field_session fs
                JOIN checklist c ON c.session_id = fs.id AND c.member_id = ?
                JOIN checklist_item ci ON ci.checklist_id = c.id
                WHERE fs.study_id = ?
                LIMIT 1
                RETURNING id
                """, Long.class, otherMemberId, UUID.randomUUID().toString(), otherMemberId, studyId);

        ResponseEntity<String> otherPatch = exchange(
                HttpMethod.PATCH,
                "/api/v1/studies/" + studyId + "/field-visit/records/" + otherRecordId,
                accessToken,
                "{\"textContent\":\"hack\"}"
        );
        assertThat(otherPatch.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // DELETE
        ResponseEntity<String> deleted = exchange(
                HttpMethod.DELETE,
                "/api/v1/studies/" + studyId + "/field-visit/records/" + sourceId,
                accessToken,
                null
        );
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(deleted.getBody()).path("code").asText())
                .isEqualTo("FIELD_RECORD_DELETE_SUCCESS");

        // 멱등 DELETE
        ResponseEntity<String> deletedAgain = exchange(
                HttpMethod.DELETE,
                "/api/v1/studies/" + studyId + "/field-visit/records/" + sourceId,
                accessToken,
                null
        );
        assertThat(deletedAgain.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(deletedAgain.getBody()).path("code").asText())
                .isEqualTo("FIELD_RECORD_ALREADY_DELETED");

        // session 종료 후 쓰기 → 409
        jdbcTemplate.update("""
                UPDATE field_session SET status = 'ENDED' WHERE study_id = ?
                """, studyId);
        ResponseEntity<String> endedWrite = exchange(
                HttpMethod.POST,
                "/api/v1/studies/" + studyId + "/field-visit/records",
                accessToken,
                """
                        {
                          "checklistItemId":%d,
                          "sourceType":"TEXT",
                          "textContent":"ended",
                          "clientRequestId":"%s"
                        }
                        """.formatted(checklistItemId, UUID.randomUUID())
        );
        assertThat(endedWrite.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(endedWrite.getBody()).path("code").asText())
                .isEqualTo("FIELD_VISIT_ALREADY_ENDED");
    }

    private ResponseEntity<String> exchange(
            HttpMethod method,
            String path,
            String token,
            String body
    ) {
        HttpHeaders headers = jsonHeaders(token);
        HttpEntity<String> entity = body == null
                ? new HttpEntity<>(headers)
                : new HttpEntity<>(body, headers);
        return restTemplate.exchange(url(path), method, entity, String.class);
    }

    private static HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void seed() {
        Long apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE015-HTTP', 127.0, 37.5) RETURNING id
                """, Long.class, "BE015H-" + UUID.randomUUID());
        memberId = insertMember("be015-http");
        otherMemberId = insertMember("be015-http-o");
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (apartment_id, leader_id, goal, capacity, title, status)
                VALUES (?, ?, 'be015-http-goal', 5, 'be015-http', 'RECRUITING') RETURNING id
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
        jdbcTemplate.update("DELETE FROM study WHERE title = 'be015-http'");
        jdbcTemplate.update("DELETE FROM apartment WHERE name = 'BE015-HTTP'");
        jdbcTemplate.update("DELETE FROM member WHERE email LIKE 'be015-http%@test.local'");
    }
}
