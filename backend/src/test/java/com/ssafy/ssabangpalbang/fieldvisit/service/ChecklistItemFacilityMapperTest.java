package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChecklistItemFacilityMapperTest {

    private final ChecklistItemFacilityMapper mapper = new ChecklistItemFacilityMapper();

    @Test
    void 단지_내_항목은_외부_시설_키워드가_있어도_제외한다() {
        ChecklistItem item = item("주차", "단지 내 주차장", "방문 차량 동선");

        assertThat(mapper.map(List.of(item))).isEmpty();
    }

    @Test
    void 직접_매칭은_여러_시설에_동시에_연결한다() {
        ChecklistItem item = item("생활편의", "상업시설 접근성", "장보기·카페·병원 거리");

        Map<FacilityType, List<ChecklistItem>> result = mapper.map(List.of(item));

        assertThat(result).containsOnlyKeys(FacilityType.MART, FacilityType.HOSPITAL);
        assertThat(result.get(FacilityType.MART)).containsExactly(item);
        assertThat(result.get(FacilityType.HOSPITAL)).containsExactly(item);
    }

    @Test
    void 직접_매칭이_없을_때만_포괄어를_대표_시설로_매핑한다() {
        ChecklistItem education = item("학군", "통학·교육 환경", "확인");
        ChecklistItem direct = item("교육", "중학교 통학", "확인");

        Map<FacilityType, List<ChecklistItem>> result = mapper.map(List.of(education, direct));

        assertThat(result.get(FacilityType.ELEMENTARY_SCHOOL)).containsExactly(education);
        assertThat(result.get(FacilityType.MIDDLE_SCHOOL)).containsExactly(direct);
    }

    private static ChecklistItem item(String category, String title, String subtitle) {
        ChecklistItem item = mock(ChecklistItem.class);
        when(item.getCategory()).thenReturn(category);
        when(item.getTitle()).thenReturn(title);
        when(item.getSubtitle()).thenReturn(subtitle);
        return item;
    }
}
