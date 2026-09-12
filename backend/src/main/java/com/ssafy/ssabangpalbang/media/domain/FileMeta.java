package com.ssafy.ssabangpalbang.media.domain;

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

/**
 * 업로드된 파일의 메타데이터다.
 *
 * <p>BE-011 범위에서는 이미 업로드가 완료된 CHAT_IMAGE 파일을 검증하고
 * Presigned GET URL을 발급하는 데에만 사용하는 조회 전용 모델이다.
 * Presigned PUT URL 발급, 업로드 완료 처리 등 쓰기 기능은 Media 도메인에서
 * 별도로 구현하며 이 커밋에서는 다루지 않는다.</p>
 */
@Getter
@Entity
@Table(name = "file_meta")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "study_id")
    private Long studyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_usage", nullable = false, length = 30)
    private FileUsage fileUsage;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "s3_key", nullable = false, unique = true, length = 500)
    private String s3Key;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    private UploadStatus uploadStatus;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FileMeta pending(
            Long ownerId,
            Long studyId,
            FileUsage fileUsage,
            String originalName,
            String uploadKey,
            String contentType,
            Long sizeBytes,
            Instant expiresAt,
            Instant createdAt
    ) {
        FileMeta file = new FileMeta();
        file.ownerId = ownerId;
        file.studyId = studyId;
        file.fileUsage = fileUsage;
        file.originalName = originalName;
        file.s3Key = uploadKey;
        file.contentType = contentType;
        file.sizeBytes = sizeBytes;
        file.uploadStatus = UploadStatus.PENDING;
        file.expiresAt = expiresAt;
        file.createdAt = createdAt;
        return file;
    }

    public void complete(
            String finalKey,
            String verifiedContentType,
            long verifiedSizeBytes,
            Instant finalExpiresAt
    ) {
        this.s3Key = finalKey;
        this.contentType = verifiedContentType;
        this.sizeBytes = verifiedSizeBytes;
        this.expiresAt = finalExpiresAt;
        this.uploadStatus = UploadStatus.COMPLETED;
    }

    public void fail() {
        this.uploadStatus = UploadStatus.FAILED;
    }

    public void markDeleted(Instant now) {
        this.uploadStatus = UploadStatus.DELETED;
        this.deletedAt = now;
    }
}
