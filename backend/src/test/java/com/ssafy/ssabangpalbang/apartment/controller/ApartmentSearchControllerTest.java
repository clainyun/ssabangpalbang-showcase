package com.ssafy.ssabangpalbang.apartment.controller;

import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentSearchMode;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentSearchResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentSearchItem;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentLatestTransaction;
import com.ssafy.ssabangpalbang.apartment.service.ApartmentService;
import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.study.service.StudyService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApartmentSearchControllerTest {

    @Test
    void 키워드_검색은_정본_성공코드와_검색모드를_반환한다() {
        ApartmentService service = mock(ApartmentService.class);
        ApartmentController controller = new ApartmentController(service, mock(StudyService.class));
        ApartmentSearchResponse response = new ApartmentSearchResponse(
                ApartmentSearchMode.KEYWORD, null, List.of(), 0, 0, 20, 0);
        when(service.search(eq(1L), any())).thenReturn(response);

        var result = controller.search(
                new AuthenticatedMember(1L), "래미안", null, null,
                null, null, null, null, null);

        assertThat(result.code()).isEqualTo("APARTMENT_LIST_SUCCESS");
        assertThat(result.data().searchMode()).isEqualTo(ApartmentSearchMode.KEYWORD);
        assertThat(result.data().currentLocation()).isNull();
    }

    @Test
    void HTTP_응답은_정본_필드만_반환한다() throws Exception {
        ApartmentService service = mock(ApartmentService.class);
        when(service.search(eq(1L), any())).thenReturn(new ApartmentSearchResponse(
                ApartmentSearchMode.KEYWORD,
                null,
                List.of(new ApartmentSearchItem(
                        10L, "래미안", "주소", "강남구", "역삼동",
                        37.5010, 127.0396,
                        new ApartmentLatestTransaction(
                                183000L, "TEN_THOUSAND_KRW",
                                new BigDecimal("84.95"), LocalDate.of(2026, 6, 15)
                        ),
                        true, null, true
                )),
                1, 0, 20, 1
        ));

        mockMvc(service).perform(get("/api/v1/apartments")
                        .param("keyword", "래미안"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("APARTMENT_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.searchMode").value("KEYWORD"))
                .andExpect(jsonPath("$.data.content[0].apartmentId").value(10))
                .andExpect(jsonPath("$.data.content[0].address").value("주소"))
                .andExpect(jsonPath("$.data.content[0].favoritedByMe").value(true))
                .andExpect(jsonPath("$.data.content[0].latestTransactionAvailable").value(true))
                .andExpect(jsonPath("$.data.content[0].latestTransaction.priceUnit")
                        .value("TEN_THOUSAND_KRW"))
                .andExpect(jsonPath("$.data.content[0].completedReportCount").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].hasNext").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].householdCount").doesNotExist());
    }

    @Test
    void HTTP_오류는_검색조건별_코드를_구분한다() throws Exception {
        MockMvc mockMvc = mockMvc(mock(ApartmentService.class));

        mockMvc.perform(get("/api/v1/apartments"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APARTMENT_FILTER_REQUIRED"));
        mockMvc.perform(get("/api/v1/apartments")
                        .param("districtCode", "11440")
                        .param("dongCode", "1168010100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGION_CODE_MISMATCH"))
                .andExpect(jsonPath("$.data.districtCode").value("11440"))
                .andExpect(jsonPath("$.data.dongCode").value("1168010100"));
        mockMvc.perform(get("/api/v1/apartments")
                        .param("latitude", "37.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APARTMENT_LOCATION_INVALID"));
    }

    private MockMvc mockMvc(ApartmentService service) {
        return MockMvcBuilders.standaloneSetup(
                        new ApartmentController(service, mock(StudyService.class))
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authenticatedMemberResolver())
                .build();
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
