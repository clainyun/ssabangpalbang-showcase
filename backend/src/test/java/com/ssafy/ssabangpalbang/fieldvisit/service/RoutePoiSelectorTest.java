package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitRouteProperties;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutePoiSelectorTest {

    private final RoutePoiSelector selector = new RoutePoiSelector(properties());

    @Test
    void 원점_80미터_이내_시설도_경유지에_포함한다() {
        ChecklistItem item = mock(ChecklistItem.class);
        List<RouteCandidate> result = selector.select(
                37.0, 127.0,
                Map.of(FacilityType.CONVENIENCE_STORE, List.of(item)),
                Map.of(FacilityType.CONVENIENCE_STORE, Optional.of(
                        poi("near", 37.0003, 127.0)
                ))
        );

        assertThat(result).singleElement().satisfies(candidate ->
                assertThat(candidate.poi().id()).isEqualTo("near")
        );
    }

    @Test
    void 인접_후보는_항목이_많은_대표_시설로_병합한다() {
        ChecklistItem first = mock(ChecklistItem.class);
        ChecklistItem second = mock(ChecklistItem.class);
        ChecklistItem third = mock(ChecklistItem.class);
        when(first.getId()).thenReturn(1L);
        when(second.getId()).thenReturn(2L);
        when(third.getId()).thenReturn(3L);
        List<RouteCandidate> result = selector.select(
                37.0, 127.0,
                Map.of(
                        FacilityType.BANK, List.of(first),
                        FacilityType.HOSPITAL, List.of(second, third)
                ),
                Map.of(
                        FacilityType.BANK, Optional.of(poi("bank", 37.0020, 127.0)),
                        FacilityType.HOSPITAL, Optional.of(poi("hospital", 37.0024, 127.0))
                )
        );

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.facilityType()).isEqualTo(FacilityType.HOSPITAL);
            assertThat(candidate.checklistItems()).containsExactlyInAnyOrder(first, second, third);
        });
    }

    @Test
    void 후보가_5개를_넘으면_항목수_거리_타입순으로_절단한다() {
        ChecklistItem one = mock(ChecklistItem.class);
        ChecklistItem two = mock(ChecklistItem.class);
        when(one.getId()).thenReturn(1L);
        when(two.getId()).thenReturn(2L);
        Map<FacilityType, List<ChecklistItem>> items = Map.of(
                FacilityType.SUBWAY_STATION, List.of(one),
                FacilityType.MART, List.of(one, two),
                FacilityType.BANK, List.of(one),
                FacilityType.HOSPITAL, List.of(one),
                FacilityType.CULTURAL_FACILITY, List.of(one),
                FacilityType.PUBLIC_OFFICE, List.of(one)
        );
        Map<FacilityType, Optional<KakaoLocalPoi>> pois = Map.of(
                FacilityType.SUBWAY_STATION, Optional.of(poi("subway", 37.015, 127.0)),
                FacilityType.MART, Optional.of(poi("mart", 37.016, 127.0)),
                FacilityType.BANK, Optional.of(poi("bank", 37.011, 127.0)),
                FacilityType.HOSPITAL, Optional.of(poi("hospital", 37.012, 127.0)),
                FacilityType.CULTURAL_FACILITY, Optional.of(poi("culture", 37.013, 127.0)),
                FacilityType.PUBLIC_OFFICE, Optional.of(poi("office", 37.014, 127.0))
        );

        FieldVisitRouteProperties trimProperties = properties();
        trimProperties.setWaypointMergeMeters(1);
        List<RouteCandidate> result = new RoutePoiSelector(trimProperties)
                .select(37.0, 127.0, items, pois);

        assertThat(result).hasSize(5);
        assertThat(result).extracting(RouteCandidate::facilityType)
                .containsExactly(
                        FacilityType.MART,
                        FacilityType.BANK,
                        FacilityType.HOSPITAL,
                        FacilityType.CULTURAL_FACILITY,
                        FacilityType.PUBLIC_OFFICE
                );
    }

    private static FieldVisitRouteProperties properties() {
        FieldVisitRouteProperties properties = new FieldVisitRouteProperties();
        properties.setWaypointMergeMeters(80);
        properties.setMaxWaypoints(5);
        return properties;
    }

    private static KakaoLocalPoi poi(String id, double latitude, double longitude) {
        return new KakaoLocalPoi(id, id, "", null, latitude, longitude, 100);
    }
}
