package com.ssafy.ssabangpalbang.report.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReportEvidenceQueryRepository {

    static final String VALID_EVIDENCE_JOIN = """
            FROM report_evidence re
            JOIN report r ON r.id = re.report_id
            JOIN field_record fr ON fr.id = re.field_record_id
            JOIN checklist_item ci ON ci.id = fr.checklist_item_id
            JOIN checklist c ON c.id = ci.checklist_id
            JOIN field_participant fp
              ON fp.session_id = r.field_session_id
             AND fp.member_id = fr.author_id
            WHERE re.report_id = :reportId
              AND fr.session_id = r.field_session_id
              AND fr.deleted_at IS NULL
              AND c.session_id = r.field_session_id
              AND c.member_id = fr.author_id
              AND fr.text_content IS NOT NULL
              AND BTRIM(fr.text_content) <> ''
              AND (
                    fr.source_type = 'TEXT'
                    OR (
                        fr.source_type = 'STT'
                        AND fr.stt_status = 'DONE'
                    )
              )
            """;

    static final String PAGE_SELECT = """
            WITH participant_labels AS (
                SELECT fp.member_id,
                       ROW_NUMBER() OVER (
                           ORDER BY fp.started_at ASC, fp.id ASC
                       ) AS participant_number
                FROM field_participant fp
                JOIN report r ON r.field_session_id = fp.session_id
                WHERE r.id = :reportId
            )
            SELECT DISTINCT fr.id AS source_id,
                   fr.source_type,
                   ci.category,
                   ci.id AS checklist_item_id,
                   ci.title AS checklist_item_title,
                   ci.subtitle AS checklist_item_subtitle,
                   (
                       SELECT pl.participant_number
                       FROM participant_labels pl
                       WHERE pl.member_id = fr.author_id
                   ) AS participant_number,
                   fr.text_content,
                   fr.created_at
            """;

    static final String SUMMARY_SELECT = """
            SELECT COUNT(DISTINCT fr.id) AS total_count,
                   COUNT(DISTINCT CASE
                       WHEN fr.source_type = 'TEXT' THEN fr.id
                   END) AS text_count,
                   COUNT(DISTINCT CASE
                       WHEN fr.source_type = 'STT' THEN fr.id
                   END) AS stt_count
            """;

    static final String DETAIL_SQL = """
            WITH participant_labels AS (
                SELECT fp.member_id,
                       ROW_NUMBER() OVER (
                           ORDER BY fp.started_at ASC, fp.id ASC
                       ) AS participant_number
                FROM field_participant fp
                JOIN report r ON r.field_session_id = fp.session_id
                WHERE r.id = :reportId
            )
            SELECT DISTINCT fr.id AS source_id,
                   fr.source_type,
                   ci.category,
                   ci.id AS checklist_item_id,
                   ci.title AS checklist_item_title,
                   ci.subtitle AS checklist_item_subtitle,
                   (
                       SELECT pl.participant_number
                       FROM participant_labels pl
                       WHERE pl.member_id = fr.author_id
                   ) AS participant_number,
                   fr.text_content,
                   fr.stt_status,
                   fr.photo_file_id,
                   fm.original_name AS photo_original_name,
                   fm.content_type AS photo_content_type,
                   fm.size_bytes AS photo_size_bytes,
                   CASE WHEN fm.id IS NOT NULL
                             AND fm.owner_id = fr.author_id
                             AND fm.study_id = r.study_id
                             AND fm.file_usage = 'FIELD_PHOTO'
                             AND fm.upload_status = 'COMPLETED'
                             AND fm.deleted_at IS NULL
                             AND (
                                 fm.expires_at IS NULL
                                 OR fm.expires_at > CURRENT_TIMESTAMP
                             )
                        THEN TRUE ELSE FALSE END AS photo_available,
                   fr.created_at
            FROM report r
            JOIN field_record fr
              ON fr.session_id = r.field_session_id
            JOIN checklist_item ci
              ON ci.id = fr.checklist_item_id
            JOIN checklist c
              ON c.id = ci.checklist_id
             AND c.session_id = r.field_session_id
             AND c.member_id = fr.author_id
            JOIN field_participant author_participant
              ON author_participant.session_id = r.field_session_id
             AND author_participant.member_id = fr.author_id
            LEFT JOIN file_meta fm
              ON fm.id = fr.photo_file_id
            WHERE r.id = :reportId
              AND fr.id = :sourceId
              AND fr.deleted_at IS NULL
              AND (
                  (
                      fr.source_type = 'TEXT'
                      AND fr.text_content IS NOT NULL
                      AND BTRIM(fr.text_content) <> ''
                  )
                  OR (
                      fr.source_type = 'STT'
                      AND fr.stt_status = 'DONE'
                      AND fr.text_content IS NOT NULL
                      AND BTRIM(fr.text_content) <> ''
                  )
                  OR (
                      fr.source_type = 'PHOTO'
                      AND fr.photo_file_id IS NOT NULL
                  )
              )
            """;

    static final String SOURCE_RELATION_SQL = """
            SELECT EXISTS (
                       SELECT 1
                       FROM field_record fr
                       WHERE fr.id = :sourceId
                   ) AS source_exists,
                   EXISTS (
                       SELECT 1
                       FROM report r
                       JOIN field_record fr
                         ON fr.session_id = r.field_session_id
                       WHERE r.id = :reportId
                         AND fr.id = :sourceId
                   ) AS source_in_report_session
            """;

    static final String LINK_SQL = """
            SELECT re.field_record_id,
                   re.claim_key,
                   re.display_order
            FROM report_evidence re
            WHERE re.report_id = :reportId
              AND re.field_record_id IN (:sourceIds)
            ORDER BY re.display_order ASC, re.id ASC
            """;

    private final JdbcClient jdbcClient;

    public List<EvidenceRow> findPage(
            Long reportId,
            String sourceType,
            String category,
            List<Long> sourceIds,
            Long cursor,
            int limit
    ) {
        StringBuilder sql = new StringBuilder(PAGE_SELECT)
                .append(VALID_EVIDENCE_JOIN);
        appendFilters(sql, sourceType, category, sourceIds, cursor);
        sql.append(" ORDER BY fr.id ASC LIMIT :limit");

        JdbcClient.StatementSpec statement = jdbcClient.sql(sql.toString())
                .param("reportId", reportId)
                .param("limit", limit);
        statement = bindFilters(
                statement,
                sourceType,
                category,
                sourceIds,
                cursor
        );
        return statement.query((resultSet, rowNum) -> mapEvidence(resultSet))
                .list();
    }

    public EvidenceSummaryRow findSummary(Long reportId) {
        return jdbcClient.sql(SUMMARY_SELECT + VALID_EVIDENCE_JOIN)
                .param("reportId", reportId)
                .query((resultSet, rowNum) -> new EvidenceSummaryRow(
                        resultSet.getLong("total_count"),
                        resultSet.getLong("text_count"),
                        resultSet.getLong("stt_count")
                ))
                .single();
    }

    public Optional<EvidenceDetailRow> findDetail(
            Long reportId,
            Long sourceId
    ) {
        return jdbcClient.sql(DETAIL_SQL)
                .param("reportId", reportId)
                .param("sourceId", sourceId)
                .query((resultSet, rowNum) -> mapEvidenceDetail(resultSet))
                .optional();
    }

    public SourceRelationRow findSourceRelation(
            Long reportId,
            Long sourceId
    ) {
        return jdbcClient.sql(SOURCE_RELATION_SQL)
                .param("reportId", reportId)
                .param("sourceId", sourceId)
                .query((resultSet, rowNum) -> new SourceRelationRow(
                        resultSet.getBoolean("source_exists"),
                        resultSet.getBoolean("source_in_report_session")
                ))
                .single();
    }

    public long countRequestedSources(
            Long reportId,
            List<Long> sourceIds
    ) {
        String sql = "SELECT COUNT(DISTINCT fr.id) "
                + VALID_EVIDENCE_JOIN
                + " AND fr.id IN (:sourceIds)";
        return jdbcClient.sql(sql)
                .param("reportId", reportId)
                .param("sourceIds", sourceIds)
                .query(Long.class)
                .single();
    }

    public List<EvidenceLinkRow> findLinks(
            Long reportId,
            Collection<Long> sourceIds
    ) {
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(LINK_SQL)
                .param("reportId", reportId)
                .param("sourceIds", sourceIds)
                .query((resultSet, rowNum) -> new EvidenceLinkRow(
                        resultSet.getLong("field_record_id"),
                        resultSet.getString("claim_key"),
                        resultSet.getInt("display_order")
                ))
                .list();
    }

    private void appendFilters(
            StringBuilder sql,
            String sourceType,
            String category,
            List<Long> sourceIds,
            Long cursor
    ) {
        if (sourceType != null) {
            sql.append(" AND fr.source_type = :sourceType");
        }
        if (category != null) {
            sql.append(" AND ci.category = :category");
        }
        if (!sourceIds.isEmpty()) {
            sql.append(" AND fr.id IN (:sourceIds)");
        }
        if (cursor != null) {
            sql.append(" AND fr.id > :cursor");
        }
    }

    private JdbcClient.StatementSpec bindFilters(
            JdbcClient.StatementSpec statement,
            String sourceType,
            String category,
            List<Long> sourceIds,
            Long cursor
    ) {
        if (sourceType != null) {
            statement = statement.param("sourceType", sourceType);
        }
        if (category != null) {
            statement = statement.param("category", category);
        }
        if (!sourceIds.isEmpty()) {
            statement = statement.param("sourceIds", sourceIds);
        }
        if (cursor != null) {
            statement = statement.param("cursor", cursor);
        }
        return statement;
    }

    private EvidenceRow mapEvidence(ResultSet resultSet) throws SQLException {
        return new EvidenceRow(
                resultSet.getLong("source_id"),
                resultSet.getString("source_type"),
                resultSet.getString("category"),
                resultSet.getLong("checklist_item_id"),
                resultSet.getString("checklist_item_title"),
                resultSet.getString("checklist_item_subtitle"),
                resultSet.getInt("participant_number"),
                resultSet.getString("text_content"),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }

    private EvidenceDetailRow mapEvidenceDetail(ResultSet resultSet)
            throws SQLException {
        return new EvidenceDetailRow(
                resultSet.getLong("source_id"),
                resultSet.getString("source_type"),
                resultSet.getString("category"),
                resultSet.getLong("checklist_item_id"),
                resultSet.getString("checklist_item_title"),
                resultSet.getString("checklist_item_subtitle"),
                resultSet.getInt("participant_number"),
                resultSet.getString("text_content"),
                resultSet.getString("stt_status"),
                nullableLong(resultSet, "photo_file_id"),
                resultSet.getString("photo_original_name"),
                resultSet.getString("photo_content_type"),
                nullableLong(resultSet, "photo_size_bytes"),
                resultSet.getBoolean("photo_available"),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }

    private Long nullableLong(ResultSet resultSet, String column)
            throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    public record EvidenceRow(
            Long sourceId,
            String sourceType,
            String category,
            Long checklistItemId,
            String checklistItemTitle,
            String checklistItemSubtitle,
            int participantNumber,
            String textContent,
            OffsetDateTime recordedAt
    ) {
    }

    public record EvidenceSummaryRow(
            long totalEvidenceCount,
            long textCount,
            long sttCount
    ) {
    }

    public record EvidenceDetailRow(
            Long sourceId,
            String sourceType,
            String category,
            Long checklistItemId,
            String checklistItemTitle,
            String checklistItemSubtitle,
            int participantNumber,
            String textContent,
            String sttStatus,
            Long photoFileId,
            String photoOriginalName,
            String photoContentType,
            Long photoSizeBytes,
            boolean photoAvailable,
            OffsetDateTime recordedAt
    ) {
    }

    public record SourceRelationRow(
            boolean sourceExists,
            boolean sourceInReportSession
    ) {
    }

    public record EvidenceLinkRow(
            Long sourceId,
            String claimKey,
            int displayOrder
    ) {
    }
}
