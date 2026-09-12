package com.ssafy.ssabangpalbang.member.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.util.List;
import java.util.Map;

public enum MemberPublicStudyStatus {
    ACTIVE,
    COMPLETED,
    ALL;

    private static final List<String> ALLOWED_VALUES = List.of(
            "ACTIVE",
            "COMPLETED",
            "ALL"
    );

    public static MemberPublicStudyStatus from(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field", "studyStatus",
                            "allowedValues", ALLOWED_VALUES
                    )
            );
        }
    }
}
