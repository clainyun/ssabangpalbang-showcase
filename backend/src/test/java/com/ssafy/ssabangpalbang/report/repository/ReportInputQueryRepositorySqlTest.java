package com.ssafy.ssabangpalbang.report.repository;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ReportInputQueryRepositorySqlTest {

    @Test
    void 권위_세션과_결정적_정렬을_사용한다() {
        String contextSql = normalize(
                ReportInputQueryRepository.CONTEXT_SQL
        );
        String participantsSql = normalize(
                ReportInputQueryRepository.PARTICIPANTS_SQL
        );
        String checklistSql = normalize(
                ReportInputQueryRepository.CHECKLIST_ITEMS_SQL
        );
        String fieldRecordsSql = normalize(
                ReportInputQueryRepository.FIELD_RECORDS_SQL
        );
        String incompleteSttSql = normalize(
                ReportInputQueryRepository.INCOMPLETE_STT_JOBS_SQL
        );

        assertThat(contextSql)
                .contains("JOIN FIELD_SESSION FS ON FS.ID = R.FIELD_SESSION_ID")
                .doesNotContain("WHERE FS.STUDY_ID = :STUDYID");
        assertThat(participantsSql)
                .contains("ORDER BY FP.STARTED_AT ASC, FP.ID ASC");
        assertThat(checklistSql)
                .contains("CI.DISPLAY_ORDER ASC")
                .contains("CI.ID ASC");
        assertThat(fieldRecordsSql).contains("ORDER BY FR.ID ASC");
        assertThat(incompleteSttSql)
                .contains("SJ.FIELD_RECORD_ID IS NULL")
                .contains("ORDER BY SJ.STT_ID ASC, SJ.ID ASC");
    }

    @Test
    void 내부_응답에_금지된_저장소와_오디오_정보를_조회하지_않는다() {
        String allInputSql = String.join(
                " ",
                ReportInputQueryRepository.CONTEXT_SQL,
                ReportInputQueryRepository.PARTICIPANTS_SQL,
                ReportInputQueryRepository.CHECKLIST_ITEMS_SQL,
                ReportInputQueryRepository.FIELD_RECORDS_SQL,
                ReportInputQueryRepository.INCOMPLETE_STT_JOBS_SQL
        ).toLowerCase(Locale.ROOT);

        assertThat(allInputSql)
                .doesNotContain("s3_key")
                .doesNotContain("original_name")
                .doesNotContain("audio_file_id")
                .doesNotContain("fail_reason")
                .doesNotContain("select fm.*")
                .doesNotContain("select sj.*")
                .doesNotContain("select fr.*");
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
