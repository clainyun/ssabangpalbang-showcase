package com.ssafy.ssabangpalbang.report.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDetailQueryRepositorySqlTest {

    @Test
    void 근거는_리포트의_권위_임장_세션과_유효한_TEXT_STT로_제한한다() {
        assertThat(ReportDetailQueryRepository.EVIDENCE_SQL)
                .contains("JOIN report r ON r.id = re.report_id")
                .contains("fr.session_id = r.field_session_id")
                .contains("fr.deleted_at IS NULL")
                .contains("fr.text_content IS NOT NULL")
                .contains("BTRIM(fr.text_content) <> ''")
                .contains("fr.source_type = 'TEXT'")
                .contains("fr.source_type = 'STT'")
                .contains("fr.stt_status = 'DONE'");
    }

    @Test
    void 미완료_상태_열람은_스터디장과_ACTIVE_스터디원으로_제한한다() {
        assertThat(ReportDetailQueryRepository.DETAIL_SQL)
                .contains("s.leader_id = :memberId")
                .contains("FROM study_member sm")
                .contains("sm.status = 'ACTIVE'");
    }
}
