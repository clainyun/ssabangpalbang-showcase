package com.ssafy.ssabangpalbang.fieldvisit.domain;

import java.util.Arrays;

/** 추천 경로에서 지원하는 주변 시설과 카카오 검색 메타데이터다. */
public enum FacilityType {

    SUBWAY_STATION("SW8", 1_500, 8, "단지에서 승강장까지 실제로 걸어 시간을 재 보세요."),
    ELEMENTARY_SCHOOL("SC4", 1_000, 7, "통학로의 횡단보도와 보행자 분리 상태를 확인해 보세요."),
    MIDDLE_SCHOOL("SC4", 1_500, 7, "통학 동선과 주변 보행 환경을 확인해 보세요."),
    HIGH_SCHOOL("SC4", 1_500, 7, "통학 동선과 주변 보행 환경을 확인해 보세요."),
    DAYCARE("PS3", 800, 5, "등·하원 동선과 주변 보행 안전을 확인해 보세요."),
    MART("MT1", 2_000, 7, "장보기 동선과 실제 접근성을 확인해 보세요."),
    CONVENIENCE_STORE("CS2", 500, 3, "생필품 구매 동선을 직접 걸어 확인해 보세요."),
    BANK("BK9", 1_500, 3, "금융 업무를 볼 수 있는 생활 동선을 확인해 보세요."),
    PARKING_LOT("PK6", 1_000, 5, "공영주차장 접근성과 실제 진입 동선을 확인해 보세요."),
    HOSPITAL("HP8", 1_500, 5, "가까운 의료시설까지의 실제 접근성을 확인해 보세요."),
    CULTURAL_FACILITY("CT1", 2_000, 5, "문화·체육 시설의 접근성과 운영 환경을 확인해 보세요."),
    PUBLIC_OFFICE("PO3", 2_000, 5, "주민센터 등 공공기관까지의 동선을 확인해 보세요.");

    private final String categoryGroupCode;
    private final int radiusMeters;
    private final int stayMinutes;
    private final String guide;

    FacilityType(
            String categoryGroupCode,
            int radiusMeters,
            int stayMinutes,
            String guide
    ) {
        this.categoryGroupCode = categoryGroupCode;
        this.radiusMeters = radiusMeters;
        this.stayMinutes = stayMinutes;
        this.guide = guide;
    }

    public String categoryGroupCode() {
        return categoryGroupCode;
    }

    public int radiusMeters() {
        return radiusMeters;
    }

    public int stayMinutes() {
        return stayMinutes;
    }

    public String guide() {
        return guide;
    }

    public boolean requiresCategoryNameFilter() {
        return this == ELEMENTARY_SCHOOL || this == MIDDLE_SCHOOL
                || this == HIGH_SCHOOL || this == DAYCARE || this == HOSPITAL;
    }

    public static int maximumRadiusForCategory(String categoryGroupCode) {
        return Arrays.stream(values())
                .filter(type -> type.categoryGroupCode.equals(categoryGroupCode))
                .mapToInt(FacilityType::radiusMeters)
                .max()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 카카오 카테고리 코드입니다: " + categoryGroupCode
                ));
    }
}
