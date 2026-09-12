package com.ssafy.ssabangpalbang.media.service;

import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;

import java.time.Instant;

public record MediaFileSnapshot(
        Long fileId,
        Long ownerId,
        FileUsage fileUsage,
        String originalName,
        String contentType,
        UploadStatus uploadStatus,
        Instant expiresAt,
        Instant deletedAt
) {
}
