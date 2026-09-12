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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class KakaoOAuthClientTest {

    private MockRestServiceServer server;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoOAuthClient(
                builder.build(),
                new SocialOAuthProperties.Provider(
                        "kakao-client-id",
                        "kakao-client-secret"
                )
        );
    }

    @Test
    void 인가_코드를_토큰으로_교환하고_카카오_사용자를_조회한다() {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString(
                        "grant_type=authorization_code"
                )))
                .andExpect(content().string(containsString(
                        "client_id=kakao-client-id"
                )))
                .andExpect(content().string(containsString(
                        "redirect_uri=ssabangpalbang%3A%2F%2Foauth%2Fkakao"
                )))
                .andExpect(content().string(containsString(
                        "code=authorization-code"
                )))
                .andRespond(withSuccess(
                        "{\"access_token\":\"kakao-access-token\"}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer kakao-access-token"
                ))
                .andRespond(withSuccess(
                        """
                                {
                                  "id": 12345,
                                  "kakao_account": {
                                    "email": "KAKAO@EXAMPLE.COM"
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        SocialOAuthUser user = client.authenticate(
                new SocialOAuthAuthorization(
                        "authorization-code",
                        "ssabangpalbang://oauth/kakao",
                        null
                )
        );

        assertThat(user.socialUserId()).isEqualTo("12345");
        assertThat(user.email()).isEqualTo("kakao@example.com");
        server.verify();
    }

    @Test
    void 카카오가_인가_코드를_거절하면_인증_실패로_변환한다() {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.authenticate(
                new SocialOAuthAuthorization(
                        "invalid-code",
                        "ssabangpalbang://oauth/kakao",
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_SOCIAL_AUTHENTICATION_FAILED);
        server.verify();
    }
}
