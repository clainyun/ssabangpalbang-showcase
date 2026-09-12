package com.ssafy.ssabangpalbang.home.code;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum HomeResponseCode implements ResponseCode {

    HOME_RETRIEVED(
            "HOME_SUCCESS",
            "홈 화면 정보 조회에 성공했습니다."
    );

    private final String code;
    private final String message;

    HomeResponseCode(String code, String message) {
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
