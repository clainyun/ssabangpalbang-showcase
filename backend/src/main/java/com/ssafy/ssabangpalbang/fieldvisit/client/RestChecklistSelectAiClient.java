package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitAiProperties;
import com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistFallbackReason;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * FastAPI {@code POST /internal/v1/checklists/select} RestClient 구현체다.
 *
 * <p>오류 매핑은 {@link RestChecklistAiClient}와 동일한 안전망을 따른다.
 * 잘못된 JSON·역직렬화 실패가 사용자 API 500으로 전파되지 않도록
 * {@link ChecklistAiException} 계열로 변환한다.</p>
 */
@RequiredArgsConstructor
public class RestChecklistSelectAiClient implements ChecklistSelectAiClient {

    private final RestClient restClient;
    private final FieldVisitAiProperties properties;

    @Override
    public ChecklistAiSelectResponse select(ChecklistAiSelectRequest request) {
        try {
            ChecklistAiSelectResponse response = restClient.post()
                    .uri(properties.selectPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChecklistAiSelectResponse.class);

            if (response == null) {
                throw new ChecklistAiInvalidResponseException(
                        ChecklistFallbackReason.AI_EMPTY_RESPONSE,
                        "FastAPI select returned an empty body"
                );
            }
            if (response.itemCodes() == null) {
                throw new ChecklistAiInvalidResponseException(
                        ChecklistFallbackReason.AI_MISSING_FIELD,
                        "FastAPI select response missing itemCodes"
                );
            }
            return response;
        } catch (ChecklistAiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status >= 500) {
                throw new ChecklistAiServerErrorException(
                        status,
                        "FastAPI select returned HTTP " + status
                );
            }
            throw new ChecklistAiInvalidResponseException(
                    ChecklistFallbackReason.AI_INVALID_JSON,
                    "FastAPI select returned HTTP " + status,
                    exception
            );
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new ChecklistAiTimeoutException(
                        "FastAPI select timed out",
                        exception
                );
            }
            throw new ChecklistAiConnectionException(
                    "Failed to connect to FastAPI select",
                    exception
            );
        } catch (RestClientException exception) {
            throw new ChecklistAiInvalidResponseException(
                    ChecklistFallbackReason.AI_INVALID_JSON,
                    "FastAPI select response could not be decoded",
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
