package com.ssafy.ssabangpalbang.fieldvisit.stt.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Import(SttRequestQueryRepository.class)
class SttRequestQueryRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SttRequestQueryRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS field_session (
                    id BIGINT PRIMARY KEY,
                    study_id BIGINT NOT NULL,
                    status VARCHAR(20) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS field_participant (
                    id BIGINT PRIMARY KEY,
                    session_id BIGINT NOT NULL,
                    member_id BIGINT NOT NULL,
                    status VARCHAR(20) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS checklist (
                    id BIGINT PRIMARY KEY,
                    session_id BIGINT NOT NULL,
                    member_id BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS checklist_item (
                    id BIGINT PRIMARY KEY,
                    checklist_id BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS file_meta (
                    id BIGINT PRIMARY KEY,
                    owner_id BIGINT NOT NULL,
                    study_id BIGINT,
                    file_usage VARCHAR(30) NOT NULL,
                    upload_status VARCHAR(20) NOT NULL,
                    deleted_at TIMESTAMP WITH TIME ZONE,
                    expires_at TIMESTAMP WITH TIME ZONE,
                    s3_key VARCHAR(500) NOT NULL,
                    content_type VARCHAR(100)
                )
                """);
    }

    @Test
    void 임장_항목과_음성_검증_projection을_조회한다() {
        OffsetDateTime expiresAt = OffsetDateTime.of(
                2026,
                7,
                26,
                14,
                25,
                0,
                0,
                ZoneOffset.ofHours(9)
        );
        jdbcTemplate.update(
                "INSERT INTO field_session(id, study_id, status) VALUES (?, ?, ?)",
                100L,
                7L,
                "IN_PROGRESS"
        );
        jdbcTemplate.update("""
                        INSERT INTO field_participant(
                            id, session_id, member_id, status
                        ) VALUES (?, ?, ?, ?)
                        """,
                200L,
                100L,
                3L,
                "IN_PROGRESS"
        );
        jdbcTemplate.update(
                "INSERT INTO checklist(id, session_id, member_id) VALUES (?, ?, ?)",
                400L,
                100L,
                3L
        );
        jdbcTemplate.update(
                "INSERT INTO checklist_item(id, checklist_id) VALUES (?, ?)",
                501L,
                400L
        );
        jdbcTemplate.update("""
                        INSERT INTO file_meta(
                            id, owner_id, study_id, file_usage, upload_status,
                            deleted_at, expires_at, s3_key, content_type
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                90L,
                3L,
                7L,
                "STT_AUDIO",
                "COMPLETED",
                null,
                expiresAt,
                "stt/audio.webm",
                "audio/webm"
        );

        assertThat(repository.findFieldSessionByStudyId(7L))
                .get()
                .satisfies(session -> {
                    assertThat(session.sessionId()).isEqualTo(100L);
                    assertThat(session.status()).isEqualTo("IN_PROGRESS");
                });
        assertThat(repository.findParticipantStatus(100L, 3L))
                .contains("IN_PROGRESS");
        assertThat(repository.findChecklistItem(501L))
                .get()
                .satisfies(item -> {
                    assertThat(item.memberId()).isEqualTo(3L);
                    assertThat(item.sessionId()).isEqualTo(100L);
                    assertThat(item.studyId()).isEqualTo(7L);
                });
        assertThat(repository.findAudioFile(90L))
                .get()
                .satisfies(file -> {
                    assertThat(file.ownerId()).isEqualTo(3L);
                    assertThat(file.expiresAt()).isEqualTo(expiresAt);
                    assertThat(file.objectKey()).isEqualTo("stt/audio.webm");
                    assertThat(file.contentType()).isEqualTo("audio/webm");
                });
        assertThat(repository.findFieldSessionByStudyIdForUpdate(7L))
                .isPresent();
        assertThat(repository.findParticipantStatusForUpdate(100L, 3L))
                .contains("IN_PROGRESS");
        assertThat(repository.findChecklistItemForUpdate(501L))
                .isPresent();
        assertThat(repository.findAudioFileForUpdate(90L))
                .isPresent();
    }
}
