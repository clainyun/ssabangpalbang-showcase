package com.ssafy.ssabangpalbang.report.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportEvidenceQueryRepositorySqlTest {

    @Test
    void 권위_세션의_직접_TEXT_DONE_STT만_조회한다() {
        assertThat(ReportEvidenceQueryRepository.VALID_EVIDENCE_JOIN)
                .contains("fr.session_id = r.field_session_id")
                .contains("c.session_id = r.field_session_id")
                .contains("c.member_id = fr.author_id")
                .contains("fp.member_id = fr.author_id")
                .contains("fr.deleted_at IS NULL")
                .contains("fr.source_type = 'TEXT'")
                .contains("fr.source_type = 'STT'")
                .contains("fr.stt_status = 'DONE'")
                .doesNotContain("PHOTO")
                .doesNotContain("s3_key")
                .doesNotContain("audio_file_id")
                .doesNotContain("SELECT *");
    }

    @Test
    void 익명_참여자_번호는_AI_정규화와_같은_순서를_사용한다() {
        assertThat(ReportEvidenceQueryRepository.PAGE_SELECT)
                .contains("ORDER BY fp.started_at ASC, fp.id ASC")
                .contains("SELECT DISTINCT fr.id AS source_id");
    }

    @Test
    void 원문_상세는_AI_인용과_무관하게_권위_세션의_세_유형을_조회한다() {
        assertThat(ReportEvidenceQueryRepository.DETAIL_SQL)
                .contains("fr.session_id = r.field_session_id")
                .contains("c.session_id = r.field_session_id")
                .contains("c.member_id = fr.author_id")
                .contains("author_participant.member_id = fr.author_id")
                .contains("fr.source_type = 'TEXT'")
                .contains("fr.source_type = 'STT'")
                .contains("fr.stt_status = 'DONE'")
                .contains("fr.source_type = 'PHOTO'")
                .contains("fm.owner_id = fr.author_id")
                .contains("fm.study_id = r.study_id")
                .contains("fm.file_usage = 'FIELD_PHOTO'")
                .contains("fm.upload_status = 'COMPLETED'")
                .contains("fr.deleted_at IS NULL")
                .contains("fm.deleted_at IS NULL")
                .contains("fm.expires_at")
                .contains("CURRENT_TIMESTAMP")
                .doesNotContain("report_evidence")
                .doesNotContain("s3_key")
                .doesNotContain("SELECT *");
    }
}
