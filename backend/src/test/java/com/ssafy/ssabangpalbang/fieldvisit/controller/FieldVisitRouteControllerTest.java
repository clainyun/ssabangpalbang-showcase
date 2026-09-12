package com.ssafy.ssabangpalbang.fieldvisit.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.fieldvisit.dto.RouteResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitRouteDetailResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitRouteGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.service.FieldVisitRouteService;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FieldVisitRouteController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FieldVisitRouteControllerTest {

    private static final String ROUTE_URI =
            "/api/v1/studies/{studyId}/field-visit/route";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FieldVisitRouteService routeService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(1L), null
                )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 신규_생성은_201과_ROUTE_GENERATE_SUCCESS를_반환한다() throws Exception {
        when(routeService.generate(eq(7L), eq(1L))).thenReturn(
                new FieldVisitRouteService.GenerateResult(
                        HttpStatus.CREATED,
                        RouteResponseCode.ROUTE_GENERATE_SUCCESS,
                        sampleGenerateResponse()
                )
        );

        mockMvc.perform(post(ROUTE_URI, 7L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ROUTE_GENERATE_SUCCESS"))
                .andExpect(jsonPath("$.data.routeId").value(21))
                .andExpect(jsonPath("$.data.waypoints[0].facilityType").value("MART"));
    }

    @Test
    void 기존_경로는_200과_ROUTE_ALREADY_EXISTS를_반환한다() throws Exception {
        when(routeService.generate(eq(7L), eq(1L))).thenReturn(
                new FieldVisitRouteService.GenerateResult(
                        HttpStatus.OK,
                        RouteResponseCode.ROUTE_ALREADY_EXISTS,
                        sampleGenerateResponse()
                )
        );

        mockMvc.perform(post(ROUTE_URI, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ROUTE_ALREADY_EXISTS"));
    }

    @Test
    void 미생성_조회는_route_null을_반환한다() throws Exception {
        when(routeService.getRoute(eq(7L), eq(1L))).thenReturn(
                new FieldVisitRouteDetailResponse(7L, false, null)
        );

        mockMvc.perform(get(ROUTE_URI, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ROUTE_DETAIL_SUCCESS"))
                .andExpect(jsonPath("$.data.route").isEmpty())
                .andExpect(jsonPath("$.data.readOnly").value(false));
    }

    @Test
    void 종료된_세션의_조회는_readOnly_true를_반환한다() throws Exception {
        when(routeService.getRoute(eq(7L), eq(1L))).thenReturn(
                new FieldVisitRouteDetailResponse(7L, true, null)
        );

        mockMvc.perform(get(ROUTE_URI, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readOnly").value(true));
    }

    @Test
    void 체크리스트가_없으면_409를_반환한다() throws Exception {
        when(routeService.generate(eq(7L), eq(1L))).thenThrow(
                new BusinessException(ErrorCode.ROUTE_CHECKLIST_REQUIRED)
        );

        mockMvc.perform(post(ROUTE_URI, 7L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_CHECKLIST_REQUIRED"));
    }

    @Test
    void 경유지가_부족하면_409를_반환한다() throws Exception {
        when(routeService.generate(eq(7L), eq(1L))).thenThrow(
                new BusinessException(ErrorCode.ROUTE_NOT_APPLICABLE)
        );

        mockMvc.perform(post(ROUTE_URI, 7L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_APPLICABLE"));
    }

    @Test
    void 카카오_장애는_503으로_반환한다() throws Exception {
        when(routeService.generate(eq(7L), eq(1L))).thenThrow(
                new BusinessException(ErrorCode.ROUTE_POI_UNAVAILABLE)
        );

        mockMvc.perform(post(ROUTE_URI, 7L))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ROUTE_POI_UNAVAILABLE"));
    }

    @Test
    void 카카오_보행_경로_장애는_503으로_반환한다() throws Exception {
        when(routeService.generate(eq(7L), eq(1L))).thenThrow(
                new BusinessException(ErrorCode.ROUTE_WALKING_UNAVAILABLE)
        );

        mockMvc.perform(post(ROUTE_URI, 7L))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ROUTE_WALKING_UNAVAILABLE"));
    }

    private static FieldVisitRouteGenerateResponse sampleGenerateResponse() {
        return new FieldVisitRouteGenerateResponse(
                7L,
                100L,
                21L,
                OffsetDateTime.parse("2026-08-02T10:12:00+09:00"),
                2_180,
                54,
                new FieldVisitRouteGenerateResponse.OriginResponse(
                        3012L, "래미안 원베일리", 37.5013, 127.0122
                ),
                null,
                new FieldVisitRouteGenerateResponse.MyProgressResponse(0, 1, 0, 1),
                List.of(new com.ssafy.ssabangpalbang.fieldvisit.dto.response.RouteWaypointResponse(
                        301L,
                        1,
                        "MART",
                        "대형마트",
                        "서울 서초구",
                        37.5031,
                        127.0141,
                        "26338954",
                        260,
                        260,
                        4,
                        7,
                        "장보기 동선과 실제 접근성을 확인해 보세요.",
                        List.of(),
                        0,
                        1
                ))
        );
    }
}
