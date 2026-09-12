package com.ssafy.ssabangpalbang.auth.config;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityProbeController.class)
@Import(AuthSecurityConfiguration.class)
class AuthSecurityPolicyTest {

    private static final String PROTECTED_PATH =
            "/api/v1/security-test/protected";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 리포트_API는_Access_Token_없이_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/reports/{reportId}", 48L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 소셜_인증_API는_Access_Token_없이_호출할_수_있다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social-login"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/social-signup"))
                .andExpect(status().isOk());

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 새_API는_별도_등록이_없어도_기본적으로_인증이_필요하다() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 유효한_Access_Token이면_보호_API에_회원_ID가_전달된다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(7L);

        mockMvc.perform(get(PROTECTED_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));

        verify(jwtTokenProvider).parseAccessToken("access-token");
    }

    @Test
    void 보호_API에_유효하지_않은_Bearer_Token이_있으면_401을_반환한다()
            throws Exception {
        when(jwtTokenProvider.parseAccessToken("invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/reports/{reportId}", 48L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verify(jwtTokenProvider).parseAccessToken("invalid-token");
    }

}

@RestController
class SecurityProbeController {

    @PostMapping({
            "/api/v1/auth/social-login",
            "/api/v1/auth/social-signup"
    })
    String publicSocialAuth() {
        return "ok";
    }

    @GetMapping("/api/v1/security-test/protected")
    String protectedApi(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return authenticatedMember.memberId().toString();
    }
}
