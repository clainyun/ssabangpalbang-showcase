package com.ssafy.ssabangpalbang.media.dto.response;

import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;

import java.time.Instant;
import java.util.Map;

public record MediaPrepareResponse(
        Long fileId,
        FileUsage fileUsage,
        String uploadUrl,
        String method,
        Map<String, String> requiredHeaders,
        UploadStatus uploadStatus,
        Instant uploadUrlExpiresAt,
        Instant fileExpiresAt
) {
}
