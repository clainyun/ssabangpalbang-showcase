package com.ssafy.ssabangpalbang.auth.service;

import com.ssafy.ssabangpalbang.auth.dto.request.SocialSignupRequest;
import com.ssafy.ssabangpalbang.auth.dto.response.SocialSignupResponse;
import com.ssafy.ssabangpalbang.auth.token.IssuedSocialSignupToken;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.auth.token.SocialSignupTokenStore;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(
        named = "RUN_LOCAL_INFRA_TESTS",
        matches = "true"
)
class SocialSignupLocalIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private SocialSignupTokenStore socialSignupTokenStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 소셜_회원과_계정을_함께_저장하고_임시_토큰_재사용을_차단한다() {
        String suffix = UUID.randomUUID().toString();
        String socialUserId = "kakao-" + suffix;
        String email = "social-" + suffix + "@example.com";
        String nickname = "소셜" + suffix.substring(0, 8);
        IssuedSocialSignupToken signupToken =
                jwtTokenProvider.issueSocialSignupToken(
                        "KAKAO",
                        socialUserId,
                        email
                );
        String signupRedisKey = "auth:social-signup:"
                + sha256(signupToken.token());
        Long memberId = null;

        try {
            socialSignupTokenStore.save(
                    signupToken.token(),
                    Duration.ofSeconds(signupToken.expiresInSeconds())
            );

            SocialSignupResponse response = authService.socialSignup(
                    new SocialSignupRequest(
                            signupToken.token(),
                            null,
                            nickname
                    )
            );
            memberId = response.memberId();

            assertThat(response.email()).isEqualTo(email);
            assertThat(response.provider()).isEqualTo("KAKAO");
            assertThat(response.selectedCharacterId()).isEqualTo("PALBANG");
            assertThat(response.onboardingCompleted()).isFalse();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT password_hash FROM member WHERE id = ?",
                    String.class,
                    memberId
            )).isNull();
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM social_account
                    WHERE member_id = ?
                      AND provider = 'KAKAO'
                      AND social_user_id = ?
                    """,
                    Long.class,
                    memberId,
                    socialUserId
            )).isEqualTo(1L);
            assertThat(redisTemplate.hasKey(signupRedisKey)).isFalse();

            assertThatThrownBy(() -> authService.socialSignup(
                    new SocialSignupRequest(
                            signupToken.token(),
                            null,
                            nickname
                    )
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_SOCIAL_ACCOUNT_ALREADY_EXISTS);
        } finally {
            if (memberId != null) {
                jdbcTemplate.update(
                        "DELETE FROM social_account WHERE member_id = ?",
                        memberId
                );
                jdbcTemplate.update(
                        "DELETE FROM member WHERE id = ?",
                        memberId
                );
                redisTemplate.delete("auth:refresh:" + memberId);
            }
            redisTemplate.delete(signupRedisKey);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
