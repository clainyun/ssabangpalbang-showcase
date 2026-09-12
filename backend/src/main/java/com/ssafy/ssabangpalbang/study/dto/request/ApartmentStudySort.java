package com.ssafy.ssabangpalbang.study.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import java.util.List;
import java.util.Map;

public enum ApartmentStudySort {
    SCHEDULE_ASC("sch.start_at ASC NULLS LAST, s.id ASC"),
    CREATED_DESC("s.created_at DESC, s.id DESC"),
    REMAINING_CAPACITY_DESC("(s.capacity - mc.cnt) DESC, s.id DESC");

    private static final List<String> ALLOWED_VALUES =
            List.of("SCHEDULE_ASC", "CREATED_DESC", "REMAINING_CAPACITY_DESC");

    private final String orderByClause;

    ApartmentStudySort(String orderByClause) {
        this.orderByClause = orderByClause;
    }

    public String orderByClause() {
        return orderByClause;
    }

    public static ApartmentStudySort from(String value) {
        if (value == null) {
            return SCHEDULE_ASC;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_STUDY_SORT_INVALID,
                    Map.of("field", "sort", "allowedValues", ALLOWED_VALUES)
            );
        }
    }
}
