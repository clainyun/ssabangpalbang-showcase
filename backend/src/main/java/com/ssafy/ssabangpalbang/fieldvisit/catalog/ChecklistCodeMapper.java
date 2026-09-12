package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 온보딩·스터디 기존 코드를 JSON v3 코드로 변환한다. DB/요청 DTO는 변경하지 않는다.
 */
public final class ChecklistCodeMapper {

    private static final Map<String, String> PRIORITY_MAP = Map.of(
            "TRANSPORT", "TRANSPORTATION",
            "COMMERCIAL", "CONVENIENCE",
            "SAFETY", "SAFETY",
            "EDUCATION", "EDUCATION",
            "WALKABILITY", "WALKABILITY",
            "GREEN_SPACE", "GREEN_SPACE",
            "PARKING", "PARKING",
            "NOISE", "NOISE"
    );

    private static final Map<String, String> PURPOSE_MAP = Map.of(
            "RESIDENCE", "LIVE",
            "INVESTMENT", "INVEST",
            "STUDY", "LEARN"
    );

    private ChecklistCodeMapper() {
    }

    public static Optional<String> toCatalogPriority(String onboardingPriority) {
        if (onboardingPriority == null || onboardingPriority.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PRIORITY_MAP.get(onboardingPriority.trim()));
    }

    public static Optional<String> toCatalogPurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PURPOSE_MAP.get(purpose.trim()));
    }

    public static Map<String, String> priorityMapSnapshot() {
        return new HashMap<>(PRIORITY_MAP);
    }

    public static Map<String, String> purposeMapSnapshot() {
        return new HashMap<>(PURPOSE_MAP);
    }
}
