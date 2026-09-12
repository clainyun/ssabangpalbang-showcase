package com.ssafy.ssabangpalbang.fieldvisit.stt.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SttStatusQueryRepository {

    private final JdbcClient jdbcClient;

    public Optional<FieldRecordView> findFieldRecord(Long fieldRecordId) {
        return jdbcClient.sql("""
                        SELECT id,
                               checklist_item_id,
                               source_type,
                               stt_status,
                               text_content
                        FROM field_record
                        WHERE id = :fieldRecordId
                        """)
                .param("fieldRecordId", fieldRecordId)
                .query((resultSet, rowNum) -> new FieldRecordView(
                        resultSet.getLong("id"),
                        resultSet.getLong("checklist_item_id"),
                        resultSet.getString("source_type"),
                        resultSet.getString("stt_status"),
                        resultSet.getString("text_content")
                ))
                .optional();
    }

    public Optional<AudioFileStateView> findAudioFileState(Long audioFileId) {
        return jdbcClient.sql("""
                        SELECT file_usage,
                               upload_status,
                               deleted_at,
                               expires_at
                        FROM file_meta
                        WHERE id = :audioFileId
                        """)
                .param("audioFileId", audioFileId)
                .query((resultSet, rowNum) -> new AudioFileStateView(
                        resultSet.getString("file_usage"),
                        resultSet.getString("upload_status"),
                        resultSet.getObject(
                                "deleted_at",
                                OffsetDateTime.class
                        ),
                        resultSet.getObject(
                                "expires_at",
                                OffsetDateTime.class
                        )
                ))
                .optional();
    }

    public record FieldRecordView(
            Long fieldRecordId,
            Long checklistItemId,
            String sourceType,
            String sttStatus,
            String textContent
    ) {
    }

    public record AudioFileStateView(
            String fileUsage,
            String uploadStatus,
            OffsetDateTime deletedAt,
            OffsetDateTime expiresAt
    ) {
    }
}
