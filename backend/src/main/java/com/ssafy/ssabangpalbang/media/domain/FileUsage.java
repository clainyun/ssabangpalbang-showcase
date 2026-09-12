package com.ssafy.ssabangpalbang.media.domain;

/**
 * file_meta.file_usage 값이다. BE-011은 CHAT_IMAGE 검증에만 사용한다.
 */
public enum FileUsage {
    FIELD_PHOTO,
    STT_AUDIO,
    CHAT_IMAGE,
    POST_ATTACHMENT
}
