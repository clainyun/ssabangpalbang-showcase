package com.ssafy.ssabangpalbang.auth.domain;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum SocialProvider {
    NAVER,
    KAKAO;

    private static final List<String> ALLOWED_VALUES = List.of(
            NAVER.name(),
            KAKAO.name()
    );

    public static SocialProvider from(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    ErrorCode.AUTH_SOCIAL_PROVIDER_INVALID,
                    Map.of("allowedValues", ALLOWED_VALUES)
            );
        }
    }
}
