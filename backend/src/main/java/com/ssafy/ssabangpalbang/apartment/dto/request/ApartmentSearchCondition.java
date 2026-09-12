package com.ssafy.ssabangpalbang.apartment.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;

public record ApartmentSearchCondition(
        String keyword, String districtCode, String dongCode,
        Double latitude, Double longitude, int radiusMeters,
        int page, int size
) {
    public static ApartmentSearchCondition of(
            String keyword, String districtCode, String dongCode,
            Double latitude, Double longitude, Integer radiusMeters,
            Integer page, Integer size
    ) {
        int normalizedPage = page == null ? 0 : page;
        int normalizedSize = size == null ? 20 : size;
        if (normalizedPage < 0) {
            invalid("page", "페이지 번호는 0 이상이어야 합니다.");
        }
        if (normalizedSize < 1 || normalizedSize > 100) {
            invalid("size", "페이지 크기는 1 이상 100 이하이어야 합니다.");
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedDistrict = trimToNull(districtCode);
        String normalizedDong = trimToNull(dongCode);
        if (normalizedDistrict != null && !normalizedDistrict.matches("\\d{5}")) {
            throw withData(ErrorCode.REGION_DISTRICT_CODE_INVALID,
                    Map.of("districtCode", normalizedDistrict));
        }
        if (normalizedDong != null && !normalizedDong.matches("\\d{10}")) {
            throw withData(ErrorCode.REGION_DONG_CODE_INVALID,
                    Map.of("dongCode", normalizedDong));
        }
        if (normalizedDistrict != null && normalizedDong != null
                && !normalizedDong.startsWith(normalizedDistrict)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("districtCode", normalizedDistrict);
            data.put("dongCode", normalizedDong);
            throw withData(ErrorCode.REGION_CODE_MISMATCH, data);
        }
        if ((latitude == null) != (longitude == null)
                || latitude != null && (!Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180)) {
            throw withData(ErrorCode.APARTMENT_LOCATION_INVALID,
                    Map.of("reason", "위도와 경도를 올바른 범위로 함께 입력해야 합니다."));
        }
        int normalizedRadius = radiusMeters == null ? 3000 : radiusMeters;
        if (normalizedRadius < 100 || normalizedRadius > 10000) {
            throw withData(ErrorCode.APARTMENT_RADIUS_INVALID,
                    Map.of("radiusMeters", normalizedRadius));
        }
        if (normalizedKeyword == null && normalizedDistrict == null
                && normalizedDong == null && latitude == null) {
            throw new BusinessException(ErrorCode.APARTMENT_FILTER_REQUIRED);
        }
        return new ApartmentSearchCondition(
                normalizedKeyword, normalizedDistrict, normalizedDong,
                latitude, longitude, normalizedRadius, normalizedPage, normalizedSize
        );
    }

    public ApartmentSearchMode mode() {
        if (latitude != null) return ApartmentSearchMode.NEARBY;
        if (districtCode != null || dongCode != null) return ApartmentSearchMode.REGION;
        return ApartmentSearchMode.KEYWORD;
    }

    private static String normalizeKeyword(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        normalized = normalized.replaceAll("\\s+", " ");
        if (normalized.length() > 100 || !normalized.matches(".*[가-힣A-Za-z0-9].*")) {
            invalid("keyword", "검색어는 100자 이하의 한글, 영문 또는 숫자를 포함해야 합니다.");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void invalid(String field, String reason) {
        throw withData(ErrorCode.INVALID_INPUT_VALUE,
                Map.of("field", field, "reason", reason));
    }

    private static BusinessException withData(ErrorCode code, Map<String, Object> data) {
        return new BusinessException(code, data);
    }
}
