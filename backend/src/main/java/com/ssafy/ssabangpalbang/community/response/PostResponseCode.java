package com.ssafy.ssabangpalbang.community.response;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum PostResponseCode implements ResponseCode {

    POST_LIST_SUCCESS(
            "POST_LIST_SUCCESS",
            "게시글 목록 조회에 성공했습니다."
    ),
    POST_CREATE_SUCCESS(
            "POST_CREATE_SUCCESS",
            "게시글이 작성되었습니다."
    ),
    POST_DETAIL_SUCCESS(
            "POST_DETAIL_SUCCESS",
            "게시글 상세 조회에 성공했습니다."
    ),
    POST_UPDATE_SUCCESS(
            "POST_UPDATE_SUCCESS",
            "게시글이 수정되었습니다."
    ),
    POST_DELETE_SUCCESS(
            "POST_DELETE_SUCCESS",
            "게시글이 삭제되었습니다."
    ),
    POST_LIKE_SUCCESS(
            "POST_LIKE_SUCCESS",
            "게시글에 좋아요를 등록했습니다."
    ),
    POST_LIKE_ALREADY_EXISTS(
            "POST_LIKE_ALREADY_EXISTS",
            "이미 좋아요한 게시글입니다."
    ),
    POST_UNLIKE_SUCCESS(
            "POST_UNLIKE_SUCCESS",
            "게시글 좋아요를 해제했습니다."
    ),
    POST_ALREADY_UNLIKED(
            "POST_ALREADY_UNLIKED",
            "이미 좋아요가 해제된 게시글입니다."
    );

    private final String code;
    private final String message;

    PostResponseCode(String code, String message) {
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
