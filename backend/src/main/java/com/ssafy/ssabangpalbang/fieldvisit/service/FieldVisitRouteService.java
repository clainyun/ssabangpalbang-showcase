package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoiClient;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoiUnavailableException;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitRouteProperties;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRoute;
import com.ssafy.ssabangpalbang.fieldvisit.dto.RouteResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitRouteDetailResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitRouteGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitRouteRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** 공유 추천 경로의 생성 멱등성과 호출자별 조회 조립을 담당한다. */
@Slf4j
@Service
public class FieldVisitRouteService {

    private final FieldVisitAccessService accessService;
    private final StudyRepository studyRepository;
    private final ApartmentRepository apartmentRepository;
    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final FieldVisitRouteRepository routeRepository;
    private final ChecklistItemFacilityMapper facilityMapper;
    private final KakaoLocalPoiClient poiClient;
    private final RoutePoiSelector poiSelector;
    private final WalkingRoutePlanner walkingRoutePlanner;
    private final RouteWriter routeWriter;
    private final RouteResponseAssembler responseAssembler;
    private final LegacyRouteRecalculationService legacyRouteRecalculationService;
    private final FieldVisitRouteProperties properties;
    private final Executor poiExecutor;

    public FieldVisitRouteService(
            FieldVisitAccessService accessService,
            StudyRepository studyRepository,
            ApartmentRepository apartmentRepository,
            ChecklistRepository checklistRepository,
            ChecklistItemRepository checklistItemRepository,
            FieldVisitRouteRepository routeRepository,
            ChecklistItemFacilityMapper facilityMapper,
            KakaoLocalPoiClient poiClient,
            RoutePoiSelector poiSelector,
            WalkingRoutePlanner walkingRoutePlanner,
            RouteWriter routeWriter,
            RouteResponseAssembler responseAssembler,
            LegacyRouteRecalculationService legacyRouteRecalculationService,
            FieldVisitRouteProperties properties,
            @Qualifier("fieldVisitRoutePoiExecutor") Executor poiExecutor
    ) {
        this.accessService = accessService;
        this.studyRepository = studyRepository;
        this.apartmentRepository = apartmentRepository;
        this.checklistRepository = checklistRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.routeRepository = routeRepository;
        this.facilityMapper = facilityMapper;
        this.poiClient = poiClient;
        this.poiSelector = poiSelector;
        this.walkingRoutePlanner = walkingRoutePlanner;
        this.routeWriter = routeWriter;
        this.responseAssembler = responseAssembler;
        this.legacyRouteRecalculationService = legacyRouteRecalculationService;
        this.properties = properties;
        this.poiExecutor = poiExecutor;
    }

    public GenerateResult generate(Long studyId, Long memberId) {
        FieldVisitParticipation participation = requireGenerationParticipation(
                studyId, memberId
        );
        Long sessionId = participation.session().getId();
        Study study = studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        Apartment apartment = apartmentRepository.findById(study.getApartmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APARTMENT_NOT_FOUND));

        Optional<FieldVisitRoute> existing = routeRepository.findBySessionId(sessionId);
        if (existing.isPresent()) {
            FieldVisitRoute ready = legacyRouteRecalculationService.ensureWalkingRoute(
                    existing.get(), apartment
            );
            return existingResult(studyId, ready, apartment, memberId);
        }
        if (!checklistRepository.existsBySessionId(sessionId)) {
            throw new BusinessException(ErrorCode.ROUTE_CHECKLIST_REQUIRED);
        }

        List<ChecklistItem> sessionItems = checklistItemRepository
                .findBySessionIdOrderByChecklistIdAndDisplayOrder(sessionId);
        Map<FacilityType, List<ChecklistItem>> mappedItems = facilityMapper.map(sessionItems);
        if (mappedItems.isEmpty()) {
            log.info("Route not applicable: no facility mapping. sessionId={}", sessionId);
            throw new BusinessException(ErrorCode.ROUTE_NOT_APPLICABLE);
        }
        Map<FacilityType, Optional<KakaoLocalPoi>> pois = findPois(
                mappedItems,
                apartment
        );
        List<RouteCandidate> selected = poiSelector.select(
                apartment.getLatitude(),
                apartment.getLongitude(),
                mappedItems,
                pois
        );
        if (selected.size() < properties.getMinWaypoints()) {
            log.info(
                    "Route not applicable: insufficient waypoints. sessionId={}, count={}",
                    sessionId,
                    selected.size()
            );
            throw new BusinessException(ErrorCode.ROUTE_NOT_APPLICABLE);
        }
        WalkingRoutePlan plan = walkingRoutePlanner.plan(
                apartment.getLatitude(), apartment.getLongitude(), selected
        );

