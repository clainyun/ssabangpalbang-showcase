package com.ssafy.ssabangpalbang.auth.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NaverOAuthCallbackController.class)
@Import(AuthSecurityConfiguration.class)
class NaverOAuthCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 네이버_성공_callback은_code와_state를_앱으로_전달한다() throws Exception {
        mockMvc.perform(get("/oauth/naver/callback")
                        .param("code", "authorization-code")
                        .param("state", "oauth-state"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        containsString("ssabangpalbang://oauth?code=authorization-code")
                ))
                .andExpect(header().string(
                        "Location",
                        containsString("state=oauth-state")
                ));
    }

    @Test
    void 네이버_실패_callback은_provider_error를_앱으로_전달한다() throws Exception {
        mockMvc.perform(get("/oauth/naver/callback")
                        .param("state", "oauth-state")
                        .param("error", "access_denied")
                        .param("error_description", "사용자가 취소했습니다."))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        containsString("error=access_denied")
                ))
                .andExpect(header().string(
                        "Location",
                        containsString("error_description=")
                ));
    }
}
