package com.ssafy.ssabangpalbang.apartment.controller;

import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentDistrictSummaryItem;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentDistrictSummaryResponse;
import com.ssafy.ssabangpalbang.apartment.service.ApartmentService;
import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.study.service.StudyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApartmentController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class ApartmentDistrictSummaryControllerTest {

    private static final String URI = "/api/v1/apartments/districts/summary";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApartmentService apartmentService;

    @MockitoBean
    private StudyService studyService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 집계_조회_성공시_200과_성공코드를_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token")).thenReturn(7L);
        when(apartmentService.getDistrictSummary(7L))
                .thenReturn(summaryResponse());

        mockMvc.perform(get(URI)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("APARTMENT_DISTRICT_SUMMARY_SUCCESS"))
                .andExpect(jsonPath("$.data.totalCount").value(25))
                .andExpect(jsonPath("$.data.districts.length()").value(25));
    }

    @Test
    void 인증_없이_호출하면_401() throws Exception {
        mockMvc.perform(get(URI))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(apartmentService);
    }

    @Test
    void 회원이_없으면_404와_MEMBER_NOT_FOUND를_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token")).thenReturn(7L);
        when(apartmentService.getDistrictSummary(7L))
                .thenThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(get(URI)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    private ApartmentDistrictSummaryResponse summaryResponse() {
        List<ApartmentDistrictSummaryItem> districts = IntStream.range(0, 25)
                .mapToObj(index -> new ApartmentDistrictSummaryItem(
                        String.valueOf(11000 + index),
                        "자치구" + index,
                        0L,
                        null,
                        null
                ))
                .toList();
        return ApartmentDistrictSummaryResponse.of(districts);
    }
}
