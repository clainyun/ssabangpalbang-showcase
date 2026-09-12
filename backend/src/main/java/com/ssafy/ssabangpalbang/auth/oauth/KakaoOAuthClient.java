package com.ssafy.ssabangpalbang.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.ssabangpalbang.auth.domain.SocialProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoOAuthClient implements SocialOAuthClient {

    private static final String TOKEN_URL =
            "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL =
            "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final SocialOAuthProperties.Provider properties;

    @Autowired
    public KakaoOAuthClient(SocialOAuthProperties properties) {
        this(SocialOAuthRestClientFactory.create(), properties.kakao());
    }

    KakaoOAuthClient(
            RestClient restClient,
            SocialOAuthProperties.Provider properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public SocialOAuthUser authenticate(
            SocialOAuthAuthorization authorization
    ) {
        validateConfiguration();

        try {
            KakaoTokenResponse tokenResponse = requestToken(authorization);
            if (tokenResponse == null
                    || isBlank(tokenResponse.accessToken())) {
                throw authenticationFailed();
            }

            KakaoUserResponse userResponse = requestUser(
                    tokenResponse.accessToken()
            );
            if (userResponse == null || userResponse.id() == null) {
                throw authenticationFailed();
            }

            String email = userResponse.kakaoAccount() == null
                    ? null
                    : userResponse.kakaoAccount().email();
            return new SocialOAuthUser(
                    userResponse.id().toString(),
                    email
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (IllegalArgumentException exception) {
            throw authenticationFailed();
        } catch (RestClientException exception) {
            throw providerUnavailable();
        }
    }

    private KakaoTokenResponse requestToken(
            SocialOAuthAuthorization authorization
    ) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", authorization.redirectUri());
        form.add("code", authorization.authorizationCode());
        if (!isBlank(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }

        return restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);
    }

    private KakaoUserResponse requestUser(String accessToken) {
        return restClient.get()
                .uri(USER_URL)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .retrieve()
                .body(KakaoUserResponse.class);
    }

    private void validateConfiguration() {
        if (isBlank(properties.clientId())) {
            throw providerUnavailable();
        }
    }

    private BusinessException translate(
            RestClientResponseException exception
    ) {
        int status = exception.getStatusCode().value();
        if (status >= 400 && status < 500 && status != 429) {
            return authenticationFailed();
        }
        return providerUnavailable();
    }

    private BusinessException authenticationFailed() {
        return new BusinessException(
                ErrorCode.AUTH_SOCIAL_AUTHENTICATION_FAILED
        );
    }

    private BusinessException providerUnavailable() {
        return new BusinessException(
                ErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {
    }

    private record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {
    }

    private record KakaoAccount(String email) {
    }
}
