package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoiClient;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitRouteProperties;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRoute;
import com.ssafy.ssabangpalbang.fieldvisit.dto.RouteResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitRouteGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitRouteRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FieldVisitRouteServiceTest {

    @Test
    void 유니크_충돌은_동시_생성된_기존_경로로_성공_처리한다() {
        FieldVisitAccessService accessService = mock(FieldVisitAccessService.class);
        StudyRepository studyRepository = mock(StudyRepository.class);
        ApartmentRepository apartmentRepository = mock(ApartmentRepository.class);
        ChecklistRepository checklistRepository = mock(ChecklistRepository.class);
        ChecklistItemRepository checklistItemRepository = mock(ChecklistItemRepository.class);
        FieldVisitRouteRepository routeRepository = mock(FieldVisitRouteRepository.class);
        ChecklistItemFacilityMapper mapper = mock(ChecklistItemFacilityMapper.class);
        KakaoLocalPoiClient poiClient = mock(KakaoLocalPoiClient.class);
        RoutePoiSelector selector = mock(RoutePoiSelector.class);
        WalkingRoutePlanner walkingRoutePlanner = mock(WalkingRoutePlanner.class);
        RouteWriter writer = mock(RouteWriter.class);
        RouteResponseAssembler assembler = mock(RouteResponseAssembler.class);
        LegacyRouteRecalculationService recalculationService =
                mock(LegacyRouteRecalculationService.class);
        FieldVisitRouteProperties properties = new FieldVisitRouteProperties();
        FieldVisitRouteService service = new FieldVisitRouteService(
                accessService, studyRepository, apartmentRepository, checklistRepository,
                checklistItemRepository, routeRepository, mapper, poiClient, selector,
                walkingRoutePlanner, writer, assembler, recalculationService,
                properties, Runnable::run
        );
        FieldSession session = mock(FieldSession.class);
        when(session.getId()).thenReturn(100L);
        when(accessService.requireChecklistGenerationParticipation(
                eq(7L), eq(1L), any()
        )).thenReturn(new FieldVisitParticipation(session, mock(FieldParticipant.class)));
        Study study = mock(Study.class);
        when(study.getApartmentId()).thenReturn(3012L);
        when(studyRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(study));
        Apartment apartment = mock(Apartment.class);
        when(apartment.getId()).thenReturn(3012L);
        when(apartment.getLatitude()).thenReturn(37.5);
        when(apartment.getLongitude()).thenReturn(127.0);
        when(apartmentRepository.findById(3012L)).thenReturn(Optional.of(apartment));
        FieldVisitRoute concurrentRoute = mock(FieldVisitRoute.class);
        when(routeRepository.findBySessionId(100L)).thenReturn(
                Optional.empty(), Optional.of(concurrentRoute)
        );
        when(checklistRepository.existsBySessionId(100L)).thenReturn(true);
        ChecklistItem item = mock(ChecklistItem.class);
        when(checklistItemRepository.findBySessionIdOrderByChecklistIdAndDisplayOrder(100L))
                .thenReturn(List.of(item));
        when(mapper.map(List.of(item))).thenReturn(Map.of(FacilityType.MART, List.of(item)));
        KakaoLocalPoi poi = new KakaoLocalPoi(
                "1", "마트", "", null, 37.51, 127.01, 100
        );
        when(poiClient.findNearest(any(), anyLong(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(poi));
        RouteCandidate candidate = new RouteCandidate(
                FacilityType.MART, poi, List.of(item), 100
        );
        when(selector.select(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(candidate, candidate));
        WalkingRoutePlan plan = mock(WalkingRoutePlan.class);
        when(walkingRoutePlanner.plan(anyDouble(), anyDouble(), any()))
                .thenReturn(plan);
        when(writer.save(anyLong(), anyLong(), anyLong(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate session route"));
        when(recalculationService.ensureWalkingRoute(concurrentRoute, apartment))
                .thenReturn(concurrentRoute);
        FieldVisitRouteGenerateResponse response = new FieldVisitRouteGenerateResponse(
                7L, 100L, 21L, OffsetDateTime.parse("2026-08-02T10:12:00+09:00"),
                0, 0, null, null, null, List.of()
        );
        when(assembler.toGenerateResponse(7L, concurrentRoute, apartment, 1L))
                .thenReturn(response);

        FieldVisitRouteService.GenerateResult result = service.generate(7L, 1L);

        assertThat(result.httpStatus().value()).isEqualTo(200);
        assertThat(result.responseCode()).isEqualTo(RouteResponseCode.ROUTE_ALREADY_EXISTS);
        assertThat(result.body()).isSameAs(response);
        verify(routeRepository, times(2)).findBySessionId(100L);
        verify(writer).save(anyLong(), anyLong(), anyLong(), any());
    }
}
