package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitAiProperties;
import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * FastAPI {@code POST /internal/v1/checklists/generate} RestClient 구현체다.
 * MVP에서는 자동 재시도하지 않는다({@code retryCount}는 설정만 보관).
 */
@Slf4j
@RequiredArgsConstructor
public class RestChecklistAiClient implements ChecklistAiClient {

    private final RestClient restClient;
    private final FieldVisitAiProperties properties;

    @Override
    public ChecklistAiGenerateResponse generate(ChecklistAiGenerateRequest request) {
        try {
            ChecklistAiGenerateResponse response = restClient.post()
                    .uri(properties.generatePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChecklistAiGenerateResponse.class);

            if (response == null) {
                throw new ChecklistAiInvalidResponseException(
                        ChecklistFallbackReason.AI_EMPTY_RESPONSE,
                        "FastAPI returned an empty body"
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status >= 500) {
                throw new ChecklistAiServerErrorException(
                        status,
                        "FastAPI returned HTTP " + status
                );
            }
            throw new ChecklistAiInvalidResponseException(
                    ChecklistFallbackReason.AI_INVALID_JSON,
                    "FastAPI returned HTTP " + status,
                    exception
            );
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new ChecklistAiTimeoutException(
                        "FastAPI call timed out",
                        exception
                );
            }
            throw new ChecklistAiConnectionException(
                    "Failed to connect to FastAPI",
                    exception
            );
        }
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }
}
