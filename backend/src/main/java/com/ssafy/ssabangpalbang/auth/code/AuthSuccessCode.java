package com.ssafy.ssabangpalbang.auth.code;

import com.ssafy.ssabangpalbang.global.response.ResponseCode;

public enum AuthSuccessCode implements ResponseCode {

    SIGNUP_SUCCESS(
            "AUTH_SIGNUP_SUCCESS",
            "회원가입이 완료되었습니다."
    ),
    LOGIN_SUCCESS(
            "AUTH_LOGIN_SUCCESS",
            "로그인에 성공했습니다."
    ),
    SOCIAL_LOGIN_SUCCESS(
            "AUTH_SOCIAL_LOGIN_SUCCESS",
            "간편 로그인에 성공했습니다."
    ),
    SOCIAL_SIGNUP_REQUIRED(
            "AUTH_SOCIAL_SIGNUP_REQUIRED",
            "소셜 회원가입이 필요합니다."
    ),
    SOCIAL_SIGNUP_SUCCESS(
            "AUTH_SOCIAL_SIGNUP_SUCCESS",
            "소셜 회원가입이 완료되었습니다."
    ),
    TOKEN_REISSUE_SUCCESS(
            "AUTH_TOKEN_REISSUE_SUCCESS",
            "토큰이 재발급되었습니다."
    ),
    LOGOUT_SUCCESS(
            "AUTH_LOGOUT_SUCCESS",
            "로그아웃되었습니다."
    ),
    PASSWORD_RESET_REQUESTED(
            "AUTH_PASSWORD_RESET_REQUESTED",
            "입력하신 이메일로 재설정 코드를 보냈어요."
    ),
    PASSWORD_RESET_SUCCESS(
            "AUTH_PASSWORD_RESET_SUCCESS",
            "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요."
    ),
    PASSWORD_RESET_CODE_VERIFY_SUCCESS(
            "AUTH_PASSWORD_RESET_CODE_VERIFY_SUCCESS",
            "인증 코드 확인이 완료되었습니다."
    ),
    EMAIL_AVAILABILITY_SUCCESS(
            "AUTH_EMAIL_AVAILABILITY_SUCCESS",
            "이메일 사용 가능 여부를 확인했습니다."
    ),
    NICKNAME_AVAILABILITY_SUCCESS(
            "AUTH_NICKNAME_AVAILABILITY_SUCCESS",
            "닉네임 사용 가능 여부를 확인했습니다."
    );

    private final String code;
    private final String message;

    AuthSuccessCode(String code, String message) {
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
