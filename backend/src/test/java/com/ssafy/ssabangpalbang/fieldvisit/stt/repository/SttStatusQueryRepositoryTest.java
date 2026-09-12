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
@Import(SttStatusQueryRepository.class)
class SttStatusQueryRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SttStatusQueryRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS field_record (
                    id BIGINT PRIMARY KEY,
                    checklist_item_id BIGINT NOT NULL,
                    source_type VARCHAR(20) NOT NULL,
                    stt_status VARCHAR(20),
                    text_content CLOB
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS file_meta (
                    id BIGINT PRIMARY KEY,
                    file_usage VARCHAR(30) NOT NULL,
                    upload_status VARCHAR(20) NOT NULL,
                    deleted_at TIMESTAMP WITH TIME ZONE,
                    expires_at TIMESTAMP WITH TIME ZONE
                )
                """);
    }

    @Test
    void 완료_기록과_음성_보존_상태의_최소_projection을_조회한다() {
        OffsetDateTime expiresAt = OffsetDateTime.of(
                2026,
                7,
                30,
                14,
                25,
                0,
                0,
                ZoneOffset.ofHours(9)
        );
        jdbcTemplate.update("""
                        INSERT INTO field_record(
                            id, checklist_item_id, source_type, stt_status,
                            text_content
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                830L,
                501L,
                "STT",
                "DONE",
                "역에서 단지 입구까지 경사가 있습니다."
        );
        jdbcTemplate.update("""
                        INSERT INTO file_meta(
                            id, file_usage, upload_status, deleted_at,
                            expires_at
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                90L,
                "STT_AUDIO",
                "COMPLETED",
                null,
                expiresAt
        );

        assertThat(repository.findFieldRecord(830L))
                .get()
                .satisfies(record -> {
                    assertThat(record.checklistItemId()).isEqualTo(501L);
                    assertThat(record.sourceType()).isEqualTo("STT");
                    assertThat(record.sttStatus()).isEqualTo("DONE");
                    assertThat(record.textContent())
                            .isEqualTo("역에서 단지 입구까지 경사가 있습니다.");
                });
        assertThat(repository.findAudioFileState(90L))
                .get()
                .satisfies(audio -> {
                    assertThat(audio.fileUsage()).isEqualTo("STT_AUDIO");
                    assertThat(audio.uploadStatus()).isEqualTo("COMPLETED");
                    assertThat(audio.deletedAt()).isNull();
                    assertThat(audio.expiresAt()).isEqualTo(expiresAt);
                });
    }

    @Test
    void 존재하지_않는_연관_데이터는_빈_결과를_반환한다() {
        assertThat(repository.findFieldRecord(999L)).isEmpty();
        assertThat(repository.findAudioFileState(999L)).isEmpty();
    }
}
