package com.ssafy.ssabangpalbang.apartment.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.util.Map;

public record ApartmentReportSearchCondition(
        Long apartmentId,
        int page,
        int size
) {
    public static ApartmentReportSearchCondition of(
            Long apartmentId,
            Integer page,
            Integer size
    ) {
        if (apartmentId == null || apartmentId < 1) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_ID_INVALID,
                    Map.of("field", "apartmentId",
                            "reason", "아파트 ID는 1 이상의 숫자여야 합니다.")
            );
        }
        int normalizedPage = page == null ? 0 : page;
        int normalizedSize = size == null ? 20 : size;
        if (normalizedPage < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of("field", "page",
                            "reason", "페이지 번호는 0 이상이어야 합니다.")
            );
        }
        if (normalizedSize < 1 || normalizedSize > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of("field", "size",
                            "reason", "페이지 크기는 1 이상 100 이하이어야 합니다.")
            );
        }
        return new ApartmentReportSearchCondition(
                apartmentId,
                normalizedPage,
                normalizedSize
        );
    }
}
