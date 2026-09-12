package com.ssafy.ssabangpalbang.apartment.controller;

import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentBoundsResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentBoundsItem;
import com.ssafy.ssabangpalbang.apartment.service.ApartmentService;
import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.study.service.StudyService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApartmentBoundsControllerTest {

    @Test
    void bounds_매핑은_정본_성공코드와_비페이징_응답을_반환한다() {
        ApartmentService service = mock(ApartmentService.class);
        ApartmentController controller =
                new ApartmentController(service, mock(StudyService.class));
        when(service.getBounds(eq(1L), any()))
                .thenReturn(ApartmentBoundsResponse.of(List.of()));

        var result = controller.getBounds(
                new AuthenticatedMember(1L), 37.46, 127.0, 37.54, 127.13
        );

        assertThat(result.code()).isEqualTo("APARTMENT_BOUNDS_SUCCESS");
        assertThat(result.data().apartments()).isEmpty();
        assertThat(result.data().count()).isZero();
    }

    @Test
    void 좌표가_누락되면_APARTMENT_BOUNDS_REQUIRED다() {
        ApartmentController controller = new ApartmentController(
                mock(ApartmentService.class), mock(StudyService.class)
        );

        assertThatThrownBy(() -> controller.getBounds(
                new AuthenticatedMember(1L), null, 127.0, 37.54, 127.13
        )).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.APARTMENT_BOUNDS_REQUIRED));
    }

    @Test
    void bounds_HTTP_매핑과_정본_JSON을_검증한다() throws Exception {
        ApartmentService service = mock(ApartmentService.class);
        when(service.getBounds(eq(1L), any())).thenReturn(ApartmentBoundsResponse.of(List.of(
                new ApartmentBoundsItem(
                        10L, "아파트", "주소", 37.5, 127.0,
                        null, false, 2, 1, true
                )
        )));

        mockMvc(service).perform(get("/api/v1/apartments/bounds")
                        .param("southWestLat", "37.46")
                        .param("southWestLng", "127.00")
                        .param("northEastLat", "37.54")
                        .param("northEastLng", "127.13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("APARTMENT_BOUNDS_SUCCESS"))
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.apartments[0].favoritedByMe").value(true))
                .andExpect(jsonPath("$.data.apartments[0].recruitingStudyCount").value(2))
                .andExpect(jsonPath("$.data.apartments[0].completedReportCount").value(1))
                .andExpect(jsonPath("$.data.page").doesNotExist())
                .andExpect(jsonPath("$.data.mode").doesNotExist());
    }

    @Test
    void bounds_HTTP_오류코드를_구분한다() throws Exception {
        MockMvc mockMvc = mockMvc(mock(ApartmentService.class));

        mockMvc.perform(get("/api/v1/apartments/bounds")
                        .param("southWestLng", "127.0")
                        .param("northEastLat", "37.54")
                        .param("northEastLng", "127.13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APARTMENT_BOUNDS_REQUIRED"))
                .andExpect(jsonPath("$.data.reason").exists());
        mockMvc.perform(get("/api/v1/apartments/bounds")
                        .param("southWestLat", "37.54")
                        .param("southWestLng", "127.0")
                        .param("northEastLat", "37.46")
                        .param("northEastLng", "127.13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APARTMENT_BOUNDS_ORDER_INVALID"));
        mockMvc.perform(get("/api/v1/apartments/bounds")
                        .param("southWestLat", "99")
                        .param("southWestLng", "127.0")
                        .param("northEastLat", "99.1")
                        .param("northEastLng", "127.13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("APARTMENT_BOUNDS_COORDINATE_INVALID"));
        mockMvc.perform(get("/api/v1/apartments/bounds")
                        .param("southWestLat", "37.0")
                        .param("southWestLng", "127.0")
                        .param("northEastLat", "37.6")
                        .param("northEastLng", "127.13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APARTMENT_BOUNDS_TOO_LARGE"))
                .andExpect(jsonPath("$.data").doesNotExist());
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
