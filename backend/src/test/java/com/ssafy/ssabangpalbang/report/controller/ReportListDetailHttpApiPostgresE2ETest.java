package com.ssafy.ssabangpalbang.report.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
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

import static org.assertj.core.api.Assertions.assertThat;

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
class ReportListDetailHttpApiPostgresE2ETest {

    private static final String MEMBER_EMAIL =
            "fe021-report-http-e2e@test.local";
    private static final String APARTMENT_CODE =
            "FE021-REPORT-HTTP-E2E";
    private static final String APARTMENT_NAME =
            "FE021 목록 상세 아파트";
    private static final String STUDY_TITLE =
            "FE021 목록 상세 스터디";
    private static final String REPORT_TITLE =
            "FE021 목록 상세 연결 리포트";
    private static final String REPORT_SUMMARY =
            "목록에서 받은 리포트 ID로 상세 API를 조회합니다.";
    private static final String RESULT_JSON = """
            {
              "title": "FE021 목록 상세 연결 리포트",
              "summary": "목록에서 받은 리포트 ID로 상세 API를 조회합니다.",
              "metrics": {
                "totalChecklistItemCount": 0,
                "completedChecklistItemCount": 0,
                "averageCompletionRate": 0.0,
                "fieldRecordCount": 0
              },
              "topPositiveFeatures": [],
              "topCautionFeatures": [],
              "commonOpinions": [],
              "conflictingOpinions": [],
              "categories": []
            }
            """;

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

    private String accessToken;

