package com.ssafy.ssabangpalbang.report.repository;

import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReportDetailQueryRepository {

    static final String DETAIL_SQL = """
            SELECT r.id AS report_id,
                   r.field_session_id AS field_session_id,
                   r.status AS report_status,
                   r.progress_stage,
                   CAST(r.result_json AS TEXT) AS result_json,
                   r.is_retryable,
                   r.completed_at,
                   r.updated_at,
                   a.id AS apartment_id,
                   a.name AS apartment_name,
                   a.address AS apartment_address,
                   a.household_count,
                   a.completion_year_month,
                   a.parking_space_count,
                   s.id AS study_id,
                   s.title AS study_title,
                   s.goal AS study_goal,
                   fs.started_at AS visited_at,
                   (
                       SELECT COUNT(*)
                       FROM field_participant fp
                       WHERE fp.session_id = r.field_session_id
                   ) AS participant_count,
                   EXISTS (
                       SELECT 1
                       FROM field_participant fp
                       WHERE fp.session_id = r.field_session_id
                         AND fp.member_id = :memberId
                   ) AS is_participant,
                   (
                       s.leader_id = :memberId
                       OR EXISTS (
                           SELECT 1
                           FROM study_member sm
                           WHERE sm.study_id = r.study_id
                             AND sm.member_id = :memberId
                             AND sm.status = 'ACTIVE'
                       )
                   ) AS can_view_processing_state,
                   EXISTS (
                       SELECT 1
                       FROM report_favorite rf
                       WHERE rf.report_id = r.id
                         AND rf.member_id = :memberId
                   ) AS favorited_by_me,
                   (
                       SELECT COUNT(*)
                       FROM report_favorite rf
                       WHERE rf.report_id = r.id
                   ) AS favorite_count,
                   (
                       SELECT MIN(p.id)
                       FROM post p
                       WHERE p.report_id = r.id
                         AND p.is_auto_report = TRUE
                         AND p.status = 'ACTIVE'
                         AND p.deleted_at IS NULL
                   ) AS post_id,
                   CASE WHEN s.apartment_id = r.apartment_id
                             AND fs.study_id = r.study_id
                             AND s.deleted_at IS NULL
                             AND s.canceled_at IS NULL
                             AND s.status <> 'CANCELED'
                        THEN TRUE ELSE FALSE END AS source_valid
            FROM report r
            JOIN study s ON s.id = r.study_id
            JOIN apartment a ON a.id = r.apartment_id
            JOIN field_session fs ON fs.id = r.field_session_id
            WHERE r.id = :reportId
            """;

    static final String EVIDENCE_SQL = """
             SELECT re.claim_key,
                    re.display_order,
                    re.field_record_id
             FROM report_evidence re
             JOIN report r ON r.id = re.report_id
             JOIN field_record fr ON fr.id = re.field_record_id
             WHERE re.report_id = :reportId
               AND fr.session_id = r.field_session_id
               AND fr.deleted_at IS NULL
               AND fr.text_content IS NOT NULL
               AND BTRIM(fr.text_content) <> ''
               AND (
                  fr.source_type = 'TEXT'
                  OR (
                      fr.source_type = 'STT'
                      AND fr.stt_status = 'DONE'
                  )
              )
            ORDER BY re.display_order ASC, re.id ASC
            """;

    static final String CATEGORY_CHECKLIST_COUNT_SQL = """
            SELECT ci.category AS category,
                   COUNT(*) AS item_count
            FROM checklist_item ci
            JOIN checklist c ON c.id = ci.checklist_id
            WHERE c.session_id = :sessionId
              AND EXISTS (
                  SELECT 1
                  FROM field_participant fp
                  WHERE fp.session_id = c.session_id
                    AND fp.member_id = c.member_id
              )
            GROUP BY ci.category
            """;

    private final JdbcClient jdbcClient;

    public Optional<DetailRow> findDetail(Long memberId, Long reportId) {
        return jdbcClient.sql(DETAIL_SQL)
                .param("memberId", memberId)
                .param("reportId", reportId)
                .query((resultSet, rowNum) -> mapDetail(resultSet))
                .optional();
    }

    public List<EvidenceRow> findEvidenceRows(Long reportId) {
        return jdbcClient.sql(EVIDENCE_SQL)
                .param("reportId", reportId)
                .query((resultSet, rowNum) -> new EvidenceRow(
                        resultSet.getString("claim_key"),
                        resultSet.getInt("display_order"),
                        resultSet.getLong("field_record_id")
                ))
                .list();
    }

    public List<CategoryChecklistCount> countChecklistItemsByCategory(
            Long sessionId
    ) {
        return jdbcClient.sql(CATEGORY_CHECKLIST_COUNT_SQL)
                .param("sessionId", sessionId)
                .query((resultSet, rowNum) -> new CategoryChecklistCount(
                        resultSet.getString("category"),
                        Math.toIntExact(resultSet.getLong("item_count"))
                ))
                .list();
    }

    private DetailRow mapDetail(ResultSet resultSet) throws SQLException {
        return new DetailRow(
                resultSet.getLong("report_id"),
                resultSet.getLong("field_session_id"),
                ReportStatus.valueOf(resultSet.getString("report_status")),
                resultSet.getString("progress_stage"),
                resultSet.getString("result_json"),
                resultSet.getBoolean("is_retryable"),
                offsetDateTime(resultSet, "completed_at"),
                offsetDateTime(resultSet, "updated_at"),
                resultSet.getLong("apartment_id"),
                resultSet.getString("apartment_name"),
                resultSet.getString("apartment_address"),
                nullableInteger(resultSet, "household_count"),
                resultSet.getString("completion_year_month"),
                nullableInteger(resultSet, "parking_space_count"),
                resultSet.getLong("study_id"),
                resultSet.getString("study_title"),
                resultSet.getString("study_goal"),
                offsetDateTime(resultSet, "visited_at"),
                 Math.toIntExact(resultSet.getLong("participant_count")),
                 resultSet.getBoolean("is_participant"),
                 resultSet.getBoolean("can_view_processing_state"),
                 resultSet.getBoolean("favorited_by_me"),
                resultSet.getLong("favorite_count"),
                nullableLong(resultSet, "post_id"),
                resultSet.getBoolean("source_valid")
        );
    }

    private Integer nullableInteger(ResultSet resultSet, String column)
            throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet resultSet, String column)
            throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private OffsetDateTime offsetDateTime(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class);
    }

    public record DetailRow(
            Long reportId,
            Long fieldSessionId,
            ReportStatus status,
            String progressStage,
            String resultJson,
            boolean retryable,
            OffsetDateTime completedAt,
            OffsetDateTime updatedAt,
            Long apartmentId,
            String apartmentName,
            String apartmentAddress,
            Integer householdCount,
            String completionYearMonth,
            Integer parkingSpaceCount,
            Long studyId,
            String studyTitle,
            String studyGoal,
            OffsetDateTime visitedAt,
             int participantCount,
             boolean participant,
             boolean canViewProcessingState,
             boolean favoritedByMe,
            long favoriteCount,
            Long postId,
            boolean sourceValid
    ) {
    }

    public record EvidenceRow(
            String claimKey,
            int displayOrder,
            Long sourceId
    ) {
    }

    public record CategoryChecklistCount(
            String category,
            int itemCount
    ) {
    }
}
