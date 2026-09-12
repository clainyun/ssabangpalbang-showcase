package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiConnectionException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiResponseValidator;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiServerErrorException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiTimeoutException;
import com.ssafy.ssabangpalbang.fieldvisit.catalog.ChecklistCatalogSelectionService;
import com.ssafy.ssabangpalbang.fieldvisit.client.FakeChecklistAiClient;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitAiProperties;
import static org.mockito.Mockito.mock;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistPersonalizationInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChecklistAiOrchestratorTest {

    private FallbackChecklistProvider fallbackProvider;
    private ChecklistAiResponseValidator validator;

    @BeforeEach
    void setUp() {
        fallbackProvider = new FallbackChecklistProvider();
        validator = new ChecklistAiResponseValidator();
    }

    @Test
    void 온보딩_부족이면_AI를_호출하지_않고_fallback한다() {
        FakeChecklistAiClient client = FakeChecklistAiClient.returning(validAiResponse());
        ChecklistAiOrchestrator orchestrator =
                new ChecklistAiOrchestrator(client, validator, fallbackProvider, disabledProperties(), mock(ChecklistCatalogSelectionService.class));

        GeneratedChecklistContent content = orchestrator.resolve(
                new ChecklistPersonalizationResult(sampleInput(), true)
        );

        assertThat(content.fallback()).isTrue();
        assertThat(content.fallbackReason())
                .isEqualTo(ChecklistFallbackReason.PERSONALIZATION_INSUFFICIENT);
        assertThat(content.response().items()).isNotEmpty();
        assertThat(client.getLastRequest()).isNull();
    }

    @Test
    void AI_정상_응답이면_fallback이_아니다() {
        FakeChecklistAiClient client = FakeChecklistAiClient.returning(validAiResponse());
        ChecklistAiOrchestrator orchestrator =
                new ChecklistAiOrchestrator(client, validator, fallbackProvider, disabledProperties(), mock(ChecklistCatalogSelectionService.class));

        GeneratedChecklistContent content = orchestrator.resolve(
                new ChecklistPersonalizationResult(sampleInput(), false)
        );

        assertThat(content.fallback()).isFalse();
        assertThat(content.response().items()).hasSize(2);
        assertThat(client.getLastRequest()).isNotNull();
    }

    @Test
    void 연결_실패면_fallback한다() {
        FakeChecklistAiClient client = FakeChecklistAiClient.throwing(
                new ChecklistAiConnectionException("down")
        );
        ChecklistAiOrchestrator orchestrator =
                new ChecklistAiOrchestrator(client, validator, fallbackProvider, disabledProperties(), mock(ChecklistCatalogSelectionService.class));

        GeneratedChecklistContent content = orchestrator.resolve(
                new ChecklistPersonalizationResult(sampleInput(), false)
        );

        assertThat(content.fallback()).isTrue();
        assertThat(content.fallbackReason())
                .isEqualTo(ChecklistFallbackReason.AI_CONNECTION_FAILED);
        assertThat(content.response().items()).hasSize(25);
    }

    @Test
    void timeout이면_fallback한다() {
        FakeChecklistAiClient client = FakeChecklistAiClient.throwing(
                new ChecklistAiTimeoutException("timeout")
        );
        ChecklistAiOrchestrator orchestrator =
                new ChecklistAiOrchestrator(client, validator, fallbackProvider, disabledProperties(), mock(ChecklistCatalogSelectionService.class));

        GeneratedChecklistContent content = orchestrator.resolve(
                new ChecklistPersonalizationResult(sampleInput(), false)
        );

        assertThat(content.fallbackReason())
                .isEqualTo(ChecklistFallbackReason.AI_TIMEOUT);
    }

    @Test
    void 서버_5xx면_fallback한다() {
        FakeChecklistAiClient client = FakeChecklistAiClient.throwing(
                new ChecklistAiServerErrorException(500, "boom")
        );
        ChecklistAiOrchestrator orchestrator =
                new ChecklistAiOrchestrator(client, validator, fallbackProvider, disabledProperties(), mock(ChecklistCatalogSelectionService.class));

        GeneratedChecklistContent content = orchestrator.resolve(
                new ChecklistPersonalizationResult(sampleInput(), false)
        );

        assertThat(content.fallbackReason())
                .isEqualTo(ChecklistFallbackReason.AI_SERVER_ERROR);
    }

    @Test
    void 빈_items면_fallback한다() {
        FakeChecklistAiClient client = FakeChecklistAiClient.returning(
                new ChecklistAiGenerateResponse(List.of())
        );
        ChecklistAiOrchestrator orchestrator =
                new ChecklistAiOrchestrator(client, validator, fallbackProvider, disabledProperties(), mock(ChecklistCatalogSelectionService.class));

        GeneratedChecklistContent content = orchestrator.resolve(
                new ChecklistPersonalizationResult(sampleInput(), false)
        );

        assertThat(content.fallbackReason())
                .isEqualTo(ChecklistFallbackReason.AI_EMPTY_ITEMS);
    }

    @Test
    void fallback은_priorities_순서를_앞에_반영한다() {
        ChecklistPersonalizationInput input = new ChecklistPersonalizationInput(
                new ChecklistPersonalizationInput.MemberOnboardingSection(
                        "RESIDENCE",
                        "SINGLE",
                        true,
                        false,
                        List.of("NOISE", "PARKING"),
                        "THIRTIES"
                ),
                new ChecklistPersonalizationInput.ApartmentSection(
                        1L, "A", "addr", "d", "dong", 100, "201001", 50
                ),
                new ChecklistPersonalizationInput.StudySection(7L, "RESIDENCE", "goal")
        );

        ChecklistAiGenerateResponse response = fallbackProvider.create(input);

        assertThat(response.items().get(0).title()).contains("소음");
        assertThat(response.items().get(1).title()).contains("주차");
        assertThat(response.items()).allSatisfy(item -> {
            assertThat(item.category()).isNotBlank();
            assertThat(item.title()).isNotBlank();
            assertThat(item.displayOrder()).isGreaterThanOrEqualTo(1);
        });
    }

    private ChecklistAiGenerateResponse validAiResponse() {
        return new ChecklistAiGenerateResponse(List.of(
                new ChecklistAiGenerateResponse.Item("교통", "역까지 도보", "확인", 1, null),
                new ChecklistAiGenerateResponse.Item("소음", "도로 소음", null, 2, null)
        ));
    }



    @Test
    void feature_flag_false면_카탈로그_서비스를_호출하지_않는다() {
        FakeChecklistAiClient client = FakeChecklistAiClient.returning(validAiResponse());
        ChecklistCatalogSelectionService catalogService = mock(ChecklistCatalogSelectionService.class);
        ChecklistAiOrchestrator orchestrator =
                new ChecklistAiOrchestrator(client, validator, fallbackProvider, disabledProperties(), catalogService);

        GeneratedChecklistContent content = orchestrator.resolve(
                new ChecklistPersonalizationResult(sampleInput(), false)
        );

        assertThat(content.fallback()).isFalse();
        assertThat(client.getLastRequest()).isNotNull();
        org.mockito.Mockito.verifyNoInteractions(catalogService);
    }

    @Test
    void feature_flag_true면_카탈로그_서비스를_호출하고_legacy_generate는_호출하지_않는다() {
        FakeChecklistAiClient client = FakeChecklistAiClient.returning(validAiResponse());
        ChecklistCatalogSelectionService catalogService = mock(ChecklistCatalogSelectionService.class);
        org.mockito.Mockito.when(catalogService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(GeneratedChecklistContent.ai(validAiResponse()));

        FieldVisitAiProperties properties = new FieldVisitAiProperties();
        properties.setCatalogSelectionEnabled(true);
        ChecklistAiOrchestrator orchestrator =
                new ChecklistAiOrchestrator(client, validator, fallbackProvider, properties, catalogService);

        GeneratedChecklistContent content = orchestrator.resolve(
                new ChecklistPersonalizationResult(sampleInput(), false)
        );

        assertThat(content.fallback()).isFalse();
        assertThat(content.response().items()).hasSize(2);
        assertThat(client.getLastRequest()).isNull();
        org.mockito.Mockito.verify(catalogService).resolve(org.mockito.ArgumentMatchers.any());
    }

    private FieldVisitAiProperties disabledProperties() {
        FieldVisitAiProperties properties = new FieldVisitAiProperties();
        properties.setCatalogSelectionEnabled(false);
        return properties;
    }

    private ChecklistPersonalizationInput sampleInput() {
        return new ChecklistPersonalizationInput(
                new ChecklistPersonalizationInput.MemberOnboardingSection(
                        "RESIDENCE",
                        "SINGLE",
                        true,
                        false,
                        List.of("TRANSPORT", "SAFETY"),
                        "THIRTIES"
                ),
                new ChecklistPersonalizationInput.ApartmentSection(
                        1L, "단지", "서울", "강남구", "역삼동", 500, "201501", 300
                ),
                new ChecklistPersonalizationInput.StudySection(7L, "RESIDENCE", "임장 목표")
        );
    }
}
