package com.ssafy.ssabangpalbang.auth.oauth;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class NaverOAuthClientTest {

    private MockRestServiceServer server;
    private NaverOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NaverOAuthClient(
                builder.build(),
                new SocialOAuthProperties.Provider(
                        "naver-client-id",
                        "naver-client-secret"
                )
        );
    }

    @Test
    void 인가_코드와_state로_토큰을_교환하고_네이버_사용자를_조회한다() {
        server.expect(request -> {
                    assertThat(request.getURI().getScheme()).isEqualTo("https");
                    assertThat(request.getURI().getHost())
                            .isEqualTo("nid.naver.com");
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/oauth2.0/token");
                    assertThat(request.getURI().getQuery())
                            .contains("grant_type=authorization_code")
                            .contains("client_id=naver-client-id")
                            .contains("client_secret=naver-client-secret")
                            .contains("code=authorization-code")
                            .contains("state=verified-oauth-state");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"access_token\":\"naver-access-token\"}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer naver-access-token"
                ))
                .andRespond(withSuccess(
                        """
                                {
                                  "resultcode": "00",
                                  "message": "success",
                                  "response": {
                                    "id": "naver-user-id",
                                    "email": "NAVER@EXAMPLE.COM"
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        SocialOAuthUser user = client.authenticate(
                new SocialOAuthAuthorization(
                        "authorization-code",
                        null,
                        "verified-oauth-state"
                )
        );

        assertThat(user.socialUserId()).isEqualTo("naver-user-id");
        assertThat(user.email()).isEqualTo("naver@example.com");
        server.verify();
    }

    @Test
    void 네이버_설정이_없으면_외부_요청_없이_503_오류를_반환한다() {
        NaverOAuthClient unconfiguredClient = new NaverOAuthClient(
                RestClient.create(),
                new SocialOAuthProperties.Provider("", "")
        );

        assertThatThrownBy(() -> unconfiguredClient.authenticate(
                new SocialOAuthAuthorization(
                        "authorization-code",
                        null,
                        "verified-oauth-state"
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    void 네이버_토큰_서버_장애를_503_오류로_변환한다() {
        server.expect(request -> assertThat(request.getURI().getHost())
                        .isEqualTo("nid.naver.com"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.authenticate(
                new SocialOAuthAuthorization(
                        "authorization-code",
                        null,
                        "verified-oauth-state"
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
        server.verify();
    }
}
