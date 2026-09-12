package com.ssafy.ssabangpalbang.community.response;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum CommentResponseCode implements ResponseCode {

    COMMENT_LIST_SUCCESS(
            "COMMENT_LIST_SUCCESS",
            "댓글 목록 조회에 성공했습니다."
    ),
    COMMENT_CREATE_SUCCESS(
            "COMMENT_CREATE_SUCCESS",
            "댓글이 작성되었습니다."
    ),
    COMMENT_UPDATE_SUCCESS(
            "COMMENT_UPDATE_SUCCESS",
            "댓글이 수정되었습니다."
    ),
    COMMENT_DELETE_SUCCESS(
            "COMMENT_DELETE_SUCCESS",
            "댓글이 삭제되었습니다."
    );

    private final String code;
    private final String message;

    CommentResponseCode(String code, String message) {
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
