package com.ssafy.ssabangpalbang.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFavoriteReportResponse;
import com.ssafy.ssabangpalbang.member.service.MemberFavoriteReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberFavoriteReportControllerTest {

    private MemberFavoriteReportService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MemberFavoriteReportService.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MemberFavoriteReportController(service)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authenticatedMemberResolver())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    @Test
    void 찜한_리포트_목록은_카드와_페이지_계약을_반환한다()
            throws Exception {
        when(service.getFavoriteReports(eq(1L), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(
                        List.of(new MemberFavoriteReportResponse(
                                48L,
                                "래미안 옥수 리버젠 임장 리포트",
                                "교통 접근성이 좋습니다.",
                                List.of("교통 우수", "단지 경사"),
                                new MemberFavoriteReportResponse.ApartmentSummary(
                                        15L,
                                        "래미안 옥수 리버젠",
                                        "서울특별시 성동구 매봉길 15",
                                        "옥수동",
                                        "https://cdn.example.com/"
                                                + "apartment-images/v1/A13201203.webp"
                                ),
                                true,
                                OffsetDateTime.parse(
                                        "2026-07-22T18:07:00+09:00"
                                ),
                                OffsetDateTime.parse(
                                        "2026-07-23T09:00:00+09:00"
                                )
                        )),
                        1,
                        0,
                        20,
                        1
                ));

        mockMvc.perform(get("/api/v1/members/me/favorite-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(
                        "MEMBER_FAVORITE_REPORT_LIST_SUCCESS"
                ))
                .andExpect(jsonPath("$.data.content[0].reportId")
                        .value(48))
                .andExpect(jsonPath("$.data.content[0].title")
                        .value("래미안 옥수 리버젠 임장 리포트"))
                .andExpect(jsonPath("$.data.content[0].analysisTags[0]")
                        .value("교통 우수"))
                .andExpect(jsonPath(
                        "$.data.content[0].apartment.apartmentId"
                ).value(15))
                .andExpect(jsonPath("$.data.content[0].favoritedByMe")
                        .value(true))
                .andExpect(jsonPath("$.data.content[0].favoritedAt")
                        .value("2026-07-23T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void 페이지_번호와_크기_범위를_검증한다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/favorite-reports")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        "COMMON_INVALID_REQUEST"
                ))
                .andExpect(jsonPath("$.data.field").value("page"));

        mockMvc.perform(get("/api/v1/members/me/favorite-reports")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        "COMMON_INVALID_REQUEST"
                ))
                .andExpect(jsonPath("$.data.field").value("size"));
    }

    @Test
    void 회원을_찾을_수_없으면_404를_반환한다() throws Exception {
        when(service.getFavoriteReports(
                eq(1L),
                anyInt(),
                anyInt()
        )).thenThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/members/me/favorite-reports"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    private HandlerMethodArgumentResolver authenticatedMemberResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType()
                        == AuthenticatedMember.class;
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    org.springframework.web.bind.support
                            .WebDataBinderFactory binderFactory
            ) {
                return new AuthenticatedMember(1L);
            }
        };
    }
}
