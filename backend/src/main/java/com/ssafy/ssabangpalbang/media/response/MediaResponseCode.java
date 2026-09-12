package com.ssafy.ssabangpalbang.media.response;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum MediaResponseCode implements ResponseCode {

    MEDIA_PRESIGNED_URL_ISSUED(
            "MEDIA_PRESIGNED_URL_ISSUED",
            "업로드 URL을 발급했습니다."
    ),
    MEDIA_UPLOAD_COMPLETED(
            "MEDIA_UPLOAD_COMPLETED",
            "업로드가 완료 처리되었습니다."
    ),
    MEDIA_UPLOAD_ALREADY_COMPLETED(
            "MEDIA_UPLOAD_ALREADY_COMPLETED",
            "이미 업로드 완료 처리된 파일입니다."
    ),
    MEDIA_FILE_GET_SUCCESS(
            "MEDIA_FILE_GET_SUCCESS",
            "파일 정보 조회에 성공했습니다."
    ),
    MEDIA_FILE_DELETE_SUCCESS(
            "MEDIA_FILE_DELETE_SUCCESS",
            "파일을 삭제했습니다."
    );

    private final String code;
    private final String message;

    MediaResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
