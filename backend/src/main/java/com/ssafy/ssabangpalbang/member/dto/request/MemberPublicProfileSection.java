package com.ssafy.ssabangpalbang.member.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.util.List;
import java.util.Map;

public enum MemberPublicProfileSection {
    STUDIES,
    REPORTS,
    FOLLOWINGS;

    private static final List<String> ALLOWED_VALUES = List.of(
            "STUDIES",
            "REPORTS",
            "FOLLOWINGS"
    );

    public static MemberPublicProfileSection from(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field", "section",
                            "allowedValues", ALLOWED_VALUES
                    )
            );
        }
    }
}
