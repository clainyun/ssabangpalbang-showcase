package com.ssafy.ssabangpalbang.media.dto.response;

import com.ssafy.ssabangpalbang.media.domain.UploadStatus;

import java.time.Instant;

public record MediaDeleteResponse(
        Long fileId,
        UploadStatus uploadStatus,
        Instant deletedAt
) {
}