        try {
            FieldVisitRoute saved = routeWriter.save(
                    studyId, sessionId, memberId, plan
            );
            return new GenerateResult(
                    HttpStatus.CREATED,
                    RouteResponseCode.ROUTE_GENERATE_SUCCESS,
                    responseAssembler.toGenerateResponse(
                            studyId, saved, apartment, memberId
                    )
            );
        } catch (DataIntegrityViolationException exception) {
            FieldVisitRoute concurrent = routeRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> exception);
            concurrent = legacyRouteRecalculationService.ensureWalkingRoute(
                    concurrent, apartment
            );
            log.info("Route UNIQUE conflict resolved by reload. sessionId={}", sessionId);
            return existingResult(studyId, concurrent, apartment, memberId);
        }
    }

    public FieldVisitRouteDetailResponse getRoute(Long studyId, Long memberId) {
        FieldVisitReadStatus status = accessService.readStatus(
                studyId, memberId, ErrorCode.FIELD_VISIT_ACCESS_DENIED
        );
        if (!status.isSessionStarted()) {
            return new FieldVisitRouteDetailResponse(studyId, false, null);
        }
        Optional<FieldVisitRoute> route = routeRepository.findBySessionId(status.sessionId());
        if (route.isEmpty()) {
            return new FieldVisitRouteDetailResponse(
                    studyId, status.readOnly(), null
            );
        }
        Study study = studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        Apartment apartment = apartmentRepository.findById(study.getApartmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APARTMENT_NOT_FOUND));
        FieldVisitRoute ready = legacyRouteRecalculationService.ensureWalkingRoute(
                route.get(), apartment
        );
        return new FieldVisitRouteDetailResponse(
                studyId,
                status.readOnly(),
                responseAssembler.toDetailRoute(ready, apartment, memberId)
        );
    }

    private FieldVisitParticipation requireGenerationParticipation(
            Long studyId,
            Long memberId
    ) {
        try {
            return accessService.requireChecklistGenerationParticipation(
                    studyId, memberId, ErrorCode.ROUTE_GENERATE_FORBIDDEN
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.FIELD_VISIT_NOT_STARTED) {
                throw new BusinessException(ErrorCode.ROUTE_FIELD_VISIT_NOT_STARTED);
            }
            throw exception;
        }
    }

    private Map<FacilityType, Optional<KakaoLocalPoi>> findPois(
            Map<FacilityType, List<ChecklistItem>> mappedItems,
            Apartment apartment
    ) {
        List<FacilityType> types = new ArrayList<>(mappedItems.keySet());
        Map<FacilityType, CompletableFuture<Optional<KakaoLocalPoi>>> futures =
                new LinkedHashMap<>();
        for (FacilityType type : types) {
            futures.put(type, CompletableFuture.supplyAsync(() -> poiClient.findNearest(
                    type,
                    apartment.getId(),
                    apartment.getLatitude(),
                    apartment.getLongitude()
            ), poiExecutor));
        }
        Map<FacilityType, Optional<KakaoLocalPoi>> results = new LinkedHashMap<>();
        for (FacilityType type : types) {
            try {
                results.put(type, futures.get(type).join());
            } catch (CompletionException exception) {
                futures.values().forEach(future -> future.cancel(true));
                Throwable cause = exception.getCause();
                if (cause instanceof KakaoLocalPoiUnavailableException) {
                    throw new BusinessException(ErrorCode.ROUTE_POI_UNAVAILABLE);
                }
                throw new BusinessException(ErrorCode.ROUTE_POI_UNAVAILABLE);
            }
        }
        return results;
    }

    private GenerateResult existingResult(
            Long studyId,
            FieldVisitRoute route,
            Apartment apartment,
            Long memberId
    ) {
        return new GenerateResult(
                HttpStatus.OK,
                RouteResponseCode.ROUTE_ALREADY_EXISTS,
                responseAssembler.toGenerateResponse(studyId, route, apartment, memberId)
        );
    }

    public record GenerateResult(
            HttpStatus httpStatus,
            RouteResponseCode responseCode,
            FieldVisitRouteGenerateResponse body
    ) {
    }
}
