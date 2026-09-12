package com.ssafy.ssabangpalbang.media.dto.response;

import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrl;

import java.time.Instant;

public record MediaFileResponse(
        Long fileId,
        FileUsage fileUsage,
        String originalName,
        String contentType,
        Long sizeBytes,
        UploadStatus uploadStatus,
        String accessUrl,
        Instant accessUrlExpiresAt,
        Instant fileExpiresAt,
        Instant createdAt
) {

    public static MediaFileResponse from(
            FileMeta file,
            MediaAccessUrl accessUrl
    ) {
        return new MediaFileResponse(
                file.getId(),
                file.getFileUsage(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getUploadStatus(),
                accessUrl == null ? null : accessUrl.url(),
                accessUrl == null ? null : accessUrl.expiresAt(),
                file.getExpiresAt(),
                file.getCreatedAt()
        );
    }
}
