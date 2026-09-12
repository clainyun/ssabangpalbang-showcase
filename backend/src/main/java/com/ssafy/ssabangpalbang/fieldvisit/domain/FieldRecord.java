package com.ssafy.ssabangpalbang.fieldvisit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * 체크리스트 항목에 연결된 현장 기록이다(BE-015).
 *
 * <p>API 응답의 {@code sourceId}는 이 Entity의 {@code id}다.
 * STT 행은 BE-016이 JDBC로 생성할 수 있으며, BE-015는 TEXT/PHOTO 생성과
 * 조회·수정·삭제(soft delete)를 담당한다.</p>
 */
@Entity
@Table(name = "field_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldRecord {

    public static final int TEXT_CONTENT_MAX_LENGTH = 2000;
    public static final String STT_STATUS_PENDING = "PENDING";
    public static final String STT_STATUS_PROCESSING = "PROCESSING";
    public static final String STT_STATUS_DONE = "DONE";
    public static final String STT_STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "checklist_item_id", nullable = false)
    private Long checklistItemId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private FieldRecordSourceType sourceType;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "photo_file_id")
    private Long photoFileId;

    @Column(name = "stt_status", length = 20)
    private String sttStatus;

    @Column(name = "client_request_id", nullable = false, unique = true, length = 100)
    private String clientRequestId;

    @Column(name = "request_fingerprint", length = 128)
    private String requestFingerprint;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FieldRecord createText(
            Long sessionId,
            Long checklistItemId,
            Long authorId,
            String textContent,
            String clientRequestId,
            String requestFingerprint,
            Instant now
    ) {
        FieldRecord record = base(
                sessionId,
                checklistItemId,
                authorId,
                FieldRecordSourceType.TEXT,
                clientRequestId,
                requestFingerprint,
                now
        );
        record.textContent = requireText(textContent);
        return record;
    }

    public static FieldRecord createPhoto(
            Long sessionId,
            Long checklistItemId,
            Long authorId,
            Long photoFileId,
            String clientRequestId,
            String requestFingerprint,
            Instant now
    ) {
        FieldRecord record = base(
                sessionId,
                checklistItemId,
                authorId,
                FieldRecordSourceType.PHOTO,
                clientRequestId,
                requestFingerprint,
                now
        );
        record.photoFileId = Objects.requireNonNull(photoFileId);
        return record;
    }

    /**
     * BE-016이 JDBC로 넣은 STT 행을 테스트·서비스 검증에서 재구성할 때 사용한다.
     * 공개 POST 생성 경로에서는 호출하지 않는다.
     */
    public static FieldRecord reconstructStt(
            Long sessionId,
            Long checklistItemId,
            Long authorId,
            String textContent,
            String sttStatus,
            String clientRequestId,
            Instant now
    ) {
        FieldRecord record = base(
                sessionId,
                checklistItemId,
                authorId,
                FieldRecordSourceType.STT,
                clientRequestId,
                null,
                now
        );
        record.textContent = textContent;
        record.sttStatus = sttStatus;
        return record;
    }

    public void updateTextContent(String textContent, Instant now) {
        ensureNotDeleted();
        this.textContent = requireText(textContent);
        touch(now);
    }

    public void updatePhotoFileId(Long photoFileId, Instant now) {
        ensureNotDeleted();
        if (sourceType != FieldRecordSourceType.PHOTO) {
            throw new IllegalStateException("PHOTO 기록만 사진을 교체할 수 있습니다.");
        }
        this.photoFileId = Objects.requireNonNull(photoFileId);
        touch(now);
    }

    public void updateChecklistItemId(Long checklistItemId, Instant now) {
        ensureNotDeleted();
        this.checklistItemId = Objects.requireNonNull(checklistItemId);
        touch(now);
    }

    public void softDelete(Instant now) {
        if (deletedAt != null) {
            return;
        }
        this.deletedAt = Objects.requireNonNull(now);
        touch(now);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isSttInProgress() {
        return sourceType == FieldRecordSourceType.STT
                && (STT_STATUS_PENDING.equals(sttStatus)
                || STT_STATUS_PROCESSING.equals(sttStatus));
    }

    public boolean isSttDone() {
        return sourceType == FieldRecordSourceType.STT
                && STT_STATUS_DONE.equals(sttStatus);
    }

    public boolean isSttFailed() {
        return sourceType == FieldRecordSourceType.STT
                && STT_STATUS_FAILED.equals(sttStatus);
    }

    public boolean matchesFingerprint(String fingerprint) {
        return requestFingerprint != null
                && requestFingerprint.equals(fingerprint);
    }

    private static FieldRecord base(
            Long sessionId,
            Long checklistItemId,
            Long authorId,
            FieldRecordSourceType sourceType,
            String clientRequestId,
            String requestFingerprint,
            Instant now
    ) {
        FieldRecord record = new FieldRecord();
        record.sessionId = Objects.requireNonNull(sessionId);
        record.checklistItemId = Objects.requireNonNull(checklistItemId);
        record.authorId = Objects.requireNonNull(authorId);
        record.sourceType = Objects.requireNonNull(sourceType);
        record.clientRequestId = Objects.requireNonNull(clientRequestId);
        record.requestFingerprint = requestFingerprint;
        record.createdAt = Objects.requireNonNull(now);
        record.updatedAt = now;
        return record;
    }

    private static String requireText(String textContent) {
        if (textContent == null) {
            throw new IllegalArgumentException("textContent is required");
        }
        String trimmed = textContent.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("textContent must not be blank");
        }
        if (trimmed.length() > TEXT_CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "textContent must be <= " + TEXT_CONTENT_MAX_LENGTH
            );
        }
        return trimmed;
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new IllegalStateException("deleted field_record cannot be updated");
        }
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now);
    }
}
