package com.ssafy.ssabangpalbang.auth.token;

import com.ssafy.ssabangpalbang.auth.domain.SocialProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET =
            "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";
    private static final String OTHER_SECRET =
            "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=";
    private static final Instant NOW = Instant.now()
            .truncatedTo(ChronoUnit.SECONDS);

    @Test
    void Access와_Refresh_Token을_각각의_만료시간으로_발급한다() {
        JwtTokenProvider provider = new JwtTokenProvider(
                new JwtProperties(
                        SECRET,
                        Duration.ofMinutes(30),
                        Duration.ofDays(30)
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        IssuedTokens tokens = provider.issue(1L);

        Claims accessClaims = claims(tokens.accessToken());
        Claims refreshClaims = claims(tokens.refreshToken());

        assertThat(accessClaims.getIssuer()).isEqualTo("ssabangpalbang");
        assertThat(accessClaims.getSubject()).isEqualTo("1");
        assertThat(accessClaims.get("tokenType", String.class))
                .isEqualTo("ACCESS");
        assertThat(accessClaims.getExpiration().toInstant())
                .isEqualTo(NOW.plus(Duration.ofMinutes(30)));

        assertThat(refreshClaims.getSubject()).isEqualTo("1");
        assertThat(refreshClaims.get("tokenType", String.class))
                .isEqualTo("REFRESH");
        assertThat(refreshClaims.getExpiration().toInstant())
                .isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(tokens.refreshTokenTtl()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void 소셜_회원가입_토큰에_제공자와_사용자정보를_담아_10분간_발급한다() {
        JwtTokenProvider provider = provider(SECRET, NOW);

        IssuedSocialSignupToken issued = provider.issueSocialSignupToken(
                "KAKAO",
                "kakao-user-id",
                "social@example.com"
        );

        Claims claims = claims(issued.token());
        assertThat(claims.getIssuer()).isEqualTo("ssabangpalbang");
        assertThat(claims.getSubject()).isEqualTo("kakao-user-id");
        assertThat(claims.get("tokenType", String.class))
                .isEqualTo("SOCIAL_SIGNUP");
        assertThat(claims.get("provider", String.class)).isEqualTo("KAKAO");
        assertThat(claims.get("email", String.class))
                .isEqualTo("social@example.com");
        assertThat(claims.getExpiration().toInstant())
                .isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(issued.expiresInSeconds()).isEqualTo(600);
    }

    @Test
    void 유효한_소셜_회원가입_토큰의_가입정보를_확인한다() {
        JwtTokenProvider provider = provider(SECRET, NOW);
        IssuedSocialSignupToken issued = provider.issueSocialSignupToken(
                "KAKAO",
                "kakao-user-id",
                "SOCIAL@EXAMPLE.COM"
        );

        SocialSignupTokenClaims claims =
                provider.parseSocialSignupToken(issued.token());

        assertThat(claims.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(claims.socialUserId()).isEqualTo("kakao-user-id");
        assertThat(claims.email()).isEqualTo("social@example.com");
    }

    @Test
    void 이메일이_없는_소셜_회원가입_토큰도_검증한다() {
        JwtTokenProvider provider = provider(SECRET, NOW);
        IssuedSocialSignupToken issued = provider.issueSocialSignupToken(
                "NAVER",
                "naver-user-id",
                null
        );

        SocialSignupTokenClaims claims =
                provider.parseSocialSignupToken(issued.token());

        assertThat(claims.provider()).isEqualTo(SocialProvider.NAVER);
        assertThat(claims.email()).isNull();
    }

    @Test
    void 다른_종류의_JWT는_소셜_회원가입에_사용할_수_없다() {
        JwtTokenProvider provider = provider(SECRET, NOW);
        IssuedTokens tokens = provider.issue(1L);

        assertThatThrownBy(() -> provider.parseSocialSignupToken(
                tokens.accessToken()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_SOCIAL_SIGNUP_TOKEN_INVALID);
    }

    @Test
    void 서명이_유효하지_않은_소셜_회원가입_토큰을_거절한다() {
        IssuedSocialSignupToken issued = provider(SECRET, NOW)
                .issueSocialSignupToken(
                        "KAKAO",
                        "kakao-user-id",
                        "social@example.com"
                );
        JwtTokenProvider verifier = provider(OTHER_SECRET, NOW);

        assertThatThrownBy(() -> verifier.parseSocialSignupToken(
                issued.token()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_SOCIAL_SIGNUP_TOKEN_INVALID);
    }

    @Test
    void 만료된_소셜_회원가입_토큰을_구분해_거절한다() {
        IssuedSocialSignupToken issued = provider(SECRET, NOW)
                .issueSocialSignupToken(
                        "KAKAO",
                        "kakao-user-id",
                        "social@example.com"
                );
        JwtTokenProvider verifier = provider(
                SECRET,
                NOW.plus(Duration.ofMinutes(11))
        );

        assertThatThrownBy(() -> verifier.parseSocialSignupToken(
                issued.token()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_SOCIAL_SIGNUP_TOKEN_EXPIRED);
    }

    @Test
    void 유효한_Refresh_Token에서_회원_ID를_확인한다() {
        JwtTokenProvider provider = provider(SECRET, NOW);
        IssuedTokens tokens = provider.issue(1L);

        assertThat(provider.parseRefreshToken(tokens.refreshToken()))
                .isEqualTo(1L);
    }

    @Test
    void Access_Token은_Refresh_Token으로_사용할_수_없다() {
        JwtTokenProvider provider = provider(SECRET, NOW);
        IssuedTokens tokens = provider.issue(1L);

        assertThatThrownBy(() -> provider.parseRefreshToken(
                tokens.accessToken()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    @Test
    void 서명이_유효하지_않은_Refresh_Token을_거절한다() {
        IssuedTokens tokens = provider(SECRET, NOW).issue(1L);
        JwtTokenProvider verifier = provider(OTHER_SECRET, NOW);

        assertThatThrownBy(() -> verifier.parseRefreshToken(
                tokens.refreshToken()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    @Test
    void 만료된_Refresh_Token을_구분해_거절한다() {
        IssuedTokens tokens = provider(SECRET, NOW).issue(1L);
        JwtTokenProvider verifier = provider(
                SECRET,
                NOW.plus(Duration.ofDays(31))
        );

        assertThatThrownBy(() -> verifier.parseRefreshToken(
                tokens.refreshToken()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
    }

    @Test
    void 유효한_Access_Token에서_회원_ID를_확인한다() {
        JwtTokenProvider provider = provider(SECRET, NOW);
        IssuedTokens tokens = provider.issue(1L);

        assertThat(provider.parseAccessToken(tokens.accessToken()))
                .isEqualTo(1L);
    }

    @Test
    void Refresh_Token은_Access_Token으로_사용할_수_없다() {
        JwtTokenProvider provider = provider(SECRET, NOW);
        IssuedTokens tokens = provider.issue(1L);

        assertThatThrownBy(() -> provider.parseAccessToken(
                tokens.refreshToken()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 서명이_유효하지_않은_Access_Token을_거절한다() {
        IssuedTokens tokens = provider(SECRET, NOW).issue(1L);
        JwtTokenProvider verifier = provider(OTHER_SECRET, NOW);

        assertThatThrownBy(() -> verifier.parseAccessToken(
                tokens.accessToken()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 만료된_Access_Token을_구분해_거절한다() {
        IssuedTokens tokens = provider(SECRET, NOW).issue(1L);
        JwtTokenProvider verifier = provider(
                SECRET,
                NOW.plus(Duration.ofMinutes(31))
        );

        assertThatThrownBy(() -> verifier.parseAccessToken(
                tokens.accessToken()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_ACCESS_TOKEN_EXPIRED);
    }

    private JwtTokenProvider provider(String secret, Instant instant) {
        return new JwtTokenProvider(
                new JwtProperties(
                        secret,
                        Duration.ofMinutes(30),
                        Duration.ofDays(30)
                ),
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private Claims claims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
