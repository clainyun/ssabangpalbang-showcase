package com.ssafy.ssabangpalbang.region.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.region.dto.response.DistrictItem;
import com.ssafy.ssabangpalbang.region.dto.response.DistrictListResponse;
import com.ssafy.ssabangpalbang.region.dto.response.DongItem;
import com.ssafy.ssabangpalbang.region.dto.response.DongListResponse;
import com.ssafy.ssabangpalbang.region.service.RegionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegionControllerTest {

    private RegionService regionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        regionService = mock(RegionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RegionController(regionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authenticatedMemberResolver())
                .build();
    }

    @Test
    void 서울_자치구_목록을_반환한다() throws Exception {
        when(regionService.getDistricts(1L)).thenReturn(new DistrictListResponse(
                "11", "서울특별시",
                List.of(new DistrictItem("11680", "강남구")),
                25
        ));

        mockMvc.perform(get("/api/v1/regions/districts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REGION_DISTRICT_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.totalCount").value(25))
                .andExpect(jsonPath("$.data.districts[0].districtName").value("강남구"));
    }

    @Test
    void 선택한_자치구의_동_목록을_반환한다() throws Exception {
        when(regionService.getDongs(1L, "11710")).thenReturn(new DongListResponse(
                "11710", "송파구",
                List.of(new DongItem("1171010100", "잠실동", true, 18)),
                1
        ));

        mockMvc.perform(get("/api/v1/regions/districts/11710/dongs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REGION_DONG_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.districtName").value("송파구"));
    }

    @Test
    void 자치구_코드별_오류응답을_반환한다() throws Exception {
        when(regionService.getDongs(1L, "1171")).thenThrow(new BusinessException(
                ErrorCode.REGION_DISTRICT_CODE_INVALID,
                Map.of("field", "districtCode", "reason", "자치구 코드는 5자리 숫자여야 합니다.")
        ));
        when(regionService.getDongs(1L, "26110")).thenThrow(new BusinessException(
                ErrorCode.REGION_OUT_OF_SERVICE_AREA,
                Map.of("districtCode", "26110")
        ));
        when(regionService.getDongs(1L, "99999")).thenThrow(new BusinessException(
                ErrorCode.REGION_DISTRICT_NOT_FOUND,
                Map.of("districtCode", "99999")
        ));

        mockMvc.perform(get("/api/v1/regions/districts/1171/dongs"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGION_DISTRICT_CODE_INVALID"));
        mockMvc.perform(get("/api/v1/regions/districts/26110/dongs"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGION_OUT_OF_SERVICE_AREA"));
        mockMvc.perform(get("/api/v1/regions/districts/99999/dongs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGION_DISTRICT_NOT_FOUND"));
    }

    private HandlerMethodArgumentResolver authenticatedMemberResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == AuthenticatedMember.class;
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    org.springframework.web.bind.support.WebDataBinderFactory binderFactory
            ) {
                return new AuthenticatedMember(1L);
            }
        };
    }
}
