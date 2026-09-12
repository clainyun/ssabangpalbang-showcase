package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 체크리스트 문구를 경로 대상 시설로 결정론적으로 매핑한다. */
@Component
public class ChecklistItemFacilityMapper {

    private static final List<String> INSIDE_COMPLEX_KEYWORDS = List.of(
            "단지 내", "단지 내부", "공용부", "세대", "현관", "엘리베이터", "로비",
            "복도", "출입 통제", "놀이터", "조경", "경비실", "지하주차장"
    );

    private static final Map<FacilityType, List<String>> DIRECT_KEYWORDS =
            directKeywords();

    private static final Map<FacilityType, List<String>> BROAD_KEYWORDS = Map.of(
            FacilityType.ELEMENTARY_SCHOOL, List.of("학군", "교육", "통학"),
            FacilityType.SUBWAY_STATION, List.of("교통", "대중교통", "통근", "출퇴근"),
            FacilityType.MART, List.of("생활편의", "생활 인프라", "상권", "상업")
    );

    /**
     * 입력 순서와 시설 enum 순서를 보존한다. 하나의 항목은 여러 시설에 속할 수 있다.
     */
    public Map<FacilityType, List<ChecklistItem>> map(
            List<ChecklistItem> checklistItems
    ) {
        Map<FacilityType, List<ChecklistItem>> mapped = new LinkedHashMap<>();
        if (checklistItems == null) {
            return mapped;
        }
        for (ChecklistItem item : checklistItems) {
            if (item == null) {
                continue;
            }
            String text = normalizedText(item);
            if (isInsideComplex(text)) {
                continue;
            }

            List<FacilityType> direct = matchedTypes(text, DIRECT_KEYWORDS);
            List<FacilityType> types = direct.isEmpty()
                    ? matchedTypes(text, BROAD_KEYWORDS)
                    : direct;
            for (FacilityType type : types) {
                mapped.computeIfAbsent(type, ignored -> new ArrayList<>()).add(item);
            }
        }
        return mapped;
    }

    private static String normalizedText(ChecklistItem item) {
        return String.join(" ", nonNull(item.getCategory()), nonNull(item.getTitle()),
                nonNull(item.getSubtitle())).toLowerCase(Locale.ROOT);
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    private static boolean isInsideComplex(String text) {
        return INSIDE_COMPLEX_KEYWORDS.stream().anyMatch(text::contains);
    }

    private static List<FacilityType> matchedTypes(
            String text,
            Map<FacilityType, List<String>> keywords
    ) {
        List<FacilityType> result = new ArrayList<>();
        for (FacilityType type : FacilityType.values()) {
            List<String> candidates = keywords.get(type);
            if (candidates != null && candidates.stream().anyMatch(text::contains)) {
                result.add(type);
            }
        }
        return result;
    }

    private static Map<FacilityType, List<String>> directKeywords() {
        Map<FacilityType, List<String>> keywords = new EnumMap<>(FacilityType.class);
        keywords.put(FacilityType.ELEMENTARY_SCHOOL, List.of("초등학교", "초품아"));
        keywords.put(FacilityType.MIDDLE_SCHOOL, List.of("중학교"));
        keywords.put(FacilityType.HIGH_SCHOOL, List.of("고등학교"));
        keywords.put(FacilityType.DAYCARE, List.of("어린이집", "보육"));
        keywords.put(FacilityType.SUBWAY_STATION, List.of("지하철", "전철", "역세권", "환승"));
        keywords.put(FacilityType.MART, List.of("대형마트", "마트", "장보기"));
        keywords.put(FacilityType.CONVENIENCE_STORE, List.of("편의점"));
        keywords.put(FacilityType.BANK, List.of("은행", "금융"));
        keywords.put(FacilityType.PARKING_LOT, List.of("주차장", "공영주차"));
        keywords.put(FacilityType.HOSPITAL, List.of("병원", "의료", "진료", "응급"));
        keywords.put(FacilityType.CULTURAL_FACILITY, List.of("도서관", "문화", "체육", "여가"));
        keywords.put(FacilityType.PUBLIC_OFFICE, List.of("주민센터", "공공기관", "관공서"));
        return keywords;
    }
}
