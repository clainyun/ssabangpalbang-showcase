package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.catalog.ChecklistCatalogSelectionService;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiClient;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiConnectionException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiInvalidResponseException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiResponseValidator;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiServerErrorException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiTimeoutException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiValidationResult;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitAiProperties;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistPersonalizationInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FastAPI 호출과 fallback 선택을 담당한다. 사용자 응답에는 내부 원인을
 * 노출하지 않고, 로그·테스트에서만 {@link ChecklistFallbackReason}으로 구분한다.
 *
 * <p>feature flag {@code catalogSelectionEnabled=false}이면 기존 generate 경로만
 * 사용한다. true이면 {@link ChecklistCatalogSelectionService}로 분기한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChecklistAiOrchestrator {

    private final ChecklistAiClient checklistAiClient;
    private final ChecklistAiResponseValidator responseValidator;
    private final FallbackChecklistProvider fallbackChecklistProvider;
    private final FieldVisitAiProperties fieldVisitAiProperties;
    private final ChecklistCatalogSelectionService catalogSelectionService;

    public GeneratedChecklistContent resolve(ChecklistPersonalizationResult personalization) {
        if (personalization.insufficient()) {
            return fallback(
                    personalization.input(),
                    ChecklistFallbackReason.PERSONALIZATION_INSUFFICIENT
            );
        }

        if (fieldVisitAiProperties.catalogSelectionEnabled()) {
            return catalogSelectionService.resolve(personalization.input());
        }

        return resolveLegacyGenerate(personalization.input());
    }

    private GeneratedChecklistContent resolveLegacyGenerate(
            ChecklistPersonalizationInput input
    ) {
        try {
            ChecklistAiGenerateResponse response = checklistAiClient.generate(
                    new ChecklistAiGenerateRequest(input)
            );
            ChecklistAiValidationResult validation = responseValidator.validate(response);
            if (!validation.valid()) {
                return fallback(input, validation.reason());
            }
            return GeneratedChecklistContent.ai(response);
        } catch (ChecklistAiConnectionException exception) {
            return fallback(
                    input,
                    ChecklistFallbackReason.AI_CONNECTION_FAILED,
                    exception
            );
        } catch (ChecklistAiTimeoutException exception) {
            return fallback(
                    input,
                    ChecklistFallbackReason.AI_TIMEOUT,
                    exception
            );
        } catch (ChecklistAiServerErrorException exception) {
            return fallback(
                    input,
                    ChecklistFallbackReason.AI_SERVER_ERROR,
                    exception
            );
        } catch (ChecklistAiInvalidResponseException exception) {
            ChecklistFallbackReason reason = exception.getReason() == null
                    ? ChecklistFallbackReason.AI_INVALID_JSON
                    : exception.getReason();
            return fallback(input, reason, exception);
        } catch (ChecklistAiException exception) {
            return fallback(
                    input,
                    ChecklistFallbackReason.AI_SERVER_ERROR,
                    exception
            );
        }
    }

    private GeneratedChecklistContent fallback(
            ChecklistPersonalizationInput input,
            ChecklistFallbackReason reason
    ) {
        return fallback(input, reason, null);
    }

    private GeneratedChecklistContent fallback(
            ChecklistPersonalizationInput input,
            ChecklistFallbackReason reason,
            Exception exception
    ) {
        if (exception == null) {
            log.warn("Using fallback checklist. reason={}", reason);
        } else {
            log.warn(
                    "Using fallback checklist. reason={}, cause={}",
                    reason,
                    exception.toString()
            );
        }
        return GeneratedChecklistContent.fallback(
                fallbackChecklistProvider.create(input),
                reason
        );
    }
}
