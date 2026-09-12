package com.ssafy.ssabangpalbang.fieldvisit.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("postgres")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class FieldVisitBe015PostgresConstraintTest {

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
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long sessionId;
    private Long checklistItemId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO apartment (complex_code, name, longitude, latitude) "
                        + "VALUES ('BE015-COMPLEX', 'BE015 아파트', 127.0, 37.5)"
        );
        Long apartmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM apartment WHERE complex_code = 'BE015-COMPLEX'", Long.class
        );
        memberId = insertMember("be015@example.com", "be015");
        jdbcTemplate.update(
                "INSERT INTO study (apartment_id, leader_id, goal, capacity) VALUES (?, ?, 'g', 5)",
                apartmentId, memberId
        );
        Long studyId = jdbcTemplate.queryForObject(
                "SELECT id FROM study WHERE leader_id = ?", Long.class, memberId
        );
        jdbcTemplate.update(
                "INSERT INTO field_session (study_id, status, started_at) VALUES (?, 'IN_PROGRESS', ?)",
                studyId, Timestamp.from(Instant.now())
        );
        sessionId = jdbcTemplate.queryForObject(
                "SELECT id FROM field_session WHERE study_id = ?", Long.class, studyId
        );
        jdbcTemplate.update(
                "INSERT INTO checklist (session_id, member_id, is_fallback) VALUES (?, ?, false)",
                sessionId, memberId
        );
        Long checklistId = jdbcTemplate.queryForObject(
                "SELECT id FROM checklist WHERE session_id = ? AND member_id = ?",
                Long.class, sessionId, memberId
        );
        jdbcTemplate.update(
                "INSERT INTO checklist_item (checklist_id, category, title, display_order) "
                        + "VALUES (?, '교통', '역', 1)",
                checklistId
        );
        checklistItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM checklist_item WHERE checklist_id = ?",
                Long.class, checklistId
        );
    }

    @Test
    void field_record_client_request_id는_UNIQUE다() {
        jdbcTemplate.update("""
                INSERT INTO field_record(
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, client_request_id, request_fingerprint
                ) VALUES (?, ?, ?, 'TEXT', 'a', '11111111-1111-1111-1111-111111111111', 'fp1')
                """, sessionId, checklistItemId, memberId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO field_record(
                    session_id, checklist_item_id, author_id, source_type,
                    text_content, client_request_id, request_fingerprint
                ) VALUES (?, ?, ?, 'TEXT', 'b', '11111111-1111-1111-1111-111111111111', 'fp2')
                """, sessionId, checklistItemId, memberId)).isInstanceOf(Exception.class);
    }

    @Test
    void checklist_answer_item_id는_UNIQUE다() {
        jdbcTemplate.update(
                "INSERT INTO checklist_answer(checklist_item_id, is_completed) VALUES (?, true)",
                checklistItemId
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO checklist_answer(checklist_item_id, is_completed) VALUES (?, false)",
                checklistItemId
        )).isInstanceOf(Exception.class);
    }

    @Test
    void request_fingerprint_컬럼이_존재한다() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_name = 'field_record'
                  AND column_name = 'request_fingerprint'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    private Long insertMember(String email, String nickname) {
        jdbcTemplate.update(
                "INSERT INTO member (email, nickname, age_group_public_agreed, "
                        + "service_notification_agreed, ad_notification_agreed) "
                        + "VALUES (?, ?, false, true, false)",
                email, nickname
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM member WHERE email = ?", Long.class, email
        );
    }
}
