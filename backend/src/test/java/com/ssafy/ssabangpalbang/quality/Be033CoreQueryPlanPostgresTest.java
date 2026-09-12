package com.ssafy.ssabangpalbang.quality;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@Testcontainers
class Be033CoreQueryPlanPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = Be033PostgresContainerFactory.create();

    private static JdbcTemplate jdbcTemplate;
    private static SingleConnectionDataSource dataSource;

    @BeforeAll
    static void migrateAndSeed() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dataSource = new SingleConnectionDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true
        );
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbcTemplate = new JdbcTemplate(dataSource);
        seedPlannerFixture();
        jdbcTemplate.execute("ANALYZE");
        jdbcTemplate.execute("SET enable_seqscan = off");
    }

    @AfterAll
    static void closeDataSource() {
        dataSource.destroy();
    }

    @Test
    void 스터디와_임장_핵심_조회는_확정_인덱스를_사용할_수_있다() {
        assertPlanUses("""
                SELECT * FROM study
                WHERE apartment_id = (SELECT min(id) FROM apartment)
                  AND status = 'RECRUITING'
                ORDER BY id DESC LIMIT 20
                """, "idx_study_apartment_status");
        assertPlanUses("""
                SELECT * FROM field_participant
                WHERE member_id = (SELECT min(id) FROM member)
                ORDER BY session_id LIMIT 20
                """, "idx_field_participant_member");
    }

    @Test
    void 리포트_게시판_챗봇_핵심_조회는_확정_인덱스를_사용할_수_있다() {
        assertPlanUses("""
                SELECT * FROM report
                WHERE apartment_id = (SELECT min(id) FROM apartment)
                  AND status = 'DONE'
                ORDER BY completed_at DESC LIMIT 20
                """, "idx_report_apartment_status");
        assertPlanUses("""
                SELECT * FROM post
                WHERE board_type = 'FREE'
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                ORDER BY created_at DESC LIMIT 20
                """, "idx_post_board_recent");
        assertPlanUses("""
                SELECT * FROM chatbot_conversation
                WHERE member_id = (SELECT min(id) FROM member)
                ORDER BY last_message_at DESC LIMIT 20
                """, "idx_chatbot_conversation_member");
    }

    @Test
    void STT_outbox_준비_조회는_확정_인덱스를_사용할_수_있다() {
        assertPlanUses("""
                SELECT * FROM stt_dispatch_outbox
                WHERE status = 'PENDING' AND next_attempt_at <= now()
                ORDER BY next_attempt_at, id LIMIT 50
                """, "idx_stt_dispatch_outbox_ready");
    }

    private void assertPlanUses(String sql, String expectedIndex) {
        List<String> planRows = jdbcTemplate.queryForList(
                "EXPLAIN (FORMAT JSON) " + sql,
                String.class
        );
        assertThat(planRows)
                .isNotEmpty()
                .anySatisfy(plan -> assertThat(plan).contains(expectedIndex));
    }

    private static void seedPlannerFixture() {
        Long memberId = jdbcTemplate.queryForObject("""
                INSERT INTO member (
                    email, nickname, age_group_public_agreed,
                    service_notification_agreed, ad_notification_agreed
                ) VALUES ('be033-plan@test.local', 'be033-plan', false, true, false)
                RETURNING id
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                SELECT 'BE033-PLAN-' || series, 'BE033-PLAN', 127.0, 37.5
                FROM generate_series(1, 500) AS series
                """);
        jdbcTemplate.update("""
                INSERT INTO study (apartment_id, leader_id, goal, capacity, title, status)
                SELECT id, ?, 'BE033 실행계획', 5, 'be033-plan-' || id, 'RECRUITING'
                FROM apartment WHERE complex_code LIKE 'BE033-PLAN-%'
                """, memberId);
        jdbcTemplate.update("""
                INSERT INTO field_session (study_id, status)
                SELECT id, 'IN_PROGRESS' FROM study WHERE title LIKE 'be033-plan-%'
                """);
        jdbcTemplate.update("""
                INSERT INTO field_participant (session_id, member_id, status)
                SELECT id, ?, 'IN_PROGRESS' FROM field_session
                """, memberId);
        jdbcTemplate.update("""
                INSERT INTO report (
                    study_id, apartment_id, field_session_id,
                    status, completed_at
                )
                SELECT study.id, study.apartment_id, session.id,
                       'DONE', now()
                FROM study
                JOIN field_session session ON session.study_id = study.id
                WHERE study.title LIKE 'be033-plan-%'
                """);
        jdbcTemplate.update("""
                INSERT INTO post (
                    board_type, author_id, title, content, status, created_at
                )
                SELECT 'FREE', ?, 'be033-plan-post-' || series,
                       'BE033 실행계획 본문', 'ACTIVE',
                       now() - (series || ' minutes')::interval
                FROM generate_series(1, 500) AS series
                """, memberId);
        jdbcTemplate.update("""
                INSERT INTO chatbot_conversation (member_id, apartment_id, last_message_at)
                SELECT ?, id, now() FROM apartment
                WHERE complex_code LIKE 'BE033-PLAN-%'
                """, memberId);

        Long studyId = jdbcTemplate.queryForObject(
                "SELECT min(id) FROM study WHERE title LIKE 'be033-plan-%'",
                Long.class
        );
        Long apartmentId = jdbcTemplate.queryForObject(
                "SELECT apartment_id FROM study WHERE id = ?", Long.class, studyId
        );
        Long sessionId = jdbcTemplate.queryForObject(
                "SELECT id FROM field_session WHERE study_id = ?", Long.class, studyId
        );
        Long checklistId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist (session_id, member_id) VALUES (?, ?) RETURNING id
                """, Long.class, sessionId, memberId);
        Long itemId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist_item (checklist_id, category, title, display_order)
                VALUES (?, 'BE033', '실행계획', 1) RETURNING id
                """, Long.class, checklistId);
        Long fileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_meta (
                    owner_id, study_id, file_usage, s3_key, content_type,
                    upload_status
                ) VALUES (?, ?, 'STT_AUDIO', 'be033/plan.webm', 'audio/webm', 'COMPLETED')
                RETURNING id
                """, Long.class, memberId, studyId);
        Long sttJobId = jdbcTemplate.queryForObject("""
                INSERT INTO stt_job (
                    stt_id, member_id, study_id, session_id, audio_file_id,
                    checklist_item_id, initial_client_request_id, status, requested_at
                ) VALUES ('be033-plan-stt', ?, ?, ?, ?, ?, gen_random_uuid(), 'PENDING', now())
                RETURNING id
                """, Long.class, memberId, studyId, sessionId, fileId, itemId);
        jdbcTemplate.update("""
                INSERT INTO stt_dispatch_outbox (
                    stt_job_id, stt_id, attempt_no, audio_file_id, object_key,
                    content_type, language, status, next_attempt_at, created_at, updated_at
                ) VALUES (?, 'be033-plan-stt', 1, ?, 'be033/plan.webm',
                          'audio/webm', 'ko-KR', 'PENDING', now(), now(), now())
                """, sttJobId, fileId);
        assertThat(apartmentId).isNotNull();
    }
}
