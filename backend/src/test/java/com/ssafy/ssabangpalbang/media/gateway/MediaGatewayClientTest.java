package com.ssafy.ssabangpalbang.media.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class MediaGatewayClientTest {

    private MockRestServiceServer server;
    private MediaGatewayClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://gateway.example/prod")
                .defaultHeader("Authorization", "Bearer test-token");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MediaGatewayClient(
                builder.build(),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void acceptsCurrentAndLegacyUploadResponseFieldNames() {
        server.expect(requestTo(
                        "https://gateway.example/prod/media/upload-url"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "Authorization",
                        "Bearer test-token"
                ))
                .andRespond(withSuccess("""
                        {
                          "fileUsage": "STT_AUDIO",
                          "s3Key": "stt-audio/2026/07/audio.m4a",
                          "uploadUrl": "https://signed.example/upload",
                          "method": "PUT",
                          "requiredHeaders": {
                            "Content-Type": "audio/mp4"
                          },
                          "sizeBytes": 1234,
                          "expiresAt": "2026-07-30T03:05:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        MediaGatewayClient.UploadUrlResponse response =
                client.createUploadUrl(
                        FileUsage.STT_AUDIO,
                        "audio/mp4",
                        1234L
                );

        assertThat(response.uploadKey())
                .isEqualTo("stt-audio/2026/07/audio.m4a");
        assertThat(response.expectedSizeBytes()).isEqualTo(1234L);
        server.verify();
    }

    @Test
    void sendsBothVerifyKeyNamesDuringLambdaMigration() {
        server.expect(requestTo(
                        "https://gateway.example/prod/media/upload/verify"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "fileUsage": "STT_AUDIO",
                          "uploadKey": "stt-audio/2026/07/audio.m4a",
                          "s3Key": "stt-audio/2026/07/audio.m4a",
                          "expectedContentType": "audio/mp4",
                          "expectedSizeBytes": 1234
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "verified": true,
                          "fileUsage": "STT_AUDIO",
                          "s3Key": "stt-audio/2026/07/audio.m4a",
                          "contentType": "audio/mp4",
                          "sizeBytes": 1234,
                          "etag": "etag"
                        }
                        """, MediaType.APPLICATION_JSON));

        MediaGatewayClient.VerifyResponse response = client.verify(
                FileUsage.STT_AUDIO,
                "stt-audio/2026/07/audio.m4a",
                "audio/mp4",
                1234L
        );

        assertThat(response.verified()).isTrue();
        server.verify();
    }

    @Test
    void classifiesLegacyVerificationSizeMismatch() {
        server.expect(requestTo(
                        "https://gateway.example/prod/media/upload/verify"
                ))
                .andRespond(withStatus(
                                HttpStatus.UNPROCESSABLE_ENTITY
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "message": "metadata mismatch",
                                  "details": {
                                    "expectedContentType": "audio/mp4",
                                    "actualContentType": "audio/mp4",
                                    "expectedSizeBytes": 1234,
                                    "actualSizeBytes": 1200
                                  }
                                }
                                """));

        assertThatThrownBy(() -> client.verify(
                FileUsage.STT_AUDIO,
                "stt-audio/2026/07/audio.m4a",
                "audio/mp4",
                1234L
        )).isInstanceOfSatisfying(
                MediaGatewayException.class,
                exception -> assertThat(exception.getFailure())
                        .isEqualTo(
                                MediaGatewayException.Failure
                                        .SIZE_MISMATCH
                        )
        );
        server.verify();
    }

    @Test
    void classifiesVerificationConflictAsChanged() {
        // Lambda는 HEAD~COPY 사이에 pending 객체가 바뀌면 409(재업로드 요구)를
        // 반환한다. code·details가 없는 이 응답은 형식/크기 오류가 아니라
        // 일시적 경합이므로 CHANGED로 분류돼야 한다.
        server.expect(requestTo(
                        "https://gateway.example/prod/media/upload/verify"
                ))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "message": "검증 중 업로드 파일이 변경되었습니다. 다시 업로드해 주세요."
                                }
                                """));

        assertThatThrownBy(() -> client.verify(
                FileUsage.POST_ATTACHMENT,
                "pending/post-attachment/2026/08/photo.jpg",
                "image/jpeg",
                1234L
        )).isInstanceOfSatisfying(
                MediaGatewayException.class,
                exception -> assertThat(exception.getFailure())
                        .isEqualTo(
                                MediaGatewayException.Failure.CHANGED
                        )
        );
        server.verify();
    }
}
