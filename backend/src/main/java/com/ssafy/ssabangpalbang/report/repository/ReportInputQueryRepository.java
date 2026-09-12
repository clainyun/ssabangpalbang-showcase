package com.ssafy.ssabangpalbang.report.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecordSourceType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReportInputQueryRepository {

    static final String CONTEXT_SQL = """
            SELECT r.id AS report_id,
                   r.study_id,
                   r.apartment_id,
                   r.field_session_id,
                   fs.status AS session_status,
                   fs.started_at AS session_started_at,
                   fs.ended_at AS session_ended_at,
                   CURRENT_TIMESTAMP AS snapshot_at,
                   CASE WHEN fs.study_id = r.study_id
                             AND s.apartment_id = r.apartment_id
                             AND s.deleted_at IS NULL
                             AND s.canceled_at IS NULL
                             AND s.status <> 'CANCELED'
                        THEN TRUE ELSE FALSE END AS source_valid
            FROM report r
            JOIN study s ON s.id = r.study_id
            JOIN field_session fs ON fs.id = r.field_session_id
            WHERE r.id = :reportId
            """;

    static final String PARTICIPANTS_SQL = """
            SELECT fp.id AS field_participant_id,
                   fp.member_id,
                   fp.status,
                   fp.started_at,
                   fp.ended_at
            FROM field_participant fp
            WHERE fp.session_id = :sessionId
            ORDER BY fp.started_at ASC, fp.id ASC
            """;

    static final String CHECKLIST_ITEMS_SQL = """
            SELECT c.id AS checklist_id,
                   ci.id AS checklist_item_id,
                   c.member_id,
                   c.is_fallback,
                   ci.category,
                   ci.title,
                   ci.subtitle,
                   ci.display_order,
                   COALESCE(ca.is_completed, FALSE) AS is_completed,
                   ca.completed_at
            FROM checklist c
            JOIN checklist_item ci ON ci.checklist_id = c.id
            LEFT JOIN checklist_answer ca
              ON ca.checklist_item_id = ci.id
            LEFT JOIN field_participant fp
              ON fp.session_id = c.session_id
             AND fp.member_id = c.member_id
            WHERE c.session_id = :sessionId
            ORDER BY fp.started_at ASC NULLS LAST,
                     fp.id ASC NULLS LAST,
                     c.member_id ASC,
                     ci.display_order ASC,
                     ci.id ASC
            """;

    static final String FIELD_RECORDS_SQL = """
            SELECT fr.id AS source_id,
                   fr.session_id,
                   fr.checklist_item_id,
                   fr.author_id,
                   fr.source_type,
                   fr.text_content,
                   fr.stt_status,
                   fr.deleted_at,
                   fr.created_at AS recorded_at,
                   fr.updated_at,
                   fm.id AS photo_file_id,
                   fm.owner_id AS photo_owner_id,
                   fm.study_id AS photo_study_id,
                   fm.file_usage AS photo_file_usage,
                   fm.content_type AS photo_content_type,
                   fm.size_bytes AS photo_size_bytes,
                   fm.upload_status AS photo_upload_status,
                   fm.expires_at AS photo_expires_at,
                   fm.deleted_at AS photo_deleted_at
            FROM field_record fr
            LEFT JOIN file_meta fm ON fm.id = fr.photo_file_id
            WHERE fr.session_id = :sessionId
            ORDER BY fr.id ASC
            """;

    static final String INCOMPLETE_STT_JOBS_SQL = """
            SELECT sj.stt_id,
                   sj.member_id,
                   sj.checklist_item_id,
                   sj.status,
                   sj.retryable,
                   sj.fail_code,
                   sj.requested_at
            FROM stt_job sj
            WHERE sj.session_id = :sessionId
              AND sj.field_record_id IS NULL
            ORDER BY sj.stt_id ASC, sj.id ASC
            """;

    private final JdbcClient jdbcClient;

    public Optional<Snapshot> loadSnapshot(Long reportId) {
        Optional<ContextRow> optionalContext = jdbcClient.sql(CONTEXT_SQL)
                .param("reportId", reportId)
                .query((resultSet, rowNum) -> mapContext(resultSet))
                .optional();

        if (optionalContext.isEmpty()) {
            return Optional.empty();
        }

        ContextRow context = optionalContext.get();
        if (!context.sourceValid()) {
            return Optional.of(Snapshot.invalid(context));
        }

        List<ParticipantRow> participants = jdbcClient
                .sql(PARTICIPANTS_SQL)
                .param("sessionId", context.fieldSessionId())
                .query((resultSet, rowNum) -> mapParticipant(resultSet))
                .list();
        List<ChecklistItemRow> checklistItems = jdbcClient
                .sql(CHECKLIST_ITEMS_SQL)
                .param("sessionId", context.fieldSessionId())
                .query((resultSet, rowNum) -> mapChecklistItem(resultSet))
                .list();
        List<FieldRecordRow> fieldRecords = jdbcClient
                .sql(FIELD_RECORDS_SQL)
                .param("sessionId", context.fieldSessionId())
                .query((resultSet, rowNum) -> mapFieldRecord(
                        resultSet,
                        context.studyId()
                ))
                .list();
        List<IncompleteSttJobRow> incompleteSttJobs = jdbcClient
                .sql(INCOMPLETE_STT_JOBS_SQL)
                .param("sessionId", context.fieldSessionId())
                .query((resultSet, rowNum) -> mapIncompleteSttJob(resultSet))
                .list();

        return Optional.of(new Snapshot(
                context,
                participants,
                checklistItems,
                fieldRecords,
                incompleteSttJobs
        ));
    }

    private ContextRow mapContext(ResultSet resultSet) throws SQLException {
        return new ContextRow(
                resultSet.getLong("report_id"),
                resultSet.getLong("study_id"),
                resultSet.getLong("apartment_id"),
                resultSet.getLong("field_session_id"),
                FieldSessionStatus.valueOf(
                        resultSet.getString("session_status")
                ),
                offsetDateTime(resultSet, "session_started_at"),
                offsetDateTime(resultSet, "session_ended_at"),
                offsetDateTime(resultSet, "snapshot_at"),
                resultSet.getBoolean("source_valid")
        );
    }

    private ParticipantRow mapParticipant(ResultSet resultSet)
            throws SQLException {
        return new ParticipantRow(
                resultSet.getLong("field_participant_id"),
                resultSet.getLong("member_id"),
                FieldParticipantStatus.valueOf(
                        resultSet.getString("status")
                ),
                offsetDateTime(resultSet, "started_at"),
                offsetDateTime(resultSet, "ended_at")
        );
    }

    private ChecklistItemRow mapChecklistItem(ResultSet resultSet)
            throws SQLException {
        return new ChecklistItemRow(
                resultSet.getLong("checklist_id"),
                resultSet.getLong("checklist_item_id"),
                resultSet.getLong("member_id"),
                resultSet.getBoolean("is_fallback"),
                resultSet.getString("category"),
                resultSet.getString("title"),
                resultSet.getString("subtitle"),
                resultSet.getInt("display_order"),
                resultSet.getBoolean("is_completed"),
                offsetDateTime(resultSet, "completed_at")
        );
    }

    private FieldRecordRow mapFieldRecord(
            ResultSet resultSet,
            Long studyId
    ) throws SQLException {
        Long authorId = resultSet.getLong("author_id");
        FieldRecordSourceType sourceType = FieldRecordSourceType.valueOf(
                resultSet.getString("source_type")
        );
        PhotoFileRow photoFile = mapPhotoFile(
                resultSet,
                sourceType,
                authorId,
                studyId
        );

        return new FieldRecordRow(
                resultSet.getLong("source_id"),
                resultSet.getLong("session_id"),
                resultSet.getLong("checklist_item_id"),
                authorId,
                sourceType,
                resultSet.getString("text_content"),
                nullableEnum(
                        SttStatus.class,
                        resultSet.getString("stt_status")
                ),
                photoFile,
                offsetDateTime(resultSet, "deleted_at"),
                offsetDateTime(resultSet, "recorded_at"),
                offsetDateTime(resultSet, "updated_at")
        );
    }

    private PhotoFileRow mapPhotoFile(
            ResultSet resultSet,
            FieldRecordSourceType sourceType,
            Long authorId,
            Long studyId
    ) throws SQLException {
        Long fileId = nullableLong(resultSet, "photo_file_id");
        Long ownerId = nullableLong(resultSet, "photo_owner_id");
        Long photoStudyId = nullableLong(resultSet, "photo_study_id");
        String fileUsage = resultSet.getString("photo_file_usage");

        if (sourceType != FieldRecordSourceType.PHOTO
                || fileId == null
                || !Objects.equals(ownerId, authorId)
                || !Objects.equals(photoStudyId, studyId)
                || !"FIELD_PHOTO".equals(fileUsage)) {
            return null;
        }

        return new PhotoFileRow(
                fileId,
                resultSet.getString("photo_content_type"),
                nullableLong(resultSet, "photo_size_bytes"),
                UploadStatus.valueOf(
                        resultSet.getString("photo_upload_status")
                ),
                offsetDateTime(resultSet, "photo_expires_at"),
                offsetDateTime(resultSet, "photo_deleted_at")
        );
    }

    private IncompleteSttJobRow mapIncompleteSttJob(ResultSet resultSet)
            throws SQLException {
        return new IncompleteSttJobRow(
                resultSet.getString("stt_id"),
                resultSet.getLong("member_id"),
                resultSet.getLong("checklist_item_id"),
                SttStatus.valueOf(resultSet.getString("status")),
                resultSet.getBoolean("retryable"),
                resultSet.getString("fail_code"),
                offsetDateTime(resultSet, "requested_at")
        );
    }

    private OffsetDateTime offsetDateTime(
            ResultSet resultSet,
            String columnName
    ) throws SQLException {
        return resultSet.getObject(columnName, OffsetDateTime.class);
    }

    private Long nullableLong(ResultSet resultSet, String columnName)
            throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private <E extends Enum<E>> E nullableEnum(
            Class<E> enumType,
            String value
    ) {
        return value == null ? null : Enum.valueOf(enumType, value);
    }

    public record Snapshot(
            ContextRow context,
            List<ParticipantRow> participants,
            List<ChecklistItemRow> checklistItems,
            List<FieldRecordRow> fieldRecords,
            List<IncompleteSttJobRow> incompleteSttJobs
    ) {

        static Snapshot invalid(ContextRow context) {
            return new Snapshot(
                    context,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }

    public record ContextRow(
            Long reportId,
            Long studyId,
            Long apartmentId,
            Long fieldSessionId,
            FieldSessionStatus sessionStatus,
            OffsetDateTime sessionStartedAt,
            OffsetDateTime sessionEndedAt,
            OffsetDateTime snapshotAt,
            boolean sourceValid
    ) {
    }

    public record ParticipantRow(
            Long fieldParticipantId,
            Long memberId,
            FieldParticipantStatus status,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt
    ) {
    }

    public record ChecklistItemRow(
            Long checklistId,
            Long checklistItemId,
            Long memberId,
            boolean fallback,
            String category,
            String title,
            String subtitle,
            int displayOrder,
            boolean completed,
            OffsetDateTime completedAt
    ) {
    }

    public record FieldRecordRow(
            Long sourceId,
            Long sessionId,
            Long checklistItemId,
            Long authorId,
            FieldRecordSourceType sourceType,
            String textContent,
            SttStatus sttStatus,
            PhotoFileRow photoFile,
            OffsetDateTime deletedAt,
            OffsetDateTime recordedAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record PhotoFileRow(
            Long fileId,
            String contentType,
            Long sizeBytes,
            UploadStatus uploadStatus,
            OffsetDateTime expiresAt,
            OffsetDateTime deletedAt
    ) {
    }

    public record IncompleteSttJobRow(
            String sttId,
            Long memberId,
            Long checklistItemId,
            SttStatus status,
            boolean retryable,
            String failCode,
            OffsetDateTime requestedAt
    ) {
    }
}
