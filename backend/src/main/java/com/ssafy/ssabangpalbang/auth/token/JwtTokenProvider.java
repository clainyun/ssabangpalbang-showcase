package com.ssafy.ssabangpalbang.auth.token;

import com.ssafy.ssabangpalbang.auth.domain.SocialProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String ISSUER = "ssabangpalbang";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String SOCIAL_PROVIDER_CLAIM = "provider";
    private static final String SOCIAL_EMAIL_CLAIM = "email";
    private static final Duration SOCIAL_SIGNUP_TOKEN_EXPIRATION =
            Duration.ofMinutes(10);

    private final SecretKey signingKey;
    private final Duration accessTokenExpiration;
    private final Duration refreshTokenExpiration;
    private final Clock clock;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.signingKey = createSigningKey(properties.secret());
        this.accessTokenExpiration = requirePositive(
                properties.accessTokenExpiration(),
                "Access Token 만료시간"
        );
        this.refreshTokenExpiration = requirePositive(
                properties.refreshTokenExpiration(),
                "Refresh Token 만료시간"
        );
        this.clock = clock;
    }

    public IssuedTokens issue(Long memberId) {
        Objects.requireNonNull(memberId, "회원 ID는 필수입니다.");

        Instant issuedAt = clock.instant();

        String accessToken = createToken(
                memberId,
                TokenType.ACCESS,
                issuedAt,
                accessTokenExpiration
        );
        String refreshToken = createToken(
                memberId,
                TokenType.REFRESH,
                issuedAt,
                refreshTokenExpiration
        );

        return new IssuedTokens(
                accessToken,
                refreshToken,
                refreshTokenExpiration
        );
    }

    public IssuedSocialSignupToken issueSocialSignupToken(
            String provider,
            String socialUserId,
            String email
    ) {
        Objects.requireNonNull(provider, "소셜 제공자는 필수입니다.");
        Objects.requireNonNull(
                socialUserId,
                "소셜 사용자 식별자는 필수입니다."
        );

        Instant issuedAt = clock.instant();
        var builder = Jwts.builder()
                .issuer(ISSUER)
                .subject(socialUserId)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(
                        issuedAt.plus(SOCIAL_SIGNUP_TOKEN_EXPIRATION)
                ))
                .claim(TOKEN_TYPE_CLAIM, TokenType.SOCIAL_SIGNUP.name())
                .claim(SOCIAL_PROVIDER_CLAIM, provider);
        if (email != null) {
            builder.claim(SOCIAL_EMAIL_CLAIM, email);
        }

        return new IssuedSocialSignupToken(
                builder.signWith(signingKey, Jwts.SIG.HS256).compact(),
                Math.toIntExact(SOCIAL_SIGNUP_TOKEN_EXPIRATION.toSeconds())
        );
    }

    public Long parseRefreshToken(String refreshToken) {
        return parseToken(
                refreshToken,
                TokenType.REFRESH,
                ErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED
        );
    }

    public Long parseAccessToken(String accessToken) {
        return parseToken(
                accessToken,
                TokenType.ACCESS,
                ErrorCode.UNAUTHORIZED,
                ErrorCode.AUTH_ACCESS_TOKEN_EXPIRED
        );
    }

    public SocialSignupTokenClaims parseSocialSignupToken(String token) {
        ErrorCode invalidErrorCode =
                ErrorCode.AUTH_SOCIAL_SIGNUP_TOKEN_INVALID;
        Claims claims = parseClaims(
                token,
                invalidErrorCode,
                ErrorCode.AUTH_SOCIAL_SIGNUP_TOKEN_EXPIRED
        );

        try {
            if (!TokenType.SOCIAL_SIGNUP.name().equals(
                    claims.get(TOKEN_TYPE_CLAIM, String.class)
            )) {
                throw new BusinessException(invalidErrorCode);
            }

            String providerValue = claims.get(
                    SOCIAL_PROVIDER_CLAIM,
                    String.class
            );
            String socialUserId = claims.getSubject();
            if (providerValue == null || providerValue.isBlank()
                    || socialUserId == null || socialUserId.isBlank()) {
                throw new BusinessException(invalidErrorCode);
            }

            String email = claims.get(SOCIAL_EMAIL_CLAIM, String.class);
            if (email != null) {
                email = email.strip().toLowerCase(Locale.ROOT);
                if (email.isBlank()) {
                    email = null;
                }
            }

            return new SocialSignupTokenClaims(
                    SocialProvider.valueOf(providerValue),
                    socialUserId.strip(),
                    email
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(invalidErrorCode);
        }
    }

    private Long parseToken(
            String token,
            TokenType expectedTokenType,
            ErrorCode invalidErrorCode,
            ErrorCode expiredErrorCode
    ) {
        Claims claims = parseClaims(
                token,
                invalidErrorCode,
                expiredErrorCode
        );

        if (!expectedTokenType.name().equals(
                claims.get(TOKEN_TYPE_CLAIM, String.class)
        )) {
            throw new BusinessException(invalidErrorCode);
        }

        return parseMemberId(claims.getSubject(), invalidErrorCode);
    }

    private Claims parseClaims(
            String token,
            ErrorCode invalidErrorCode,
            ErrorCode expiredErrorCode
    ) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(expiredErrorCode);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(invalidErrorCode);
        }
    }

    private String createToken(
            Long memberId,
            TokenType tokenType,
            Instant issuedAt,
            Duration expiration
    ) {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(memberId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(expiration)))
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey createSigningKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET 환경변수가 설정되어야 합니다."
            );
        }

        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET은 256비트 이상의 Base64 키여야 합니다.",
                    exception
            );
        }
    }

    private Long parseMemberId(String subject, ErrorCode invalidErrorCode) {
        try {
            Long memberId = Long.valueOf(subject);
            if (memberId <= 0) {
                throw new NumberFormatException("회원 ID는 양수여야 합니다.");
            }
            return memberId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(invalidErrorCode);
        }
    }

    private Duration requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(name + "은 0보다 커야 합니다.");
        }
        return duration;
    }

    private enum TokenType {
        ACCESS,
        REFRESH,
        SOCIAL_SIGNUP
    }
}
