package com.ssafy.ssabangpalbang.community.dto.response;

import java.time.OffsetDateTime;

public record PostAttachmentResponse(
        Long fileId,
        String originalName,
        String contentType,
        String fileUrl,
        int displayOrder,
        boolean available,
        OffsetDateTime expiresAt
) {
}
