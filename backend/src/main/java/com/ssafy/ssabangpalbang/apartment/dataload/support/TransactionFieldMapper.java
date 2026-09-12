package com.ssafy.ssabangpalbang.apartment.dataload.support;

import com.ssafy.ssabangpalbang.apartment.dataload.dto.AptTradeItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;

public final class TransactionFieldMapper {

    private TransactionFieldMapper() {
    }

    public static Long price(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long price = Long.parseLong(value.replace(",", "").replaceAll("\\s+", ""));
            return price < 0 ? null : price;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static BigDecimal exclusiveArea(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static Integer floor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static LocalDate dealDate(String year, String month, String day) {
        if (year == null || month == null || day == null) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(year.trim()),
                    Integer.parseInt(month.trim()),
                    Integer.parseInt(day.trim())
            );
        } catch (NumberFormatException | DateTimeException exception) {
            return null;
        }
    }

    public static boolean canceled(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static String dedupKey(AptTradeItem item) {
        LocalDate date = dealDate(item.dealYear(), item.dealMonth(), item.dealDay());
        if (date == null) {
            return "";
        }
        return value(item.sigunguCode()) + "|" + value(item.legalDongCode()) + "|"
                + value(item.aptSeq()) + "|" + date + "|" + value(item.exclusiveArea()) + "|"
                + value(item.floor()) + "|" + normalizedAmount(item.dealAmount());
    }

    private static String normalizedAmount(String value) {
        return value == null ? "" : value.replace(",", "").replaceAll("\\s+", "");
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
