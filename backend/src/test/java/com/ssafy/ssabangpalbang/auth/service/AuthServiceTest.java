package com.ssafy.ssabangpalbang.auth.service;

import com.ssafy.ssabangpalbang.auth.code.AuthSuccessCode;
import com.ssafy.ssabangpalbang.auth.domain.SocialAccount;
import com.ssafy.ssabangpalbang.auth.domain.SocialProvider;
import com.ssafy.ssabangpalbang.auth.dto.request.LoginRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.LogoutRequest;
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
import com.ssafy.ssabangpalbang.auth.oauth.SocialOAuthAuthorization;
import com.ssafy.ssabangpalbang.auth.oauth.SocialOAuthClientRegistry;
import com.ssafy.ssabangpalbang.auth.oauth.SocialOAuthUser;
import com.ssafy.ssabangpalbang.auth.repository.SocialAccountRepository;
import com.ssafy.ssabangpalbang.auth.repository.SocialMemberSnapshot;
import com.ssafy.ssabangpalbang.auth.token.IssuedSocialSignupToken;
import com.ssafy.ssabangpalbang.auth.token.IssuedTokens;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.auth.token.RefreshTokenStore;
import com.ssafy.ssabangpalbang.auth.token.SocialSignupTokenClaims;
import com.ssafy.ssabangpalbang.auth.token.SocialSignupTokenStore;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hibernate.exception.ConstraintViolationException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Validator VALIDATOR = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private SocialOAuthClientRegistry socialOAuthClientRegistry;

    @Mock
    private SocialSignupTokenStore socialSignupTokenStore;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                memberRepository,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenStore,
                socialAccountRepository,
                socialOAuthClientRegistry,
                socialSignupTokenStore,
                VALIDATOR
        );
    }

    @Test
    void 같은_회원의_Refresh_Token을_폐기한다() {
        LogoutRequest request = new LogoutRequest("refresh-token");
        when(jwtTokenProvider.parseRefreshToken("refresh-token"))
                .thenReturn(1L);

        authService.logout(1L, request);

        verify(refreshTokenStore).revoke(1L, "refresh-token");
        verifyNoInteractions(memberRepository, passwordEncoder);
    }

    @Test
    void 이미_폐기된_Refresh_Token도_로그아웃에_성공한다() {
        LogoutRequest request = new LogoutRequest("revoked-token");
        when(jwtTokenProvider.parseRefreshToken("revoked-token"))
                .thenReturn(1L);
        when(refreshTokenStore.revoke(1L, "revoked-token"))
                .thenReturn(false);

        authService.logout(1L, request);

        verify(refreshTokenStore).revoke(1L, "revoked-token");
    }

    @Test
    void 다른_회원의_Refresh_Token이면_로그아웃을_거절한다() {
        LogoutRequest request = new LogoutRequest("other-refresh-token");
        when(jwtTokenProvider.parseRefreshToken("other-refresh-token"))
                .thenReturn(2L);

        assertThatThrownBy(() -> authService.logout(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_MEMBER_MISMATCH);

        verify(refreshTokenStore, never()).revoke(any(), any());
    }

    @Test
    void 유효하지_않은_Refresh_Token이면_로그아웃을_거절한다() {
        LogoutRequest request = new LogoutRequest("invalid-token");
        when(jwtTokenProvider.parseRefreshToken("invalid-token"))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_REFRESH_TOKEN_INVALID
                ));

        assertThatThrownBy(() -> authService.logout(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);

        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void 유효한_Refresh_Token을_회전하고_새_토큰을_반환한다() {
        TokenReissueRequest request = new TokenReissueRequest("old-refresh");
        Member member = member(1L, "encoded-password");
        IssuedTokens tokens = new IssuedTokens(
                "new-access",
                "new-refresh",
                Duration.ofDays(30)
        );

        when(jwtTokenProvider.parseRefreshToken("old-refresh"))
                .thenReturn(1L);
        when(refreshTokenStore.matches(1L, "old-refresh"))
                .thenReturn(true);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(jwtTokenProvider.issue(1L)).thenReturn(tokens);
        when(refreshTokenStore.rotate(
                1L,
                "old-refresh",
                "new-refresh",
                Duration.ofDays(30)
        )).thenReturn(true);

        TokenReissueResponse response = authService.reissue(request);

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenStore).rotate(
                1L,
                "old-refresh",
                "new-refresh",
                Duration.ofDays(30)
        );
    }

    @Test
    void Redis에_일치하는_Refresh_Token이_없으면_폐기_오류를_반환한다() {
        TokenReissueRequest request = new TokenReissueRequest("revoked");

        when(jwtTokenProvider.parseRefreshToken("revoked"))
                .thenReturn(1L);
        when(refreshTokenStore.matches(1L, "revoked"))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_REVOKED);

        verifyNoInteractions(memberRepository);
        verify(jwtTokenProvider, never()).issue(any());
        verify(refreshTokenStore, never()).rotate(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void Refresh_Token의_회원이_없으면_404_오류를_반환한다() {
        TokenReissueRequest request = new TokenReissueRequest("refresh-token");

        when(jwtTokenProvider.parseRefreshToken("refresh-token"))
                .thenReturn(99L);
        when(refreshTokenStore.matches(99L, "refresh-token"))
                .thenReturn(true);
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verify(jwtTokenProvider, never()).issue(any());
        verify(refreshTokenStore, never()).rotate(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void 탈퇴_회원은_토큰을_재발급할_수_없다() {
        TokenReissueRequest request = new TokenReissueRequest("refresh-token");
        Member member = member(1L, "encoded-password");
        ReflectionTestUtils.setField(
                member,
                "status",
                MemberStatus.WITHDRAWN
        );

        when(jwtTokenProvider.parseRefreshToken("refresh-token"))
                .thenReturn(1L);
        when(refreshTokenStore.matches(1L, "refresh-token"))
                .thenReturn(true);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_MEMBER_WITHDRAWN);

        verify(jwtTokenProvider, never()).issue(any());
        verify(refreshTokenStore, never()).rotate(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void 동시_요청에서_토큰_회전에_실패하면_재사용_오류를_반환한다() {
        TokenReissueRequest request = new TokenReissueRequest("old-refresh");
        Member member = member(1L, "encoded-password");
        IssuedTokens tokens = new IssuedTokens(
                "new-access",
                "new-refresh",
                Duration.ofDays(30)
        );

        when(jwtTokenProvider.parseRefreshToken("old-refresh"))
                .thenReturn(1L);
        when(refreshTokenStore.matches(1L, "old-refresh"))
                .thenReturn(true);
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(jwtTokenProvider.issue(1L)).thenReturn(tokens);
        when(refreshTokenStore.rotate(
                1L,
                "old-refresh",
                "new-refresh",
                Duration.ofDays(30)
        )).thenReturn(false);

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_REVOKED);
    }

    @Test
    void 로그인_정보를_검증하고_온보딩_상태와_토큰을_반환한다() {
        LoginRequest request = new LoginRequest(
                " Dain@Example.COM ",
                "Ssafy1234"
        );
        Member member = member(
                1L,
                "encoded-password"
        );
        IssuedTokens tokens = new IssuedTokens(
                "access-token",
                "refresh-token",
                Duration.ofDays(30)
        );

        when(memberRepository.findByEmail("dain@example.com"))
                .thenReturn(Optional.of(member));
        when(passwordEncoder.matches(
                "Ssafy1234",
                "encoded-password"
        )).thenReturn(true);
        when(memberRepository.existsPreferenceByMemberId(1L))
                .thenReturn(true);
        when(jwtTokenProvider.issue(1L)).thenReturn(tokens);

        LoginResponse response = authService.login(request);

        verify(refreshTokenStore).save(
                1L,
                "refresh-token",
                Duration.ofDays(30)
        );
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("dain@example.com");
        assertThat(response.nickname()).isEqualTo("루돌푸");
        assertThat(response.profileImageUrl()).isNull();
        assertThat(response.selectedCharacterId()).isEqualTo("PALBANG");
        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void 회원이_없으면_이메일과_비밀번호를_구분하지_않고_로그인을_거절한다() {
        LoginRequest request = new LoginRequest(
                "unknown@example.com",
                "Ssafy1234"
        );
        when(memberRepository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);

        verifyNoInteractions(
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenStore
        );
    }

    @Test
    void 비밀번호가_일치하지_않으면_토큰을_발급하지_않는다() {
        LoginRequest request = new LoginRequest(
                "dain@example.com",
                "Wrong1234"
        );
        Member member = member(1L, "encoded-password");

        when(memberRepository.findByEmail("dain@example.com"))
                .thenReturn(Optional.of(member));
        when(passwordEncoder.matches(
                "Wrong1234",
                "encoded-password"
        )).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);

        verify(memberRepository, never()).existsPreferenceByMemberId(any());
        verify(jwtTokenProvider, never()).issue(any());
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void 탈퇴_회원은_비밀번호가_일치해도_로그인을_거절한다() {
        LoginRequest request = new LoginRequest(
                "dain@example.com",
                "Ssafy1234"
        );
        Member member = member(1L, "encoded-password");
        ReflectionTestUtils.setField(
                member,
                "status",
                MemberStatus.WITHDRAWN
        );

        when(memberRepository.findByEmail("dain@example.com"))
                .thenReturn(Optional.of(member));
        when(passwordEncoder.matches(
                "Ssafy1234",
                "encoded-password"
        )).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_MEMBER_WITHDRAWN);

        verify(memberRepository, never()).existsPreferenceByMemberId(any());
        verify(jwtTokenProvider, never()).issue(any());
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void 비밀번호가_없는_소셜_전용_계정은_일반_로그인을_거절한다() {
        LoginRequest request = new LoginRequest(
                "social@example.com",
                "Ssafy1234"
        );
        Member member = member(1L, null);

        when(memberRepository.findByEmail("social@example.com"))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_PASSWORD_LOGIN_NOT_AVAILABLE);

        verifyNoInteractions(
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenStore
        );
        verify(memberRepository, never()).existsPreferenceByMemberId(any());
    }

    @Test
    void 기존_카카오_회원이면_서비스_토큰과_회원정보를_반환한다() {
        SocialLoginRequest request = new SocialLoginRequest(
                "KAKAO",
                "authorization-code",
                "ssabangpalbang://oauth/kakao",
                null
        );
        SocialMemberSnapshot member = new SocialMemberSnapshot(
                1L,
                "social@example.com",
                "소셜회원",
                null,
                "PALBANG",
                MemberStatus.ACTIVE
        );
        IssuedTokens tokens = new IssuedTokens(
                "access-token",
                "refresh-token",
                Duration.ofDays(30)
        );

        when(socialOAuthClientRegistry.authenticate(
                eq(SocialProvider.KAKAO),
                any(SocialOAuthAuthorization.class)
        )).thenReturn(new SocialOAuthUser(
                "kakao-user-id",
                "SOCIAL@EXAMPLE.COM"
        ));
        when(socialAccountRepository.findMemberSnapshot(
                SocialProvider.KAKAO,
                "kakao-user-id"
        )).thenReturn(Optional.of(member));
        when(memberRepository.existsPreferenceByMemberId(1L))
                .thenReturn(true);
        when(jwtTokenProvider.issue(1L)).thenReturn(tokens);

        AuthService.SocialLoginResult result = authService.socialLogin(request);

        assertThat(result.responseCode())
                .isEqualTo(AuthSuccessCode.SOCIAL_LOGIN_SUCCESS);
        assertThat(result.response())
                .isInstanceOf(SocialLoginResponse.ExistingMember.class);
        SocialLoginResponse.ExistingMember response =
                (SocialLoginResponse.ExistingMember) result.response();
        assertThat(response.signupRequired()).isFalse();
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("social@example.com");
        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenStore).save(
                1L,
                "refresh-token",
                Duration.ofDays(30)
        );
    }

    @Test
    void 신규_네이버_사용자이면_10분짜리_회원가입_토큰을_반환한다() {
        SocialLoginRequest request = new SocialLoginRequest(
                "NAVER",
                "authorization-code",
                null,
                "verified-oauth-state"
        );

        when(socialOAuthClientRegistry.authenticate(
                eq(SocialProvider.NAVER),
                any(SocialOAuthAuthorization.class)
        )).thenReturn(new SocialOAuthUser("naver-user-id", null));
        when(socialAccountRepository.findMemberSnapshot(
                SocialProvider.NAVER,
                "naver-user-id"
        )).thenReturn(Optional.empty());
        when(jwtTokenProvider.issueSocialSignupToken(
                "NAVER",
                "naver-user-id",
                null
        )).thenReturn(new IssuedSocialSignupToken(
                "social-signup-token",
                600
        ));

        AuthService.SocialLoginResult result = authService.socialLogin(request);

        assertThat(result.responseCode())
                .isEqualTo(AuthSuccessCode.SOCIAL_SIGNUP_REQUIRED);
        assertThat(result.response())
                .isInstanceOf(SocialLoginResponse.SignupRequired.class);
        SocialLoginResponse.SignupRequired response =
                (SocialLoginResponse.SignupRequired) result.response();
        assertThat(response.signupRequired()).isTrue();
        assertThat(response.provider()).isEqualTo("NAVER");
        assertThat(response.email()).isNull();
        assertThat(response.emailRequired()).isTrue();
        assertThat(response.socialSignupToken())
                .isEqualTo("social-signup-token");
        assertThat(response.expiresIn()).isEqualTo(600);
        verify(socialSignupTokenStore).save(
                "social-signup-token",
                Duration.ofMinutes(10)
        );
        verifyNoInteractions(memberRepository, refreshTokenStore);
    }

    @Test
    void 제공자_이메일로_소셜_회원과_계정을_생성한다() {
        SocialSignupRequest request = new SocialSignupRequest(
                "social-signup-token",
                "ignored@example.com",
                " 루돌푸 "
        );
        IssuedTokens tokens = new IssuedTokens(
                "access-token",
                "refresh-token",
                Duration.ofDays(30)
        );
        when(jwtTokenProvider.parseSocialSignupToken(
                "social-signup-token"
        )).thenReturn(new SocialSignupTokenClaims(
                SocialProvider.KAKAO,
                "kakao-user-id",
                "social@example.com"
        ));
        when(socialSignupTokenStore.consume("social-signup-token"))
                .thenReturn(true);
        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenAnswer(invocation -> {
                    Member member = invocation.getArgument(0);
                    ReflectionTestUtils.setField(member, "id", 2L);
                    return member;
                });
        when(jwtTokenProvider.issue(2L)).thenReturn(tokens);

        SocialSignupResponse response = authService.socialSignup(request);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(
                Member.class
        );
        ArgumentCaptor<SocialAccount> socialAccountCaptor =
                ArgumentCaptor.forClass(SocialAccount.class);
        verify(memberRepository).saveAndFlush(memberCaptor.capture());
        verify(socialAccountRepository).saveAndFlush(
                socialAccountCaptor.capture()
        );

        Member savedMember = memberCaptor.getValue();
        SocialAccount savedSocialAccount = socialAccountCaptor.getValue();
        assertThat(savedMember.getEmail()).isEqualTo("social@example.com");
        assertThat(savedMember.getPasswordHash()).isNull();
        assertThat(savedMember.getNickname()).isEqualTo("루돌푸");
        assertThat(savedMember.getSelectedCharacterId()).isEqualTo("PALBANG");
        assertThat(savedMember.isServiceNotificationAgreed()).isTrue();
        assertThat(savedMember.isAdNotificationAgreed()).isFalse();
        assertThat(savedMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(savedSocialAccount.getMember()).isSameAs(savedMember);
        assertThat(savedSocialAccount.getProvider())
                .isEqualTo(SocialProvider.KAKAO);
        assertThat(savedSocialAccount.getSocialUserId())
                .isEqualTo("kakao-user-id");
        assertThat(savedSocialAccount.getEmail())
                .isEqualTo("social@example.com");

        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.email()).isEqualTo("social@example.com");
        assertThat(response.nickname()).isEqualTo("루돌푸");
        assertThat(response.provider()).isEqualTo("KAKAO");
        assertThat(response.selectedCharacterId()).isEqualTo("PALBANG");
        assertThat(response.onboardingCompleted()).isFalse();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenStore).save(
                2L,
                "refresh-token",
                Duration.ofDays(30)
        );
    }

    @Test
    void 제공자_이메일이_없으면_요청_이메일을_정규화해_사용한다() {
        SocialSignupRequest request = new SocialSignupRequest(
                "social-signup-token",
                " Dain@Example.COM ",
                "루돌푸"
        );
        when(jwtTokenProvider.parseSocialSignupToken(
                "social-signup-token"
        )).thenReturn(new SocialSignupTokenClaims(
                SocialProvider.NAVER,
                "naver-user-id",
                null
        ));
        when(socialSignupTokenStore.consume("social-signup-token"))
                .thenReturn(true);
        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenAnswer(invocation -> {
                    Member member = invocation.getArgument(0);
                    ReflectionTestUtils.setField(member, "id", 3L);
                    return member;
                });
        when(jwtTokenProvider.issue(3L)).thenReturn(new IssuedTokens(
                "access-token",
                "refresh-token",
                Duration.ofDays(30)
        ));

        authService.socialSignup(request);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(
                Member.class
        );
        ArgumentCaptor<SocialAccount> socialAccountCaptor =
                ArgumentCaptor.forClass(SocialAccount.class);
        verify(memberRepository).saveAndFlush(memberCaptor.capture());
        verify(socialAccountRepository).saveAndFlush(
                socialAccountCaptor.capture()
        );
        assertThat(memberCaptor.getValue().getEmail())
                .isEqualTo("dain@example.com");
        assertThat(socialAccountCaptor.getValue().getEmail()).isNull();
    }

    @Test
    void 제공자와_요청에_이메일이_모두_없으면_입력을_요청한다() {
        SocialSignupRequest request = new SocialSignupRequest(
                "social-signup-token",
                null,
                "루돌푸"
        );
        when(jwtTokenProvider.parseSocialSignupToken(
                "social-signup-token"
        )).thenReturn(new SocialSignupTokenClaims(
                SocialProvider.NAVER,
                "naver-user-id",
                null
        ));

        assertThatThrownBy(() -> authService.socialSignup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED);
                    assertThat(businessException.getData())
                            .containsEntry("field", "email");
                });

        verify(socialSignupTokenStore, never()).consume(any());
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void 소셜_제공자_이메일_형식이_올바르지_않으면_거절한다() {
        SocialSignupRequest request = new SocialSignupRequest(
                "social-signup-token",
                null,
                "루돌푸"
        );
        when(jwtTokenProvider.parseSocialSignupToken(
                "social-signup-token"
        )).thenReturn(new SocialSignupTokenClaims(
                SocialProvider.KAKAO,
                "kakao-user-id",
                "invalid-email"
        ));

        assertThatThrownBy(() -> authService.socialSignup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(businessException.getData())
                            .containsEntry("field", "email")
                            .containsEntry(
                                    "reason",
                                    "올바른 이메일 형식이 아닙니다."
                            );
                });

        verify(socialSignupTokenStore, never()).consume(any());
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void 이미_가입된_소셜_계정이면_회원_생성을_거절한다() {
        SocialSignupRequest request = new SocialSignupRequest(
                "social-signup-token",
                null,
                "루돌푸"
        );
        when(jwtTokenProvider.parseSocialSignupToken(
                "social-signup-token"
        )).thenReturn(new SocialSignupTokenClaims(
                SocialProvider.KAKAO,
                "kakao-user-id",
                "social@example.com"
        ));
        when(socialAccountRepository.existsByProviderAndSocialUserId(
                SocialProvider.KAKAO,
                "kakao-user-id"
        )).thenReturn(true);

        assertThatThrownBy(() -> authService.socialSignup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_SOCIAL_ACCOUNT_ALREADY_EXISTS);

        verify(socialSignupTokenStore, never()).consume(any());
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void 이미_소비된_소셜_회원가입_토큰은_재사용할_수_없다() {
        SocialSignupRequest request = new SocialSignupRequest(
                "used-social-signup-token",
                null,
                "루돌푸"
        );
        when(jwtTokenProvider.parseSocialSignupToken(
                "used-social-signup-token"
        )).thenReturn(new SocialSignupTokenClaims(
                SocialProvider.KAKAO,
                "kakao-user-id",
                "social@example.com"
        ));
        when(socialSignupTokenStore.consume(
                "used-social-signup-token"
        )).thenReturn(false);

        assertThatThrownBy(() -> authService.socialSignup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_SOCIAL_SIGNUP_TOKEN_INVALID);

        verify(memberRepository, never()).saveAndFlush(any());
        verify(socialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_요청의_소셜_계정_UNIQUE_충돌을_중복_오류로_변환한다() {
        SocialSignupRequest request = new SocialSignupRequest(
                "social-signup-token",
                null,
                "루돌푸"
        );
        when(jwtTokenProvider.parseSocialSignupToken(
                "social-signup-token"
        )).thenReturn(new SocialSignupTokenClaims(
                SocialProvider.KAKAO,
                "kakao-user-id",
                "social@example.com"
        ));
        when(socialSignupTokenStore.consume("social-signup-token"))
                .thenReturn(true);
        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenAnswer(invocation -> {
                    Member member = invocation.getArgument(0);
                    ReflectionTestUtils.setField(member, "id", 4L);
                    return member;
                });
        when(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
                .thenThrow(uniqueConstraintViolation(
                        "social_account_provider_social_user_id_key"
                ));

        assertThatThrownBy(() -> authService.socialSignup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_SOCIAL_ACCOUNT_ALREADY_EXISTS);

        verify(jwtTokenProvider, never()).issue(any());
        verify(refreshTokenStore, never()).save(any(), any(), any());
    }

    @Test
    void 카카오_Redirect_URI가_없으면_요청을_거절한다() {
        SocialLoginRequest request = new SocialLoginRequest(
                "KAKAO",
                "authorization-code",
                null,
                null
        );

        assertThatThrownBy(() -> authService.socialLogin(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(
                socialOAuthClientRegistry,
                socialAccountRepository
        );
    }

    @Test
    void 네이버_state가_없으면_요청을_거절한다() {
        SocialLoginRequest request = new SocialLoginRequest(
                "NAVER",
                "authorization-code",
                null,
                " "
        );

        assertThatThrownBy(() -> authService.socialLogin(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(
                socialOAuthClientRegistry,
                socialAccountRepository
        );
    }

    @Test
    void 지원하지_않는_소셜_제공자는_거절한다() {
        SocialLoginRequest request = new SocialLoginRequest(
                "GOOGLE",
                "authorization-code",
                null,
                null
        );

        assertThatThrownBy(() -> authService.socialLogin(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_SOCIAL_PROVIDER_INVALID);

        verifyNoInteractions(
                socialOAuthClientRegistry,
                socialAccountRepository
        );
    }

    @Test
    void 탈퇴한_소셜_회원은_로그인할_수_없다() {
        SocialLoginRequest request = new SocialLoginRequest(
                "KAKAO",
                "authorization-code",
                "ssabangpalbang://oauth/kakao",
                null
        );
        SocialMemberSnapshot withdrawnMember = new SocialMemberSnapshot(
                1L,
                "withdrawn@example.com",
                "탈퇴회원",
                null,
                "PALBANG",
                MemberStatus.WITHDRAWN
        );

        when(socialOAuthClientRegistry.authenticate(
                eq(SocialProvider.KAKAO),
                any(SocialOAuthAuthorization.class)
        )).thenReturn(new SocialOAuthUser(
                "withdrawn-kakao-user",
                "withdrawn@example.com"
        ));
        when(socialAccountRepository.findMemberSnapshot(
                SocialProvider.KAKAO,
                "withdrawn-kakao-user"
        )).thenReturn(Optional.of(withdrawnMember));

        assertThatThrownBy(() -> authService.socialLogin(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_MEMBER_WITHDRAWN);

        verify(jwtTokenProvider, never()).issue(any());
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void 회원을_생성하고_토큰을_발급한다() {
        SignupRequest request = new SignupRequest(
                " Dain@Example.COM ",
                "Ssafy1234",
                "루돌푸"
        );
        IssuedTokens tokens = new IssuedTokens(
                "access-token",
                "refresh-token",
                Duration.ofDays(30)
        );

        when(memberRepository.existsByEmail("dain@example.com"))
                .thenReturn(false);
        when(memberRepository.existsByNickname("루돌푸"))
                .thenReturn(false);
        when(passwordEncoder.encode("Ssafy1234"))
                .thenReturn("encoded-password");
        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenAnswer(invocation -> {
                    Member member = invocation.getArgument(0);
                    ReflectionTestUtils.setField(member, "id", 1L);
                    return member;
                });
        when(jwtTokenProvider.issue(1L)).thenReturn(tokens);

        SignupResponse response = authService.signup(request);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(
                Member.class
        );
        verify(memberRepository).saveAndFlush(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();

        assertThat(savedMember.getEmail()).isEqualTo("dain@example.com");
        assertThat(savedMember.getPasswordHash())
                .isEqualTo("encoded-password")
                .isNotEqualTo(request.password());
        assertThat(savedMember.getNickname()).isEqualTo("루돌푸");
        assertThat(savedMember.getSelectedCharacterId()).isEqualTo("PALBANG");
        assertThat(savedMember.isServiceNotificationAgreed()).isTrue();
        assertThat(savedMember.isAdNotificationAgreed()).isFalse();

        verify(refreshTokenStore).save(
                1L,
                "refresh-token",
                Duration.ofDays(30)
        );

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("dain@example.com");
        assertThat(response.onboardingCompleted()).isFalse();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void 정규화된_이메일이_중복되면_회원가입을_거절한다() {
        SignupRequest request = new SignupRequest(
                " Dain@Example.COM ",
                "Ssafy1234",
                "루돌푸"
        );
        when(memberRepository.existsByEmail("dain@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_EMAIL_DUPLICATED);

        verify(memberRepository, never()).saveAndFlush(any(Member.class));
        verify(jwtTokenProvider, never()).issue(any());
    }

    @Test
    void 닉네임이_중복되면_회원가입을_거절한다() {
        SignupRequest request = new SignupRequest(
                "dain@example.com",
                "Ssafy1234",
                "루돌푸"
        );
        when(memberRepository.existsByEmail("dain@example.com"))
                .thenReturn(false);
        when(memberRepository.existsByNickname("루돌푸"))
                .thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_NICKNAME_DUPLICATED);

        verify(memberRepository, never()).saveAndFlush(any(Member.class));
        verify(jwtTokenProvider, never()).issue(any());
    }

    @Test
    void 동시_요청의_이메일_UNIQUE_충돌을_중복_오류로_변환한다() {
        SignupRequest request = new SignupRequest(
                "dain@example.com",
                "Ssafy1234",
                "루돌푸"
        );
        when(passwordEncoder.encode("Ssafy1234"))
                .thenReturn("encoded-password");
        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenThrow(uniqueConstraintViolation("member_email_key"));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_EMAIL_DUPLICATED);

        verify(jwtTokenProvider, never()).issue(any());
        verify(refreshTokenStore, never()).save(any(), any(), any());
    }

    @Test
    void 동시_요청의_닉네임_UNIQUE_충돌을_중복_오류로_변환한다() {
        SignupRequest request = new SignupRequest(
                "dain@example.com",
                "Ssafy1234",
                "루돌푸"
        );
        when(passwordEncoder.encode("Ssafy1234"))
                .thenReturn("encoded-password");
        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenThrow(uniqueConstraintViolation("member_nickname_key"));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_NICKNAME_DUPLICATED);

        verify(jwtTokenProvider, never()).issue(any());
        verify(refreshTokenStore, never()).save(any(), any(), any());
    }

    private DataIntegrityViolationException uniqueConstraintViolation(
            String constraintName
    ) {
        ConstraintViolationException hibernateException =
                new ConstraintViolationException(
                        "unique constraint violation",
                        new SQLException("duplicate key", "23505"),
                        constraintName
                );
        return new DataIntegrityViolationException(
                "could not execute statement",
                hibernateException
        );
    }

    @Test
    void 이메일이_미중복이면_사용_가능으로_판정한다() {
        when(memberRepository.existsByEmail("dain@example.com"))
                .thenReturn(false);

        AvailabilityResponse response =
                authService.checkEmailAvailability(" Dain@Example.COM ");

        assertThat(response.available()).isTrue();
        verify(memberRepository).existsByEmail("dain@example.com");
    }

    @Test
    void 이메일이_중복이면_사용_불가로_판정한다() {
        when(memberRepository.existsByEmail("dain@example.com"))
                .thenReturn(true);

        AvailabilityResponse response =
                authService.checkEmailAvailability("dain@example.com");

        assertThat(response.available()).isFalse();
    }

    @Test
    void 닉네임이_미중복이면_사용_가능으로_판정한다() {
        when(memberRepository.existsByNickname("루돌푸"))
                .thenReturn(false);

        AvailabilityResponse response =
                authService.checkNicknameAvailability("루돌푸");

        assertThat(response.available()).isTrue();
        verify(memberRepository).existsByNickname("루돌푸");
    }

    @Test
    void 닉네임이_중복이면_사용_불가로_판정한다() {
        when(memberRepository.existsByNickname("루돌푸"))
                .thenReturn(true);

        AvailabilityResponse response =
                authService.checkNicknameAvailability("루돌푸");

        assertThat(response.available()).isFalse();
    }

    private Member member(Long id, String passwordHash) {
        Member member = new Member(
                "dain@example.com",
                passwordHash,
                "루돌푸"
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
