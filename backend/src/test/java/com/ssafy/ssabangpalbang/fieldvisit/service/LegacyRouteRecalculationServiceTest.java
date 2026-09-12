package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoiClient;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitRouteProperties;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRoute;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyRouteRecalculationServiceTest {

    @Test
    void geometry가_없는_V16_경로는_현재_POI_선정_규칙부터_다시_적용한다() {
        Fixture fixture = new Fixture();
        FieldVisitRoute route = fixture.geometrylessRoute();
        ChecklistItem item = mock(ChecklistItem.class);
        Map<FacilityType, List<ChecklistItem>> mapped = Map.of(
                FacilityType.HOSPITAL, List.of(item)
        );
        KakaoLocalPoi humanHospital = new KakaoLocalPoi(
                "human", "행복내과", "의료 > 병원 > 내과", null,
                37.001, 127.001, 100
        );
        RouteCandidate first = new RouteCandidate(
                FacilityType.HOSPITAL, humanHospital, List.of(item), 100
        );
        RouteCandidate second = new RouteCandidate(
                FacilityType.MART,
                new KakaoLocalPoi("mart", "마트", "", null, 37.002, 127.002, 200),
                List.of(item),
                200
        );
        when(fixture.itemRepository.findBySessionIdOrderByChecklistIdAndDisplayOrder(100L))
                .thenReturn(List.of(item));
        when(fixture.mapper.map(List.of(item))).thenReturn(mapped);
        when(fixture.poiClient.findNearest(
                FacilityType.HOSPITAL, 3012L, 37.0, 127.0
        )).thenReturn(Optional.of(humanHospital));
        when(fixture.selector.select(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(first, second));
        WalkingRoutePlan plan = mock(WalkingRoutePlan.class);
        when(fixture.planner.plan(37.0, 127.0, List.of(first, second))).thenReturn(plan);
        FieldVisitRoute updated = mock(FieldVisitRoute.class);
        when(fixture.writer.replaceLegacy(21L, plan)).thenReturn(updated);

        FieldVisitRoute result = fixture.service.ensureWalkingRoute(route, fixture.apartment());

        assertThat(result).isSameAs(updated);
        verify(fixture.poiClient).findNearest(
                FacilityType.HOSPITAL, 3012L, 37.0, 127.0
        );
        verify(fixture.selector).select(anyDouble(), anyDouble(), any(), any());
        verify(fixture.writer).replaceLegacy(21L, plan);
    }

    @Test
    void 보행_API_실패시_기존_V16_경로를_교체하지_않는다() {
        Fixture fixture = new Fixture();
        FieldVisitRoute route = fixture.geometrylessRoute();
        ChecklistItem item = mock(ChecklistItem.class);
        RouteCandidate first = fixture.candidate("first", 37.001);
        RouteCandidate second = fixture.candidate("second", 37.002);
        when(fixture.itemRepository.findBySessionIdOrderByChecklistIdAndDisplayOrder(100L))
                .thenReturn(List.of(item));
        when(fixture.mapper.map(List.of(item))).thenReturn(Map.of(
                FacilityType.MART, List.of(item)
        ));
        when(fixture.poiClient.findNearest(any(), any(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(first.poi()));
        when(fixture.selector.select(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(first, second));
        when(fixture.planner.plan(anyDouble(), anyDouble(), any()))
                .thenThrow(new BusinessException(ErrorCode.ROUTE_WALKING_UNAVAILABLE));

        assertThatThrownBy(() -> fixture.service.ensureWalkingRoute(
                route, fixture.apartment()
        )).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ROUTE_WALKING_UNAVAILABLE);
        verify(fixture.writer, never()).replaceLegacy(any(), any());
    }

    @Test
    void geometry가_이미_있으면_외부_POI를_다시_조회하지_않는다() {
        Fixture fixture = new Fixture();
        FieldVisitRoute route = mock(FieldVisitRoute.class);
        when(route.getGeometry()).thenReturn(JsonNodeFactory.instance.objectNode());

        assertThat(fixture.service.ensureWalkingRoute(route, fixture.apartment()))
                .isSameAs(route);
        verify(fixture.poiClient, never()).findNearest(any(), any(), anyDouble(), anyDouble());
    }

    private static final class Fixture {

        private final ChecklistItemRepository itemRepository = mock(ChecklistItemRepository.class);
        private final ChecklistItemFacilityMapper mapper = mock(ChecklistItemFacilityMapper.class);
        private final KakaoLocalPoiClient poiClient = mock(KakaoLocalPoiClient.class);
        private final RoutePoiSelector selector = mock(RoutePoiSelector.class);
        private final WalkingRoutePlanner planner = mock(WalkingRoutePlanner.class);
        private final RouteWriter writer = mock(RouteWriter.class);
        private final FieldVisitRouteProperties properties = new FieldVisitRouteProperties();
        private final LegacyRouteRecalculationService service =
                new LegacyRouteRecalculationService(
                        itemRepository,
                        mapper,
                        poiClient,
                        selector,
                        planner,
                        writer,
                        properties
                );

        private FieldVisitRoute geometrylessRoute() {
            FieldVisitRoute route = mock(FieldVisitRoute.class);
            when(route.getId()).thenReturn(21L);
            when(route.getSessionId()).thenReturn(100L);
            when(route.getGeometry()).thenReturn(null);
            return route;
        }

        private Apartment apartment() {
            Apartment apartment = mock(Apartment.class);
            when(apartment.getId()).thenReturn(3012L);
            when(apartment.getLatitude()).thenReturn(37.0);
            when(apartment.getLongitude()).thenReturn(127.0);
            return apartment;
        }

        private RouteCandidate candidate(String id, double latitude) {
            return new RouteCandidate(
                    FacilityType.MART,
                    new KakaoLocalPoi(id, id, "", null, latitude, 127.001, 100),
                    List.of(),
                    100
            );
        }
    }
}
