package com.ssafy.ssabangpalbang.media.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;

import java.util.Map;
import java.util.Set;

final class MediaFilePolicy {

    private static final long PHOTO_MAX_BYTES = 10L * 1024 * 1024;
    private static final long POST_MAX_BYTES = 20L * 1024 * 1024;
    private static final long AUDIO_MAX_BYTES = 50L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> AUDIO_TYPES = Set.of(
            "audio/m4a",
            "audio/mp4",
            "audio/mpeg",
            "audio/wav",
            "audio/x-wav",
            "audio/webm",
            "audio/aac",
            "audio/x-m4a"
    );
    private static final Set<String> POST_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf"
    );

    private MediaFilePolicy() {
    }

    static void validate(
            FileUsage usage,
            String contentType,
            long sizeBytes
    ) {
        if (usage == null) {
            throw new BusinessException(
                    ErrorCode.MEDIA_FILE_USAGE_INVALID
            );
        }
        Set<String> allowedTypes = switch (usage) {
            case STT_AUDIO -> AUDIO_TYPES;
            case POST_ATTACHMENT -> POST_TYPES;
            case FIELD_PHOTO, CHAT_IMAGE -> IMAGE_TYPES;
        };
        if (contentType == null
                || !allowedTypes.contains(contentType.toLowerCase())) {
            throw new BusinessException(
                    ErrorCode.MEDIA_CONTENT_TYPE_INVALID,
                    Map.of(
                            "fileUsage", usage,
                            "contentType",
                            contentType == null ? "" : contentType
                    )
            );
        }
        long maxBytes = switch (usage) {
            case STT_AUDIO -> AUDIO_MAX_BYTES;
            case POST_ATTACHMENT -> POST_MAX_BYTES;
            case FIELD_PHOTO, CHAT_IMAGE -> PHOTO_MAX_BYTES;
        };
        if (sizeBytes < 1 || sizeBytes > maxBytes) {
            throw new BusinessException(
                    ErrorCode.MEDIA_FILE_SIZE_EXCEEDED,
                    Map.of("maxBytes", maxBytes)
            );
        }
    }

    static boolean requiresStudy(FileUsage usage) {
        return usage != FileUsage.POST_ATTACHMENT;
    }
}