    @BeforeEach
    void setUp() {
        cleanup();
        Long memberId = seedCompletedReport();
        accessToken = jwtTokenProvider.issue(memberId).accessToken();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void 목록에서_받은_reportId로_동일_JWT_상세_조회에_성공한다()
            throws Exception {
        ResponseEntity<String> listResponse = get(
                "/api/v1/members/me/reports?page=0&size=20",
                accessToken
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listBody = objectMapper.readTree(listResponse.getBody());
        assertThat(listBody.path("code").asText())
                .isEqualTo("MEMBER_REPORT_LIST_SUCCESS");
        assertThat(listBody.path("data").path("totalElements").asLong())
                .isEqualTo(1L);
        assertThat(listBody.path("data").path("page").asInt())
                .isZero();
        assertThat(listBody.path("data").path("size").asInt())
                .isEqualTo(20);
        assertThat(listBody.path("data").path("totalPages").asInt())
                .isEqualTo(1);

        JsonNode listedReport = listBody.path("data").path("content").get(0);
        long listedReportId = listedReport.path("reportId").asLong();
        assertThat(listedReportId).isPositive();
        assertThat(listedReport.path("title").asText())
                .isEqualTo(REPORT_TITLE);
        assertThat(listedReport.path("summary").asText())
                .isEqualTo(REPORT_SUMMARY);
        assertThat(listedReport.path("status").asText()).isEqualTo("DONE");
        assertThat(listedReport.path("analysisTags").isArray()).isTrue();
        assertThat(listedReport.path("apartment").path("apartmentId").asLong())
                .isPositive();
        assertThat(listedReport.path("apartment").path("name").asText())
                .isEqualTo(APARTMENT_NAME);
        assertThat(listedReport.path("study").path("studyId").asLong())
                .isPositive();
        assertThat(listedReport.path("study").path("title").asText())
                .isEqualTo(STUDY_TITLE);

        ResponseEntity<String> detailResponse = get(
                "/api/v1/reports/" + listedReportId,
                accessToken
        );

        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode detailBody = objectMapper.readTree(detailResponse.getBody());
        assertThat(detailBody.path("code").asText())
                .isEqualTo("REPORT_DETAIL_SUCCESS");
        JsonNode detail = detailBody.path("data");
        assertThat(detail.path("reportId").asLong())
                .isEqualTo(listedReportId);
        assertThat(detail.path("status").asText()).isEqualTo("DONE");
        assertThat(detail.path("progressStage").asText())
                .isEqualTo("COMPLETED");
        assertThat(detail.path("title").asText())
                .isEqualTo(listedReport.path("title").asText());
        assertThat(detail.path("summary").asText())
                .isEqualTo(listedReport.path("summary").asText());
        assertThat(detail.path("categories").isArray()).isTrue();
        assertThat(detail.path("apartment").path("apartmentId").asLong())
                .isEqualTo(listedReport.path("apartment")
                        .path("apartmentId").asLong());
        assertThat(detail.path("apartment").path("name").asText())
                .isEqualTo(APARTMENT_NAME);
        assertThat(detail.path("study").path("studyId").asLong())
                .isEqualTo(listedReport.path("study")
                        .path("studyId").asLong());
        assertThat(detail.path("study").path("title").asText())
                .isEqualTo(STUDY_TITLE);
        assertThat(detail.path("study").path("participantCount").asInt())
                .isEqualTo(1);
        assertThat(detail.path("viewer").path("isParticipant").asBoolean())
                .isTrue();
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(
                url(path),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Long seedCompletedReport() {
        Long seededMemberId = jdbcTemplate.queryForObject("""
                INSERT INTO member (email, nickname, status)
                VALUES (?, ?, 'ACTIVE')
                RETURNING id
                """, Long.class, MEMBER_EMAIL, "fe021-report-http-e2e");

        Long apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (
                    complex_code, name, address, longitude, latitude,
                    household_count, completion_year_month,
                    parking_space_count
                )
                VALUES (?, ?, '서울특별시 성동구', 127.04, 37.54,
                        1200, '2018-09', 1500)
                RETURNING id
                """, Long.class, APARTMENT_CODE, APARTMENT_NAME);

        Long studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (
                    apartment_id, leader_id, title, goal, capacity,
                    purpose, status
                )
                VALUES (?, ?, ?, '목록과 상세 연결을 검증한다.', 1,
                        'RESIDENCE', 'COMPLETED')
                RETURNING id
                """, Long.class, apartmentId, seededMemberId, STUDY_TITLE);

        jdbcTemplate.update("""
                INSERT INTO study_member (
                    study_id, member_id, role, status
                )
                VALUES (?, ?, 'LEADER', 'ACTIVE')
                """, studyId, seededMemberId);

        Long sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO field_session (
                    study_id, status, started_at, ended_at,
                    ended_by_id, end_reason
                )
                VALUES (?, 'ENDED',
                        TIMESTAMPTZ '2026-08-01 01:00:00+00',
                        TIMESTAMPTZ '2026-08-01 02:00:00+00',
                        ?, 'ALL_ENDED')
                RETURNING id
                """, Long.class, studyId, seededMemberId);

        jdbcTemplate.update("""
                INSERT INTO field_participant (
                    session_id, member_id, status, started_at, ended_at,
                    end_reason, stay_duration_sec
                )
                VALUES (?, ?, 'ENDED',
                        TIMESTAMPTZ '2026-08-01 01:00:00+00',
                        TIMESTAMPTZ '2026-08-01 02:00:00+00',
                        'SELF_ENDED', 3600)
                """, sessionId, seededMemberId);

        jdbcTemplate.queryForObject("""
                INSERT INTO report (
                    study_id, apartment_id, field_session_id, status,
                    progress_stage, result_json, published_at, completed_at
                )
                VALUES (?, ?, ?, 'DONE', 'COMPLETED', ?::jsonb,
                        TIMESTAMPTZ '2026-08-01 02:05:00+00',
                        TIMESTAMPTZ '2026-08-01 02:05:00+00')
                RETURNING id
                """, Long.class,
                studyId,
                apartmentId,
                sessionId,
                RESULT_JSON
        );

        return seededMemberId;
    }

    private void cleanup() {
        jdbcTemplate.update("""
                DELETE FROM report_evidence
                WHERE report_id IN (
                    SELECT r.id
                    FROM report r
                    JOIN study s ON s.id = r.study_id
                    WHERE s.title = ?
                )
                """, STUDY_TITLE);
        jdbcTemplate.update("""
                DELETE FROM report
                WHERE study_id IN (SELECT id FROM study WHERE title = ?)
                """, STUDY_TITLE);
        jdbcTemplate.update("""
                DELETE FROM field_participant
                WHERE session_id IN (
                    SELECT fs.id
                    FROM field_session fs
                    JOIN study s ON s.id = fs.study_id
                    WHERE s.title = ?
                )
                """, STUDY_TITLE);
        jdbcTemplate.update("""
                DELETE FROM field_session
                WHERE study_id IN (SELECT id FROM study WHERE title = ?)
                """, STUDY_TITLE);
        jdbcTemplate.update("""
                DELETE FROM study_member
                WHERE study_id IN (SELECT id FROM study WHERE title = ?)
                """, STUDY_TITLE);
        jdbcTemplate.update("DELETE FROM study WHERE title = ?", STUDY_TITLE);
        jdbcTemplate.update(
                "DELETE FROM apartment WHERE complex_code = ?",
                APARTMENT_CODE
        );
        jdbcTemplate.update(
                "DELETE FROM member WHERE email = ?",
                MEMBER_EMAIL
        );
    }
}
