package com.ssafy.ssabangpalbang.apartment.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

public enum TransactionSort {

    DEAL_DATE_DESC("dealDate", Sort.Direction.DESC),
    DEAL_DATE_ASC("dealDate", Sort.Direction.ASC),
    PRICE_DESC("price", Sort.Direction.DESC),
    PRICE_ASC("price", Sort.Direction.ASC),
    AREA_DESC("exclusiveArea", Sort.Direction.DESC),
    AREA_ASC("exclusiveArea", Sort.Direction.ASC);

    private static final List<String> ALLOWED_VALUES =
            List.of("DEAL_DATE_DESC", "DEAL_DATE_ASC", "PRICE_DESC", "PRICE_ASC", "AREA_DESC", "AREA_ASC");

    private final String property;
    private final Sort.Direction direction;

    TransactionSort(String property, Sort.Direction direction) {
        this.property = property;
        this.direction = direction;
    }

    public Sort toSort() {
        return Sort.by(
                new Sort.Order(direction, property),
                new Sort.Order(direction, "id")
        );
    }

    public static TransactionSort from(String value) {
        if (value == null) {
            return DEAL_DATE_DESC;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_TRANSACTION_SORT_INVALID,
                    Map.of("field", "sort", "allowedValues", ALLOWED_VALUES)
            );
        }
    }
}
