package com.ssafy.ssabangpalbang.apartment.dataload.support;

import java.math.BigDecimal;

public final class ApartmentFieldMapper {

    private ApartmentFieldMapper() {
    }

    public static Integer householdCount(String value) {
        Integer parsed = nonNegativeInteger(value);
        return parsed == null || parsed == 0 ? null : parsed;
    }

    public static String completionYearMonth(String value) {
        if (value == null || !value.matches("\\d{8}")) {
            return null;
        }
        int month = Integer.parseInt(value.substring(4, 6));
        return month >= 1 && month <= 12 ? value.substring(0, 4) + "-" + value.substring(4, 6) : null;
    }

    public static Integer parkingSpaceCount(String aboveGround, String underground) {
        Integer above = nonNegativeInteger(aboveGround);
        Integer below = nonNegativeInteger(underground);
        if (above == null && below == null) {
            return null;
        }
        if ((aboveGround != null && !aboveGround.isBlank() && above == null)
                || (underground != null && !underground.isBlank() && below == null)) {
            return null;
        }
        return (above == null ? 0 : above) + (below == null ? 0 : below);
    }

    public static String districtCode(String bjdCode) {
        return bjdCode != null && bjdCode.length() >= 5 ? bjdCode.substring(0, 5) : null;
    }

    public static String legalDongCode(String bjdCode) {
        return bjdCode;
    }

    private static Integer nonNegativeInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = new BigDecimal(value.trim()).intValueExact();
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }
}
