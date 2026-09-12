package com.ssafy.ssabangpalbang.fieldvisit.stt.repository;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class SttResultPersistenceRepository {

    private final JdbcClient jdbcClient;

    public Long createFieldRecord(
            SttJob job,
            String textContent,
            OffsetDateTime completedAt
    ) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO field_record(
                            session_id,
                            checklist_item_id,
                            author_id,
                            source_type,
                            text_content,
                            stt_status,
                            client_request_id,
                            created_at,
                            updated_at
                        ) VALUES (
                            :sessionId,
                            :checklistItemId,
                            :authorId,
                            'STT',
                            :textContent,
                            'DONE',
                            :clientRequestId,
                            :completedAt,
                            :completedAt
                        )
                        """)
                .param("sessionId", job.getSessionId())
                .param("checklistItemId", job.getChecklistItemId())
                .param("authorId", job.getMemberId())
                .param("textContent", textContent)
                .param("clientRequestId", "STT:" + job.getSttId())
                .param("completedAt", completedAt)
                .update(keyHolder, "id");

        Long fieldRecordId = keyHolder.getKeyAs(Long.class);
        if (fieldRecordId == null) {
            throw new IllegalStateException(
                    "생성된 STT 현장 기록 ID를 확인할 수 없습니다."
            );
        }
        return fieldRecordId;
    }

    public String markAudioDeleted(
            Long audioFileId,
            OffsetDateTime deletedAt
    ) {
        String objectKey = jdbcClient.sql("""
                        SELECT s3_key
                        FROM file_meta
                        WHERE id = :audioFileId
                        FOR UPDATE
                        """)
                .param("audioFileId", audioFileId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "삭제할 STT 음성 원본을 찾을 수 없습니다. audioFileId="
                                + audioFileId
                ));

        int updated = jdbcClient.sql("""
                        UPDATE file_meta
                        SET upload_status = 'DELETED',
                            deleted_at = COALESCE(deleted_at, :deletedAt)
                        WHERE id = :audioFileId
                        """)
                .param("deletedAt", deletedAt)
                .param("audioFileId", audioFileId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException(
                    "STT 음성 원본 접근 차단에 실패했습니다. audioFileId="
                            + audioFileId
            );
        }
        return objectKey;
    }
}
