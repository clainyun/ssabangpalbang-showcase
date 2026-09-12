package com.ssafy.ssabangpalbang.apartment.controller;

import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentReportItem;
import com.ssafy.ssabangpalbang.apartment.service.ApartmentService;
import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.study.service.StudyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApartmentReportControllerTest {

    private ApartmentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ApartmentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ApartmentController(service, mock(StudyService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authenticatedMemberResolver())
                .build();
    }

    @Test
    void 완료_리포트_목록은_정본_필드만_반환한다() throws Exception {
        when(service.getReports(eq(1L), any())).thenReturn(new PageResponse<>(
                List.of(new ApartmentReportItem(
                        48L, "제목", List.of("교통"), "요약",
                        OffsetDateTime.parse("2026-07-22T18:07:00+09:00"),
                        true, false)),
                1, 0, 20, 1
        ));

        mockMvc.perform(get("/api/v1/apartments/1/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("APARTMENT_REPORT_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].reportId").value(48))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].isParticipant").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].evidenceCount").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].participantCount").doesNotExist());
    }

    @Test
    void 요청값_오류를_구분한다() throws Exception {
        mockMvc.perform(get("/api/v1/apartments/1/reports").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("page"));
        mockMvc.perform(get("/api/v1/apartments/1/reports").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("size"));
        mockMvc.perform(get("/api/v1/apartments/0/reports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APARTMENT_ID_INVALID"));
    }

    @Test
    void 아파트가_없으면_404를_반환한다() throws Exception {
        when(service.getReports(eq(1L), any())).thenThrow(new BusinessException(
                ErrorCode.APARTMENT_NOT_FOUND,
                Map.of("apartmentId", 99999999L)
        ));
        mockMvc.perform(get("/api/v1/apartments/99999999/reports"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APARTMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.data.apartmentId").value(99999999L));
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
