package com.ssafy.ssabangpalbang.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.ssabangpalbang.auth.domain.SocialProvider;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NaverOAuthClient implements SocialOAuthClient {

    private static final String TOKEN_URL =
            "https://nid.naver.com/oauth2.0/token";
    private static final String USER_URL =
            "https://openapi.naver.com/v1/nid/me";

    private final RestClient restClient;
    private final SocialOAuthProperties.Provider properties;

    @Autowired
    public NaverOAuthClient(SocialOAuthProperties properties) {
        this(SocialOAuthRestClientFactory.create(), properties.naver());
    }

    NaverOAuthClient(
            RestClient restClient,
            SocialOAuthProperties.Provider properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.NAVER;
    }

    @Override
    public SocialOAuthUser authenticate(
            SocialOAuthAuthorization authorization
    ) {
        validateConfiguration();

        try {
            NaverTokenResponse tokenResponse = requestToken(authorization);
            if (tokenResponse == null
                    || isBlank(tokenResponse.accessToken())) {
                throw authenticationFailed();
            }

            NaverUserResponse userResponse = requestUser(
                    tokenResponse.accessToken()
            );
            if (userResponse == null
                    || !"00".equals(userResponse.resultCode())
                    || userResponse.response() == null
                    || isBlank(userResponse.response().id())) {
                throw authenticationFailed();
            }

            return new SocialOAuthUser(
                    userResponse.response().id(),
                    userResponse.response().email()
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

    private NaverTokenResponse requestToken(
            SocialOAuthAuthorization authorization
    ) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("nid.naver.com")
                        .path("/oauth2.0/token")
                        .queryParam("grant_type", "authorization_code")
                        .queryParam("client_id", properties.clientId())
                        .queryParam(
                                "client_secret",
                                properties.clientSecret()
                        )
                        .queryParam(
                                "code",
                                authorization.authorizationCode()
                        )
                        .queryParam("state", authorization.state())
                        .build())
                .retrieve()
                .body(NaverTokenResponse.class);
    }

    private NaverUserResponse requestUser(String accessToken) {
        return restClient.get()
                .uri(USER_URL)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .retrieve()
                .body(NaverUserResponse.class);
    }

    private void validateConfiguration() {
        if (isBlank(properties.clientId())
                || isBlank(properties.clientSecret())) {
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

    private record NaverTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {
    }

    private record NaverUserResponse(
            @JsonProperty("resultcode") String resultCode,
            NaverProfile response
    ) {
    }

    private record NaverProfile(
            String id,
            String email
    ) {
    }
}
