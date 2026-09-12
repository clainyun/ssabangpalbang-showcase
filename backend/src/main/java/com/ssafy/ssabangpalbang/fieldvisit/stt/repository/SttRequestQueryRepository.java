package com.ssafy.ssabangpalbang.fieldvisit.stt.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SttRequestQueryRepository {

    private final JdbcClient jdbcClient;

    public Optional<FieldSessionView> findFieldSessionByStudyId(Long studyId) {
        return findFieldSessionByStudyId(studyId, "");
    }

    public Optional<FieldSessionView> findFieldSessionByStudyIdForUpdate(
            Long studyId
    ) {
        return findFieldSessionByStudyId(studyId, " FOR UPDATE");
    }

    private Optional<FieldSessionView> findFieldSessionByStudyId(
            Long studyId,
            String lockClause
    ) {
        return jdbcClient.sql("""
                        SELECT id, status
                        FROM field_session
                        WHERE study_id = :studyId
                        """ + lockClause)
                .param("studyId", studyId)
                .query((resultSet, rowNum) -> new FieldSessionView(
                        resultSet.getLong("id"),
                        resultSet.getString("status")
                ))
                .optional();
    }

    public Optional<String> findParticipantStatus(
            Long sessionId,
            Long memberId
    ) {
        return findParticipantStatus(sessionId, memberId, "");
    }

    public Optional<String> findParticipantStatusForUpdate(
            Long sessionId,
            Long memberId
    ) {
        return findParticipantStatus(sessionId, memberId, " FOR UPDATE");
    }

    private Optional<String> findParticipantStatus(
            Long sessionId,
            Long memberId,
            String lockClause
    ) {
        return jdbcClient.sql("""
                        SELECT status
                        FROM field_participant
                        WHERE session_id = :sessionId
                          AND member_id = :memberId
                        """ + lockClause)
                .param("sessionId", sessionId)
                .param("memberId", memberId)
                .query(String.class)
                .optional();
    }

    public Optional<ChecklistItemView> findChecklistItem(Long checklistItemId) {
        return findChecklistItem(checklistItemId, "");
    }

    public Optional<ChecklistItemView> findChecklistItemForUpdate(
            Long checklistItemId
    ) {
        return findChecklistItem(checklistItemId, " FOR UPDATE");
    }

    private Optional<ChecklistItemView> findChecklistItem(
            Long checklistItemId,
            String lockClause
    ) {
        return jdbcClient.sql("""
                        SELECT ci.id,
                               c.member_id,
                               c.session_id,
                               fs.study_id
                        FROM checklist_item ci
                        JOIN checklist c ON c.id = ci.checklist_id
                        JOIN field_session fs ON fs.id = c.session_id
                        WHERE ci.id = :checklistItemId
                        """ + lockClause)
                .param("checklistItemId", checklistItemId)
                .query((resultSet, rowNum) -> new ChecklistItemView(
                        resultSet.getLong("id"),
                        resultSet.getLong("member_id"),
                        resultSet.getLong("session_id"),
                        resultSet.getLong("study_id")
                ))
                .optional();
    }

    public Optional<AudioFileView> findAudioFile(Long audioFileId) {
        return findAudioFile(audioFileId, "");
    }

    public Optional<AudioFileView> findAudioFileForUpdate(Long audioFileId) {
        return findAudioFile(audioFileId, " FOR UPDATE");
    }

    private Optional<AudioFileView> findAudioFile(
            Long audioFileId,
            String lockClause
    ) {
        return jdbcClient.sql("""
                        SELECT id,
                               owner_id,
                               study_id,
                               file_usage,
                               upload_status,
                               deleted_at,
                               expires_at,
                               s3_key,
                               content_type
                        FROM file_meta
                        WHERE id = :audioFileId
                        """ + lockClause)
                .param("audioFileId", audioFileId)
                .query((resultSet, rowNum) -> new AudioFileView(
                        resultSet.getLong("id"),
                        resultSet.getLong("owner_id"),
                        nullableLong(resultSet, "study_id"),
                        resultSet.getString("file_usage"),
                        resultSet.getString("upload_status"),
                        resultSet.getObject(
                                "deleted_at",
                                OffsetDateTime.class
                        ),
                        resultSet.getObject(
                                "expires_at",
                                OffsetDateTime.class
                        ),
                        resultSet.getString("s3_key"),
                        resultSet.getString("content_type")
                ))
                .optional();
    }

    private Long nullableLong(
            java.sql.ResultSet resultSet,
            String columnName
    ) throws java.sql.SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    public record FieldSessionView(
            Long sessionId,
            String status
    ) {
    }

    public record ChecklistItemView(
            Long checklistItemId,
            Long memberId,
            Long sessionId,
            Long studyId
    ) {
    }

    public record AudioFileView(
            Long audioFileId,
            Long ownerId,
            Long studyId,
            String fileUsage,
            String uploadStatus,
            OffsetDateTime deletedAt,
            OffsetDateTime expiresAt,
            String objectKey,
            String contentType
    ) {
    }
}
