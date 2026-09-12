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
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String MEMBER_EMAIL_UNIQUE_CONSTRAINT =
            "member_email_key";
    private static final String MEMBER_NICKNAME_UNIQUE_CONSTRAINT =
            "member_nickname_key";
    private static final String SOCIAL_ACCOUNT_UNIQUE_CONSTRAINT =
            "social_account_provider_social_user_id_key";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialOAuthClientRegistry socialOAuthClientRegistry;
    private final SocialSignupTokenStore socialSignupTokenStore;
    private final Validator validator;

    @Transactional
    public void logout(Long memberId, LogoutRequest request) {
        Long refreshTokenMemberId = jwtTokenProvider.parseRefreshToken(
                request.refreshToken()
        );
        if (!memberId.equals(refreshTokenMemberId)) {
            throw new BusinessException(
                    ErrorCode.AUTH_REFRESH_TOKEN_MEMBER_MISMATCH
            );
        }

        refreshTokenStore.revoke(memberId, request.refreshToken());
    }

    @Transactional
    public TokenReissueResponse reissue(TokenReissueRequest request) {
        Long memberId = jwtTokenProvider.parseRefreshToken(
                request.refreshToken()
        );

        if (!refreshTokenStore.matches(
                memberId,
                request.refreshToken()
        )) {
            throw new BusinessException(
                    ErrorCode.AUTH_REFRESH_TOKEN_REVOKED
            );
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        validateActiveMember(member);

        IssuedTokens tokens = jwtTokenProvider.issue(memberId);
        boolean rotated = refreshTokenStore.rotate(
                memberId,
                request.refreshToken(),
                tokens.refreshToken(),
                tokens.refreshTokenTtl()
        );
        if (!rotated) {
            throw new BusinessException(
                    ErrorCode.AUTH_REFRESH_TOKEN_REVOKED
            );
        }

        return TokenReissueResponse.from(tokens);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(
                        request.normalizedEmail()
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTH_LOGIN_FAILED
                ));

        validatePasswordLogin(member, request.password());
        validateActiveMember(member);

        boolean onboardingCompleted =
                memberRepository.existsPreferenceByMemberId(member.getId());
        IssuedTokens tokens = jwtTokenProvider.issue(member.getId());

        refreshTokenStore.save(
                member.getId(),
                tokens.refreshToken(),
                tokens.refreshTokenTtl()
        );

        return LoginResponse.from(member, onboardingCompleted, tokens);
    }

    public SocialLoginResult socialLogin(SocialLoginRequest request) {
        SocialProvider provider = SocialProvider.from(request.provider());
        validateSocialLoginRequest(provider, request);

        SocialOAuthUser socialUser = socialOAuthClientRegistry.authenticate(
                provider,
                new SocialOAuthAuthorization(
                        request.authorizationCode(),
                        request.redirectUri(),
                        request.state()
                )
        );

        return socialAccountRepository
                .findMemberSnapshot(
                        provider,
                        socialUser.socialUserId()
                )
                .map(this::loginExistingSocialMember)
                .orElseGet(() -> requireSocialSignup(
                        provider,
                        socialUser
                ));
    }

    @Transactional
    public SocialSignupResponse socialSignup(SocialSignupRequest request) {
        SocialSignupTokenClaims signupClaims =
                jwtTokenProvider.parseSocialSignupToken(
                        request.socialSignupToken()
                );

        if (socialAccountRepository.existsByProviderAndSocialUserId(
                signupClaims.provider(),
                signupClaims.socialUserId()
        )) {
            throw new BusinessException(
                    ErrorCode.AUTH_SOCIAL_ACCOUNT_ALREADY_EXISTS
            );
        }

        String memberEmail = resolveSocialSignupEmail(
                signupClaims.email(),
                request.normalizedEmail()
        );
        validateSocialSignupEmail(memberEmail);
        validateUniqueMember(memberEmail, request.nickname());

        if (!socialSignupTokenStore.consume(
                request.socialSignupToken()
        )) {
            throw new BusinessException(
                    ErrorCode.AUTH_SOCIAL_SIGNUP_TOKEN_INVALID
            );
        }

        Member member = saveMember(new Member(
                memberEmail,
                null,
                request.nickname()
        ));
        saveSocialAccount(new SocialAccount(
                member,
                signupClaims.provider(),
                signupClaims.socialUserId(),
                signupClaims.email()
        ));

        IssuedTokens tokens = jwtTokenProvider.issue(member.getId());
        refreshTokenStore.save(
                member.getId(),
                tokens.refreshToken(),
                tokens.refreshTokenTtl()
        );

        return SocialSignupResponse.from(
                member,
                signupClaims.provider(),
                tokens
        );
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkEmailAvailability(String email) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        return AvailabilityResponse.of(
                !memberRepository.existsByEmail(normalizedEmail)
        );
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkNicknameAvailability(String nickname) {
        return AvailabilityResponse.of(
                !memberRepository.existsByNickname(nickname)
        );
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String normalizedEmail = request.normalizedEmail();

        validateUniqueMember(normalizedEmail, request.nickname());

        Member member = saveMember(new Member(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.nickname()
        ));

        IssuedTokens tokens = jwtTokenProvider.issue(member.getId());
        refreshTokenStore.save(
                member.getId(),
                tokens.refreshToken(),
                tokens.refreshTokenTtl()
        );

        return SignupResponse.from(member, tokens);
    }

    private Member saveMember(Member member) {
        try {
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            String constraintName = findConstraintName(exception);
            if (MEMBER_EMAIL_UNIQUE_CONSTRAINT.equals(constraintName)) {
                throw new BusinessException(
                        ErrorCode.AUTH_EMAIL_DUPLICATED
                );
            }
            if (MEMBER_NICKNAME_UNIQUE_CONSTRAINT.equals(constraintName)) {
                throw new BusinessException(
                        ErrorCode.AUTH_NICKNAME_DUPLICATED
                );
            }
            throw exception;
        }
    }

    private void saveSocialAccount(SocialAccount socialAccount) {
        try {
            socialAccountRepository.saveAndFlush(socialAccount);
        } catch (DataIntegrityViolationException exception) {
            if (SOCIAL_ACCOUNT_UNIQUE_CONSTRAINT.equals(
                    findConstraintName(exception)
            )) {
                throw new BusinessException(
                        ErrorCode.AUTH_SOCIAL_ACCOUNT_ALREADY_EXISTS
                );
            }
            throw exception;
        }
    }

    private SocialLoginResult loginExistingSocialMember(
            SocialMemberSnapshot member
    ) {
        validateActiveMember(member.status());

        boolean onboardingCompleted =
                memberRepository.existsPreferenceByMemberId(member.memberId());
        IssuedTokens tokens = jwtTokenProvider.issue(member.memberId());
        refreshTokenStore.save(
                member.memberId(),
                tokens.refreshToken(),
                tokens.refreshTokenTtl()
        );

        return new SocialLoginResult(
                AuthSuccessCode.SOCIAL_LOGIN_SUCCESS,
                new SocialLoginResponse.ExistingMember(
                        false,
                        member.memberId(),
                        member.email(),
                        member.nickname(),
                        member.profileImageUrl(),
                        member.selectedCharacterId(),
                        onboardingCompleted,
                        tokens.accessToken(),
                        tokens.refreshToken()
                )
        );
    }

    private SocialLoginResult requireSocialSignup(
            SocialProvider provider,
            SocialOAuthUser socialUser
    ) {
        IssuedSocialSignupToken signupToken =
                jwtTokenProvider.issueSocialSignupToken(
                        provider.name(),
                        socialUser.socialUserId(),
                        socialUser.email()
                );
        socialSignupTokenStore.save(
                signupToken.token(),
                Duration.ofSeconds(signupToken.expiresInSeconds())
        );

        return new SocialLoginResult(
                AuthSuccessCode.SOCIAL_SIGNUP_REQUIRED,
                new SocialLoginResponse.SignupRequired(
                        true,
                        provider.name(),
                        socialUser.email(),
                        socialUser.email() == null,
                        signupToken.token(),
                        signupToken.expiresInSeconds()
                )
        );
    }

    private void validateSocialLoginRequest(
            SocialProvider provider,
            SocialLoginRequest request
    ) {
        if (provider == SocialProvider.KAKAO
                && isBlank(request.redirectUri())) {
            throw invalidSocialRequest(
                    "redirectUri",
                    "카카오 로그인 Redirect URI는 필수입니다."
            );
        }
        if (provider == SocialProvider.NAVER
                && isBlank(request.state())) {
            throw invalidSocialRequest(
                    "state",
                    "네이버 로그인 OAuth state는 필수입니다."
            );
        }
    }

    private BusinessException invalidSocialRequest(
            String field,
            String reason
    ) {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of("field", field, "reason", reason)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String findConstraintName(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }

    private void validateUniqueMember(String email, String nickname) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.AUTH_NICKNAME_DUPLICATED);
        }
    }

    private String resolveSocialSignupEmail(
            String providerEmail,
            String requestEmail
    ) {
        if (providerEmail != null) {
            return providerEmail;
        }
        if (requestEmail == null) {
            throw new BusinessException(
                    ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED,
                    Map.of("field", "email")
            );
        }
        return requestEmail;
    }

    private void validateSocialSignupEmail(String email) {
        var violation = validator.validate(new SocialSignupEmail(email))
                .stream()
                .min(Comparator.comparing(
                        constraint -> constraint.getMessage()
                ));
        if (violation.isPresent()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field",
                            "email",
                            "reason",
                            violation.get().getMessage()
                    )
            );
        }
    }

    private void validatePasswordLogin(Member member, String password) {
        if (member.getPasswordHash() == null) {
            throw new BusinessException(
                    ErrorCode.AUTH_PASSWORD_LOGIN_NOT_AVAILABLE
            );
        }
        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
    }

    private void validateActiveMember(Member member) {
        validateActiveMember(member.getStatus());
    }

    private void validateActiveMember(MemberStatus status) {
        if (status != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.AUTH_MEMBER_WITHDRAWN);
        }
    }

    public record SocialLoginResult(
            AuthSuccessCode responseCode,
            SocialLoginResponse response
    ) {
    }

    private record SocialSignupEmail(
            @Email(message = "올바른 이메일 형식이 아닙니다.")
            @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
            String value
    ) {
    }
}
