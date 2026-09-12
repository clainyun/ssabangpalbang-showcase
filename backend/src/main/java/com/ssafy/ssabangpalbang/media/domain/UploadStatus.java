package com.ssafy.ssabangpalbang.media.domain;

/**
 * file_meta.upload_status 값이다. BE-011은 CHAT_IMAGE의 COMPLETED 여부만 검사한다.
 */
public enum UploadStatus {
    PENDING,
    COMPLETED,
    FAILED,
    DELETED
}
