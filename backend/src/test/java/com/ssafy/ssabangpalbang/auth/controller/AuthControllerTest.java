package com.ssafy.ssabangpalbang.auth.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.code.AuthSuccessCode;
import com.ssafy.ssabangpalbang.auth.dto.request.LoginRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.LogoutRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.PasswordResetCodeRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.PasswordResetConfirmRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.PasswordResetVerifyRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.SignupRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.SocialLoginRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.SocialSignupRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.TokenReissueRequest;
import com.ssafy.ssabangpalbang.auth.dto.response.AvailabilityResponse;
import com.ssafy.ssabangpalbang.auth.dto.response.LoginResponse;
import com.ssafy.ssabangpalbang.auth.dto.response.SignupResponse;
import com.ssafy.ssabangpalbang.auth.dto.response.SocialLoginResponse;
import com.ssafy.ssabangpalbang.auth.dto.response.SocialSignupResponse;
import com.ssafy.ssabangpalbang.auth.dto.response.TokenReissueResponse;
import com.ssafy.ssabangpalbang.auth.service.AuthService;
import com.ssafy.ssabangpalbang.auth.service.PasswordResetService;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        GlobalExceptionHandler.class,
        AuthSecurityConfiguration.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 로그아웃에_성공하면_200과_null_data를_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_LOGOUT_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("로그아웃되었습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(authService).logout(
                eq(1L),
                any(LogoutRequest.class)
        );
    }

    @Test
    void Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message")
                        .value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(authService, never()).logout(any(), any());
    }

    @Test
    void 유효하지_않은_Access_Token이면_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message")
                        .value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(authService, never()).logout(any(), any());
    }

    @Test
    void 만료된_Access_Token이면_401을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("expired-token"))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_ACCESS_TOKEN_EXPIRED
                ));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer expired-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message")
                        .value("Access Token이 만료되었습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(authService, never()).logout(any(), any());
    }

    @Test
    void 로그아웃_Refresh_Token이_누락되면_400을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("refreshToken"))
                .andExpect(jsonPath("$.data.reason")
                        .value("Refresh Token은 필수입니다."));

        verify(authService, never()).logout(any(), any());
    }

    @Test
    void 다른_회원의_Refresh_Token이면_400을_반환한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(1L);
        doThrow(new BusinessException(
                ErrorCode.AUTH_REFRESH_TOKEN_MEMBER_MISMATCH
        )).when(authService).logout(
                eq(1L),
                any(LogoutRequest.class)
        );

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "other-refresh-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_REFRESH_TOKEN_MEMBER_MISMATCH"))
                .andExpect(jsonPath("$.message")
                        .value("로그아웃 요청 정보가 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 토큰_재발급에_성공하면_200과_새_토큰을_반환한다() throws Exception {
        when(authService.reissue(any(TokenReissueRequest.class)))
                .thenReturn(new TokenReissueResponse(
                        "new-access-token",
                        "new-refresh-token"
                ));

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_TOKEN_REISSUE_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("토큰이 재발급되었습니다."))
                .andExpect(jsonPath("$.data.accessToken")
                        .value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken")
                        .value("new-refresh-token"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void Refresh_Token이_누락되면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.data.field")
                        .value("refreshToken"))
                .andExpect(jsonPath("$.data.reason")
                        .value("Refresh Token은 필수입니다."));

        verify(authService, never()).reissue(any(TokenReissueRequest.class));
    }

    @Test
    void 유효하지_않은_Refresh_Token이면_401을_반환한다() throws Exception {
        assertReissueError(
                ErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                401,
                "AUTH_REFRESH_TOKEN_INVALID",
                "유효하지 않은 Refresh Token입니다."
        );
    }

    @Test
    void 만료된_Refresh_Token이면_401을_반환한다() throws Exception {
        assertReissueError(
                ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED,
                401,
                "AUTH_REFRESH_TOKEN_EXPIRED",
                "로그인이 만료되었습니다. 다시 로그인해 주세요."
        );
    }

    @Test
    void 폐기된_Refresh_Token이면_401을_반환한다() throws Exception {
        assertReissueError(
                ErrorCode.AUTH_REFRESH_TOKEN_REVOKED,
                401,
                "AUTH_REFRESH_TOKEN_REVOKED",
                "사용할 수 없는 Refresh Token입니다."
        );
    }

    @Test
    void 토큰의_회원이_없으면_404를_반환한다() throws Exception {
        assertReissueError(
                ErrorCode.MEMBER_NOT_FOUND,
                404,
                "MEMBER_NOT_FOUND",
                "회원 정보를 찾을 수 없습니다."
        );
    }

    @Test
    void 토큰의_회원이_탈퇴했으면_403을_반환한다() throws Exception {
        assertReissueError(
                ErrorCode.AUTH_MEMBER_WITHDRAWN,
                403,
                "AUTH_MEMBER_WITHDRAWN",
                "탈퇴 처리된 회원입니다."
        );
    }

    @Test
    void 로그인에_성공하면_200과_회원정보와_토큰을_반환한다() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse(
                        1L,
                        "dain@example.com",
                        "루돌푸",
                        null,
                        "PALBANG_RABBIT",
                        true,
                        "access-token",
                        "refresh-token"
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " Dain@Example.COM ",
                                  "password": "Ssafy1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("로그인에 성공했습니다."))
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.email")
                        .value("dain@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("루돌푸"))
                .andExpect(jsonPath("$.data.profileImageUrl")
                        .value(nullValue()))
                .andExpect(jsonPath("$.data.selectedCharacterId")
                        .value("PALBANG_RABBIT"))
                .andExpect(jsonPath("$.data.onboardingCompleted")
                        .value(true))
                .andExpect(jsonPath("$.data.accessToken")
                        .value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken")
                        .value("refresh-token"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 로그인_이메일_형식이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": "Ssafy1234"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.data.field").value("email"))
                .andExpect(jsonPath("$.data.reason")
                        .value("올바른 이메일 형식이 아닙니다."));

        verify(authService, never()).login(any(LoginRequest.class));
    }

    @Test
    void 이메일이나_비밀번호가_일치하지_않으면_401을_반환한다() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_LOGIN_FAILED
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com",
                                  "password": "Wrong1234"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_LOGIN_FAILED"))
                .andExpect(jsonPath("$.message")
                        .value("이메일 또는 비밀번호를 확인해 주세요."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 탈퇴_회원이면_403을_반환한다() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_MEMBER_WITHDRAWN
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com",
                                  "password": "Ssafy1234"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_MEMBER_WITHDRAWN"))
                .andExpect(jsonPath("$.message")
                        .value("탈퇴 처리된 회원입니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 소셜_전용_계정이면_409를_반환한다() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_PASSWORD_LOGIN_NOT_AVAILABLE
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com",
                                  "password": "Ssafy1234"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_PASSWORD_LOGIN_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("간편 로그인을 이용해 주세요."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 기존_소셜_회원이면_200과_서비스_토큰을_반환한다() throws Exception {
        when(authService.socialLogin(any(SocialLoginRequest.class)))
                .thenReturn(new AuthService.SocialLoginResult(
                        AuthSuccessCode.SOCIAL_LOGIN_SUCCESS,
                        new SocialLoginResponse.ExistingMember(
                                false,
                                1L,
                                "social@example.com",
                                "소셜회원",
                                null,
                                "PALBANG",
                                true,
                                "access-token",
                                "refresh-token"
                        )
                ));

        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "authorizationCode": "authorization-code",
                                  "redirectUri": "ssabangpalbang://oauth/kakao"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_SOCIAL_LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("간편 로그인에 성공했습니다."))
                .andExpect(jsonPath("$.data.signupRequired").value(false))
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.email")
                        .value("social@example.com"))
                .andExpect(jsonPath("$.data.onboardingCompleted")
                        .value(true))
                .andExpect(jsonPath("$.data.accessToken")
                        .value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken")
                        .value("refresh-token"))
                .andExpect(jsonPath("$.data.provider").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 신규_소셜_사용자이면_200과_회원가입_토큰을_반환한다() throws Exception {
        when(authService.socialLogin(any(SocialLoginRequest.class)))
                .thenReturn(new AuthService.SocialLoginResult(
                        AuthSuccessCode.SOCIAL_SIGNUP_REQUIRED,
                        new SocialLoginResponse.SignupRequired(
                                true,
                                "NAVER",
                                null,
                                true,
                                "social-signup-token",
                                600
                        )
                ));

        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "NAVER",
                                  "authorizationCode": "authorization-code",
                                  "state": "verified-oauth-state"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_SOCIAL_SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("소셜 회원가입이 필요합니다."))
                .andExpect(jsonPath("$.data.signupRequired").value(true))
                .andExpect(jsonPath("$.data.provider").value("NAVER"))
                .andExpect(jsonPath("$.data.email").value(nullValue()))
                .andExpect(jsonPath("$.data.emailRequired").value(true))
                .andExpect(jsonPath("$.data.socialSignupToken")
                        .value("social-signup-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(600))
                .andExpect(jsonPath("$.data.memberId").doesNotExist());
    }

    @Test
    void 소셜_인가_코드가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "authorizationCode": "",
                                  "redirectUri": "ssabangpalbang://oauth/kakao"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("authorizationCode"))
                .andExpect(jsonPath("$.data.reason")
                        .value("인가 코드는 필수입니다."));

        verify(authService, never()).socialLogin(any());
    }

    @Test
    void 지원하지_않는_소셜_제공자이면_400을_반환한다() throws Exception {
        when(authService.socialLogin(any(SocialLoginRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_SOCIAL_PROVIDER_INVALID,
                        Map.of("allowedValues", new String[]{"NAVER", "KAKAO"})
                ));

        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "authorizationCode": "authorization-code"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_SOCIAL_PROVIDER_INVALID"))
                .andExpect(jsonPath("$.data.allowedValues[0]")
                        .value("NAVER"))
                .andExpect(jsonPath("$.data.allowedValues[1]")
                        .value("KAKAO"));
    }

    @Test
    void 소셜_제공자_장애이면_503을_반환한다() throws Exception {
        when(authService.socialLogin(any(SocialLoginRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE
                ));

        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "NAVER",
                                  "authorizationCode": "authorization-code",
                                  "state": "verified-oauth-state"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_SOCIAL_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 소셜_인가_코드_검증에_실패하면_401을_반환한다() throws Exception {
        when(authService.socialLogin(any(SocialLoginRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_SOCIAL_AUTHENTICATION_FAILED
                ));

        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "authorizationCode": "invalid-code",
                                  "redirectUri": "ssabangpalbang://oauth/kakao"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_SOCIAL_AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message")
                        .value("간편 로그인 인증에 실패했습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 탈퇴한_소셜_회원이면_403을_반환한다() throws Exception {
        when(authService.socialLogin(any(SocialLoginRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_MEMBER_WITHDRAWN
                ));

        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "NAVER",
                                  "authorizationCode": "authorization-code",
                                  "state": "verified-oauth-state"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_MEMBER_WITHDRAWN"))
                .andExpect(jsonPath("$.message")
                        .value("탈퇴 처리된 회원입니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 소셜_회원가입에_성공하면_201과_서비스_토큰을_반환한다() throws Exception {
        when(authService.socialSignup(any(SocialSignupRequest.class)))
                .thenReturn(new SocialSignupResponse(
                        2L,
                        "social@example.com",
                        "루돌푸",
                        "KAKAO",
                        "PALBANG",
                        false,
                        "access-token",
                        "refresh-token"
                ));

        mockMvc.perform(post("/api/v1/auth/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialSignupToken": "social-signup-token",
                                  "nickname": "루돌푸"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_SOCIAL_SIGNUP_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("소셜 회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.data.memberId").value(2L))
                .andExpect(jsonPath("$.data.email")
                        .value("social@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("루돌푸"))
                .andExpect(jsonPath("$.data.provider").value("KAKAO"))
                .andExpect(jsonPath("$.data.selectedCharacterId")
                        .value("PALBANG"))
                .andExpect(jsonPath("$.data.onboardingCompleted")
                        .value(false))
                .andExpect(jsonPath("$.data.accessToken")
                        .value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken")
                        .value("refresh-token"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 소셜_이메일이_필요하면_400과_email_필드를_반환한다() throws Exception {
        when(authService.socialSignup(any(SocialSignupRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED,
                        Map.of("field", "email")
                ));

        mockMvc.perform(post("/api/v1/auth/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialSignupToken": "social-signup-token",
                                  "nickname": "루돌푸"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_SOCIAL_EMAIL_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("회원가입에 사용할 이메일을 입력해 주세요."))
                .andExpect(jsonPath("$.data.field").value("email"));
    }

    @Test
    void 소셜_회원가입_이메일_형식이_틀리면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialSignupToken": "social-signup-token",
                                  "email": "invalid-email",
                                  "nickname": "루돌푸"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("email"))
                .andExpect(jsonPath("$.data.reason")
                        .value("올바른 이메일 형식이 아닙니다."));

        verify(authService, never()).socialSignup(any());
    }

    @Test
    void 소셜_회원가입_토큰이_만료되면_401을_반환한다() throws Exception {
        when(authService.socialSignup(any(SocialSignupRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_SOCIAL_SIGNUP_TOKEN_EXPIRED
                ));

        mockMvc.perform(post("/api/v1/auth/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialSignupToken": "expired-token",
                                  "nickname": "루돌푸"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_SOCIAL_SIGNUP_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message")
                        .value("소셜 인증 정보가 만료되었습니다. 다시 로그인해 주세요."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 이미_가입된_소셜_계정이면_409를_반환한다() throws Exception {
        when(authService.socialSignup(any(SocialSignupRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_SOCIAL_ACCOUNT_ALREADY_EXISTS
                ));

        mockMvc.perform(post("/api/v1/auth/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialSignupToken": "social-signup-token",
                                  "nickname": "루돌푸"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_SOCIAL_ACCOUNT_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("이미 가입된 간편 로그인 계정입니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 회원가입에_성공하면_201과_토큰을_반환한다() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenReturn(new SignupResponse(
                        1L,
                        "dain@example.com",
                        "루돌푸",
                        "PALBANG",
                        false,
                        "access-token",
                        "refresh-token"
                ));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " Dain@Example.COM ",
                                  "password": "Ssafy1234",
                                  "nickname": "루돌푸"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_SIGNUP_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.email")
                        .value("dain@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("루돌푸"))
                .andExpect(jsonPath("$.data.selectedCharacterId")
                        .value("PALBANG"))
                .andExpect(jsonPath("$.data.onboardingCompleted")
                        .value(false))
                .andExpect(jsonPath("$.data.accessToken")
                        .value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken")
                        .value("refresh-token"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 비밀번호_규칙을_위반하면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com",
                                  "password": "short",
                                  "nickname": "루돌푸"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.data.field").value("password"))
                .andExpect(jsonPath("$.data.reason")
                        .value("비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다."));

        verify(authService, never()).signup(any(SignupRequest.class));
    }

    @Test
    void 읽을_수_없는_JSON이면_명세의_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(authService, never()).signup(any(SignupRequest.class));
    }

    @Test
    void 이메일이_중복되면_409를_반환한다() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_EMAIL_DUPLICATED
                ));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com",
                                  "password": "Ssafy1234",
                                  "nickname": "루돌푸"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_EMAIL_DUPLICATED"))
                .andExpect(jsonPath("$.message")
                        .value("이미 사용 중인 이메일입니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 비밀번호_재설정_코드_요청에_성공하면_200과_일반_메시지를_반환한다()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " Dain@Example.COM "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_PASSWORD_RESET_REQUESTED"))
                .andExpect(jsonPath("$.message")
                        .value("입력하신 이메일로 재설정 코드를 보냈어요."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(passwordResetService).requestPasswordReset(
                any(PasswordResetCodeRequest.class)
        );
    }

    @Test
    void 비밀번호_재설정_코드_요청_이메일_형식이_잘못되면_400을_반환한다()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("email"))
                .andExpect(jsonPath("$.data.reason")
                        .value("올바른 이메일 형식이 아닙니다."));

        verify(passwordResetService, never()).requestPasswordReset(any());
    }

    @Test
    void 비밀번호_재설정_확정에_성공하면_200과_성공_메시지를_반환한다()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com",
                                  "code": "123456",
                                  "newPassword": "Ssafy5678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_PASSWORD_RESET_SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(passwordResetService).confirmPasswordReset(
                any(PasswordResetConfirmRequest.class)
        );
    }

    @Test
    void 비밀번호_재설정_확정_필수값이_누락되면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com",
                                  "newPassword": "Ssafy5678"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("code"))
                .andExpect(jsonPath("$.data.reason")
                        .value("인증 코드는 필수입니다."));

        verify(passwordResetService, never()).confirmPasswordReset(any());
    }

    @Test
    void 비밀번호_재설정_코드가_유효하지_않으면_400을_반환한다() throws Exception {
        doThrow(new BusinessException(
                ErrorCode.AUTH_PASSWORD_RESET_CODE_INVALID
        )).when(passwordResetService).confirmPasswordReset(
                any(PasswordResetConfirmRequest.class)
        );

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com",
                                  "code": "000000",
                                  "newPassword": "Ssafy5678"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_PASSWORD_RESET_CODE_INVALID"))
                .andExpect(jsonPath("$.message")
                        .value("인증 코드가 올바르지 않거나 만료되었습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 비밀번호_재설정_대상이_소셜_전용_계정이면_409를_반환한다() throws Exception {
        doThrow(new BusinessException(
                ErrorCode.AUTH_PASSWORD_LOGIN_NOT_AVAILABLE
        )).when(passwordResetService).confirmPasswordReset(
                any(PasswordResetConfirmRequest.class)
        );

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "social@example.com",
                                  "code": "123456",
                                  "newPassword": "Ssafy5678"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_PASSWORD_LOGIN_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("간편 로그인을 이용해 주세요."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 이메일_사용_가능이면_200과_available_true를_반환한다() throws Exception {
        when(authService.checkEmailAvailability("dain@example.com"))
                .thenReturn(new AvailabilityResponse(true));

        mockMvc.perform(get("/api/v1/auth/check-email")
                        .param("email", "dain@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_EMAIL_AVAILABILITY_SUCCESS"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 이메일_중복이면_200과_available_false를_반환한다() throws Exception {
        when(authService.checkEmailAvailability("dain@example.com"))
                .thenReturn(new AvailabilityResponse(false));

        mockMvc.perform(get("/api/v1/auth/check-email")
                        .param("email", "dain@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void 이메일_형식이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-email")
                        .param("email", "invalid-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("email"))
                .andExpect(jsonPath("$.data.reason")
                        .value("올바른 이메일 형식이 아닙니다."));

        verify(authService, never()).checkEmailAvailability(any());
    }

    @Test
    void 이메일_파라미터가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-email")
                        .param("email", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("email"));

        verify(authService, never()).checkEmailAvailability(any());
    }

    @Test
    void 닉네임_사용_가능이면_200과_available_true를_반환한다() throws Exception {
        when(authService.checkNicknameAvailability("루돌푸"))
                .thenReturn(new AvailabilityResponse(true));

        mockMvc.perform(get("/api/v1/auth/check-nickname")
                        .param("nickname", "루돌푸"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_NICKNAME_AVAILABILITY_SUCCESS"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 닉네임_중복이면_200과_available_false를_반환한다() throws Exception {
        when(authService.checkNicknameAvailability("루돌푸"))
                .thenReturn(new AvailabilityResponse(false));

        mockMvc.perform(get("/api/v1/auth/check-nickname")
                        .param("nickname", "루돌푸"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void 닉네임_파라미터가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-nickname")
                        .param("nickname", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("nickname"))
                .andExpect(jsonPath("$.data.reason")
                        .value("닉네임은 필수입니다."));

        verify(authService, never()).checkNicknameAvailability(any());
    }

    @Test
    void 재설정_코드_검증에_성공하면_200과_valid_true를_반환한다() throws Exception {
        when(passwordResetService.verifyCode(
                any(PasswordResetVerifyRequest.class)
        )).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_PASSWORD_RESET_CODE_VERIFY_SUCCESS"))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 재설정_코드가_틀리면_200과_valid_false를_반환한다() throws Exception {
        when(passwordResetService.verifyCode(
                any(PasswordResetVerifyRequest.class)
        )).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com",
                                  "code": "000000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(false));
    }

    @Test
    void 재설정_코드_검증_필수값이_누락되면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("code"))
                .andExpect(jsonPath("$.data.reason")
                        .value("인증 코드는 필수입니다."));

        verify(passwordResetService, never()).verifyCode(any());
    }

    @Test
    void 재설정_코드_검증_시도_횟수가_초과되면_400을_반환한다() throws Exception {
        when(passwordResetService.verifyCode(
                any(PasswordResetVerifyRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.AUTH_PASSWORD_RESET_CODE_INVALID
        ));

        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dain@example.com",
                                  "code": "000000"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("AUTH_PASSWORD_RESET_CODE_INVALID"))
                .andExpect(jsonPath("$.message")
                        .value("인증 코드가 올바르지 않거나 만료되었습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private void assertReissueError(
            ErrorCode errorCode,
            int statusCode,
            String code,
            String message
    ) throws Exception {
        when(authService.reissue(any(TokenReissueRequest.class)))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
