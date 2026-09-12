package com.ssafy.ssabangpalbang.community.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.util.List;
import java.util.Map;

public enum PostSort {
    LATEST,
    HOT;

    public static PostSort parse(String value) {
        try {
            return PostSort.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    ErrorCode.POST_SORT_INVALID,
                    Map.of(
                            "field", "sort",
                            "allowedValues", List.of("HOT", "LATEST")
                    )
            );
        }
    }
}
