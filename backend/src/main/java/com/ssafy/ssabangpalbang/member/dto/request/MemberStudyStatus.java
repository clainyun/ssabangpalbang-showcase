package com.ssafy.ssabangpalbang.member.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.util.List;
import java.util.Map;

public enum MemberStudyStatus {
    ALL,
    ACTIVE,
    IN_PROGRESS,
    COMPLETED;

    private static final List<String> ALLOWED_VALUES = List.of(
            "ACTIVE",
            "IN_PROGRESS",
            "COMPLETED",
            "ALL"
    );

    public static MemberStudyStatus from(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    ErrorCode.MEMBER_STUDY_STATUS_INVALID,
                    Map.of(
                            "field", "status",
                            "allowedValues", ALLOWED_VALUES
                    )
            );
        }
    }
}
