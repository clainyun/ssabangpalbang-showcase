package com.ssafy.ssabangpalbang.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentLatestTransaction;
import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFavoriteApartmentResponse;
import com.ssafy.ssabangpalbang.member.service.MemberFavoriteApartmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberFavoriteApartmentControllerTest {

    private MemberFavoriteApartmentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MemberFavoriteApartmentService.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MemberFavoriteApartmentController(service)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authenticatedMemberResolver())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    @Test
    void 찜한_아파트_목록은_카드와_페이지_계약을_반환한다()
            throws Exception {
        when(service.getFavoriteApartments(eq(1L), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(
                        List.of(new MemberFavoriteApartmentResponse(
                                15L,
                                "래미안 옥수 리버젠",
                                "서울특별시 성동구 매봉길 15",
                                "성동구",
                                "옥수동",
                                1821,
                                new ApartmentLatestTransaction(
                                        183000L,
                                        "TEN_THOUSAND_KRW",
                                        new BigDecimal("84.95"),
                                        LocalDate.of(2026, 6, 15)
                                ),
                                2,
                                5,
                                OffsetDateTime.parse(
                                        "2026-07-22T18:30:00+09:00"
                                ),
                                "https://cdn.example.com/"
                                        + "apartment-images/v1/A13201203.webp"
                        )),
                        1,
                        0,
                        20,
                        1
                ));

        mockMvc.perform(get(
                        "/api/v1/members/me/favorite-apartments"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(
                        "MEMBER_FAVORITE_APARTMENT_LIST_SUCCESS"
                ))
                .andExpect(jsonPath("$.data.content[0].apartmentId")
                        .value(15))
                .andExpect(jsonPath("$.data.content[0].districtName")
                        .value("성동구"))
                .andExpect(jsonPath("$.data.content[0].dongName")
                        .value("옥수동"))
                .andExpect(jsonPath("$.data.content[0].householdCount")
                        .value(1821))
                .andExpect(jsonPath(
                        "$.data.content[0].latestTransaction.price"
                ).value(183000))
                .andExpect(jsonPath(
                        "$.data.content[0].recruitingStudyCount"
                ).value(2))
                .andExpect(jsonPath(
                        "$.data.content[0].completedReportCount"
                ).value(5))
                .andExpect(jsonPath("$.data.content[0].favoritedAt")
                        .value("2026-07-22T18:30:00+09:00"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void 페이지_번호와_크기_범위를_검증한다() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/members/me/favorite-apartments"
                ).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        "COMMON_INVALID_REQUEST"
                ))
                .andExpect(jsonPath("$.data.field").value("page"));

        mockMvc.perform(get(
                        "/api/v1/members/me/favorite-apartments"
                ).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        "COMMON_INVALID_REQUEST"
                ))
                .andExpect(jsonPath("$.data.field").value("size"));
    }

    @Test
    void 회원을_찾을_수_없으면_404를_반환한다() throws Exception {
        when(service.getFavoriteApartments(
                eq(1L),
                anyInt(),
                anyInt()
        )).thenThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(get(
                        "/api/v1/members/me/favorite-apartments"
                ))
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
