package com.ssafy.ssabangpalbang.report.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReportEvidenceWriteRepository {

    private static final String INSERT_SQL = """
            INSERT INTO report_evidence (
                report_id,
                field_record_id,
                claim_key,
                display_order
            ) VALUES (
                :reportId,
                :fieldRecordId,
                :claimKey,
                :displayOrder
            )
            """;

    private final JdbcClient jdbcClient;

    public long countByReportId(Long reportId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM report_evidence
                        WHERE report_id = :reportId
                        """)
                .param("reportId", reportId)
                .query(Long.class)
                .single();
    }

    public void deleteByReportId(Long reportId) {
        jdbcClient.sql("""
                        DELETE FROM report_evidence
                        WHERE report_id = :reportId
                        """)
                .param("reportId", reportId)
                .update();
    }

    public void insertAll(Long reportId, List<EvidenceRow> rows) {
        for (EvidenceRow row : rows) {
            jdbcClient.sql(INSERT_SQL)
                    .param("reportId", reportId)
                    .param("fieldRecordId", row.fieldRecordId())
                    .param("claimKey", row.claimKey())
                    .param("displayOrder", row.displayOrder())
                    .update();
        }
    }

    public record EvidenceRow(
            Long fieldRecordId,
            String claimKey,
            int displayOrder
    ) {
    }
}
