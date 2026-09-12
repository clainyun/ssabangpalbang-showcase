package com.ssafy.ssabangpalbang.media.gateway;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "ssabangpalbang.media.gateway",
        name = "enabled",
        havingValue = "true"
)
public class MediaGatewayClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MediaGatewayClient(
            @Qualifier("mediaGatewayRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public UploadUrlResponse createUploadUrl(
            FileUsage fileUsage,
            String contentType,
            long sizeBytes
    ) {
        return post(
                "/media/upload-url",
                new UploadUrlRequest(fileUsage, contentType, sizeBytes),
                UploadUrlResponse.class
        );
    }

    public VerifyResponse verify(
            FileUsage fileUsage,
            String uploadKey,
            String expectedContentType,
            long expectedSizeBytes
    ) {
        return post(
                "/media/upload/verify",
                new VerifyRequest(
                        fileUsage,
                        uploadKey,
                        uploadKey,
                        expectedContentType,
                        expectedSizeBytes
                ),
                VerifyResponse.class
        );
    }

    public DownloadUrlResponse createDownloadUrl(
            FileUsage fileUsage,
            String s3Key
    ) {
        return post(
                "/media/download-url",
                new ObjectRequest(fileUsage, s3Key),
                DownloadUrlResponse.class
        );
    }

    public DeleteResponse delete(FileUsage fileUsage, String s3Key) {
        return post(
                "/media/delete",
                new ObjectRequest(fileUsage, s3Key),
                DeleteResponse.class
        );
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        try {
            T response = restClient.post()
                    .uri(path)
                    .body(request)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new MediaGatewayException(
                        MediaGatewayException.Failure.UNAVAILABLE
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw map(exception);
        } catch (RestClientException exception) {
            throw new MediaGatewayException(
                    MediaGatewayException.Failure.UNAVAILABLE,
                    exception
            );
        }
    }

    private MediaGatewayException map(
            RestClientResponseException exception
    ) {
        HttpStatusCode status = exception.getStatusCode();
        if (status.value() == 404) {
            return new MediaGatewayException(
                    MediaGatewayException.Failure.NOT_FOUND,
                    exception
            );
        }
        if (status.value() == 409) {
            // 검증(HEAD~COPY) 사이에 pending 객체가 변경돼 게이트웨이가
            // 재업로드를 요구한 경우. 형식/크기 오류(400·422)와 달리
            // 일시적 경합이므로 별도 실패로 구분한다.
            return new MediaGatewayException(
                    MediaGatewayException.Failure.CHANGED,
                    exception
            );
        }
        if (status.value() == 400
                || status.value() == 422) {
            MediaGatewayException.Failure validationFailure =
                    validationFailure(exception);
            if (validationFailure != null) {
                return new MediaGatewayException(
                        validationFailure,
                        exception
                );
            }
            return new MediaGatewayException(
                    MediaGatewayException.Failure.REJECTED,
                    exception
            );
        }
        return new MediaGatewayException(
                MediaGatewayException.Failure.UNAVAILABLE,
                exception
        );
    }

    private MediaGatewayException.Failure validationFailure(
            RestClientResponseException exception
    ) {
        try {
            JsonNode body = objectMapper.readTree(
                    exception.getResponseBodyAsString()
            );
            String code = body.path("code").asText();
            if (code.isBlank()) {
                code = body.path("error").path("code").asText();
            }
            if ("MEDIA_SIZE_MISMATCH".equals(code)) {
                return MediaGatewayException.Failure.SIZE_MISMATCH;
            }
            if ("MEDIA_CONTENT_TYPE_INVALID".equals(code)) {
                return MediaGatewayException.Failure
                        .CONTENT_TYPE_MISMATCH;
            }

            JsonNode details = body.path("details");
            JsonNode expectedSize = details.path(
                    "expectedSizeBytes"
            );
            JsonNode actualSize = details.path("actualSizeBytes");
            if (expectedSize.isNumber()
                    && actualSize.isNumber()
                    && expectedSize.asLong() != actualSize.asLong()) {
                return MediaGatewayException.Failure.SIZE_MISMATCH;
            }
            String expectedType = details.path(
                    "expectedContentType"
            ).asText();
            String actualType = details.path(
                    "actualContentType"
            ).asText();
            if (!expectedType.isBlank()
                    && !expectedType.equalsIgnoreCase(actualType)) {
                return MediaGatewayException.Failure
                        .CONTENT_TYPE_MISMATCH;
            }
        } catch (Exception ignored) {
            // Fall back to the HTTP status classification below.
        }
        return null;
    }

    public record UploadUrlRequest(
            FileUsage fileUsage,
            String contentType,
            long sizeBytes
    ) {
    }

    public record UploadUrlResponse(
            FileUsage fileUsage,
            @JsonAlias("s3Key")
            String uploadKey,
            String uploadUrl,
            String method,
            Map<String, String> requiredHeaders,
            @JsonAlias("sizeBytes")
            long expectedSizeBytes,
            Instant expiresAt
    ) {
    }

    public record VerifyRequest(
            FileUsage fileUsage,
            String uploadKey,
            String s3Key,
            String expectedContentType,
            long expectedSizeBytes
    ) {
    }

    public record VerifyResponse(
            boolean verified,
            FileUsage fileUsage,
            String s3Key,
            String contentType,
            long sizeBytes,
            String etag
    ) {
    }

    public record ObjectRequest(FileUsage fileUsage, String s3Key) {
    }

    public record DownloadUrlResponse(
            FileUsage fileUsage,
            String s3Key,
            String downloadUrl,
            Instant expiresAt
    ) {
    }

    public record DeleteResponse(
            boolean deleted,
            FileUsage fileUsage,
            String s3Key
    ) {
    }
}
