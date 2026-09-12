package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistPersonalizationInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Production fallback 체크리스트다. LLM을 호출하지 않으며 동일 입력에
 * 대해 deterministic하다. priorities가 유효하면 해당 관점 항목을 앞에 배치한다.
 */
@Component
public class FallbackChecklistProvider {

    private static final int MAX_ITEMS = 25;

    private static final Map<String, ChecklistAiGenerateResponse.Item> PRIORITY_TEMPLATES =
            Map.of(
                    "TRANSPORT", item("교통", "대중교통·도로 접근성",
                            "가장 가까운 역·버스 정류장까지 실제 도보 시간을 확인하세요.", 0),
                    "SAFETY", item("안전", "야간 보행·보안",
                            "야간 가로등·CCTV·경비실 위치를 확인하세요.", 0),
                    "EDUCATION", item("학군", "통학·교육 환경",
                            "어린이집·학교·학원가까지의 동선과 대기 공간을 확인하세요.", 0),
                    "COMMERCIAL", item("생활편의", "상업시설 접근성",
                            "장보기·카페·병원 등 생활 편의시설 거리를 확인하세요.", 0),
                    "WALKABILITY", item("보행", "보행 쾌적도",
                            "인도와 단지 보행로의 경사·단차를 확인하세요.", 0),
                    "GREEN_SPACE", item("주변환경", "녹지·공원",
                            "단지 인근 공원·녹지 접근성과 관리 상태를 확인하세요.", 0),
                    "PARKING", item("주차", "주차 여유와 동선",
                            "지하·지상 주차 여유와 출입 동선을 확인하세요.", 0),
                    "NOISE", item("소음", "도로·생활 소음",
                            "출퇴근 시간대와 야간 소음 수준을 확인하세요.", 0)
            );

    private static final List<ChecklistAiGenerateResponse.Item> BASE_ITEMS = List.of(
            item("교통", "출퇴근 혼잡도", "출근·퇴근 시간대 도로·대중교통 혼잡을 확인하세요.", 0),
            item("안전", "단지 출입 통제", "출입구 개수와 방문 차량 통제 방식을 확인하세요.", 0),
            item("보행", "유모차·휠체어 이동성", "경사로·엘리베이터 접근성을 확인하세요.", 0),
            item("주차", "방문 차량 주차", "방문객 주차 가능 여부와 요금을 확인하세요.", 0),
            item("소음", "단지 내부 소음", "놀이터·단지 도로 소음을 확인하세요.", 0),
            item("생활편의", "생활 인프라", "편의점·세탁·쓰레기 배출 동선을 확인하세요.", 0),
            item("단지환경", "공용부 관리 상태", "로비·복도·엘리베이터 청결을 확인하세요.", 0),
            item("주변환경", "주변 공사·혐오시설", "인근 공사·혐오시설 유무를 확인하세요.", 0),
            item("단지환경", "조경·휴게 공간", "단지 내 벤치·조경 관리 상태를 확인하세요.", 0),
            item("안전", "화재·대피 동선", "소화전·대피 안내 표지를 확인하세요.", 0),
            item("교통", "역까지 실제 도보 시간", "단지 주출입구에서 가까운 역까지 직접 걸어 시간을 확인하세요.", 0),
            item("소음", "층간·벽간 소음", "세대 안에서 위·옆집 생활 소음을 확인하세요.", 0),
            item("채광", "일조·채광", "거실·안방에 햇빛이 드는 시간과 방향을 확인하세요.", 0),
            item("주차", "주차 여유", "지하·지상 주차 여유와 세대당 대수를 확인하세요.", 0),
            item("생활편의", "병원·약국 접근성", "가까운 병원·약국까지 거리와 진료 과목을 확인하세요.", 0),
            item("학군", "통학 환경", "어린이집·학교까지 통학로 안전과 거리를 확인하세요.", 0),
            item("단지환경", "엘리베이터 상태", "엘리베이터 대수·대기 시간·노후도를 확인하세요.", 0),
            item("주변환경", "녹지·공원", "산책로·공원 접근성과 관리 상태를 확인하세요.", 0),
            item("안전", "야간 보안", "야간 가로등·CCTV·경비 상태를 확인하세요.", 0),
            item("생활편의", "장보기 편의", "마트·시장까지 거리와 이동 수단을 확인하세요.", 0),
            item("단지환경", "세대 수납·구조", "방·수납 공간과 발코니 확장 여부를 확인하세요.", 0),
            item("교통", "대중교통 노선", "버스·지하철 노선 다양성과 배차 간격을 확인하세요.", 0),
            item("보행", "단지 내 보행 안전", "차도·보도 분리와 과속방지턱을 확인하세요.", 0),
            item("생활편의", "쓰레기·분리수거", "분리수거장 위치와 배출 규칙을 확인하세요.", 0),
            item("단지환경", "결로·누수 흔적", "벽·천장·창틀의 곰팡이·누수 흔적을 확인하세요.", 0)
    );

    public ChecklistAiGenerateResponse create(
            ChecklistPersonalizationInput personalization
    ) {
        LinkedHashMap<String, ChecklistAiGenerateResponse.Item> ordered =
                new LinkedHashMap<>();

        if (personalization != null
                && personalization.member() != null
                && personalization.member().priorities() != null) {
            for (String priority : personalization.member().priorities()) {
                ChecklistAiGenerateResponse.Item template =
                        PRIORITY_TEMPLATES.get(priority);
                if (template != null) {
                    ordered.putIfAbsent(template.title(), template);
                }
            }
        }

        for (ChecklistAiGenerateResponse.Item base : BASE_ITEMS) {
            ordered.putIfAbsent(base.title(), base);
        }

        List<ChecklistAiGenerateResponse.Item> items = new ArrayList<>();
        int order = 1;
        for (ChecklistAiGenerateResponse.Item item : ordered.values()) {
            if (items.size() >= MAX_ITEMS) {
                break;
            }
            items.add(new ChecklistAiGenerateResponse.Item(
                    item.category(),
                    item.title(),
                    item.subtitle(),
                    order++,
                    item.example()
            ));
        }
        return new ChecklistAiGenerateResponse(List.copyOf(items));
    }

    private static ChecklistAiGenerateResponse.Item item(
            String category,
            String title,
            String subtitle,
            int displayOrder
    ) {
        // 하드코딩 fallback 항목은 예시를 두지 않는다(AI 불가 시 퇴화 경로). 조회 응답에서는
        // example 이 null 이면 프론트가 기본 안내 문구를 쓴다.
        return new ChecklistAiGenerateResponse.Item(
                category, title, subtitle, displayOrder, null
        );
    }
}
