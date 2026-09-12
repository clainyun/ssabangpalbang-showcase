package com.ssafy.ssabangpalbang.report.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportShareLandingController.class)
@Import(AuthSecurityConfiguration.class)
class ReportShareLandingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 로그인하지_않아도_데이터_없는_앱_연결_페이지를_조회한다() throws Exception {
        mockMvc.perform(get("/report/{reportId}", 48L))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(
                        "X-Report-Share-Delivery",
                        "backend-fallback"
                ))
                .andExpect(header().string(
                        "X-Robots-Tag",
                        "noindex, nofollow, noarchive"
                ))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<meta property=\"og:url\" content=\"https://portfolio.example.com/report/48\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "intent://portfolio.example.com/open/report/48"
                                + "#Intent;scheme=https;"
                                + "package=com.ssafy.ssabangpalbang;end"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/api/v1/reports/48")
                )));

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 올바르지_않은_리포트_ID는_데이터_존재를_조회하지_않고_404를_반환한다()
            throws Exception {
        for (String reportId : new String[]{
                "0",
                "-1",
                "not-a-number",
                "9007199254740992",
                "9223372036854775808"
        }) {
            mockMvc.perform(get("/report/{reportId}", reportId))
                    .andExpect(status().isNotFound());
        }

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 공개_페이지라도_잘못된_Bearer_Token은_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("tampered-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/report/{reportId}", 48L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer tampered-token"))
                .andExpect(status().isUnauthorized());

        verify(jwtTokenProvider).parseAccessToken("tampered-token");
    }

    @Test
    void 실제_리포트_API는_계속_Access_Token을_요구한다() throws Exception {
        mockMvc.perform(get("/api/v1/reports/{reportId}", 48L))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jwtTokenProvider);
    }
}
