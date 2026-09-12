package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.catalog.ChecklistCatalogSelectionService;
import com.ssafy.ssabangpalbang.fieldvisit.client.FakeChecklistAiClient;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitAiProperties;
import static org.mockito.Mockito.mock;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiConnectionException;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiResponseValidator;
import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationStage;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistPersonalizationInput;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistAnswerRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChecklistServiceTest {

    private static final String ATTEMPT_ID = "attempt-1";

    @Mock FieldVisitAccessService accessService;
    @Mock ChecklistPersonalizationReader personalizationReader;
    @Mock ChecklistWriter checklistWriter;
    @Mock ChecklistRepository checklistRepository;
    @Mock ChecklistItemRepository checklistItemRepository;
    @Mock ChecklistAnswerRepository checklistAnswerRepository;
    @Mock FieldRecordCountRepository fieldRecordCountRepository;
    @Mock ChecklistGenerationProgressService generationProgressService;
    @Mock FieldSession session;
    @Mock FieldParticipant participant;
    @Mock Checklist existingChecklist;

    private ChecklistService service;
    private FakeChecklistAiClient aiClient;
    private FallbackChecklistProvider fallbackProvider;

    @BeforeEach
    void setUp() {
        fallbackProvider = new FallbackChecklistProvider();
        aiClient = FakeChecklistAiClient.returning(new ChecklistAiGenerateResponse(List.of(
                new ChecklistAiGenerateResponse.Item("교통", "역", "확인", 1, null)
        )));
        ChecklistAiOrchestrator orchestrator = new ChecklistAiOrchestrator(aiClient, new ChecklistAiResponseValidator(), fallbackProvider, disabledAiProperties(), mock(ChecklistCatalogSelectionService.class));
        service = new ChecklistService(
                accessService,
                personalizationReader,
                orchestrator,
                checklistWriter,
                checklistRepository,
                checklistItemRepository,
                checklistAnswerRepository,
                fieldRecordCountRepository,
                generationProgressService
        );
        lenient().when(session.getId()).thenReturn(100L);
    }

    @Test
    void 기존_체크리스트가_있으면_AI를_호출하지_않는다() {
        when(accessService.requireChecklistGenerationParticipation(
                eq(7L), eq(1L), eq(ErrorCode.CHECKLIST_GENERATE_FORBIDDEN)
        )).thenReturn(new FieldVisitParticipation(session, participant));
        when(checklistRepository.findBySessionIdAndMemberId(100L, 1L))
                .thenReturn(Optional.of(existingChecklist));
        when(existingChecklist.getId()).thenReturn(55L);
        when(existingChecklist.getSessionId()).thenReturn(100L);
        when(existingChecklist.isFallback()).thenReturn(false);
        when(existingChecklist.getGeneratedAt()).thenReturn(java.time.Instant.parse("2026-07-25T05:03:00Z"));
        when(checklistItemRepository.findByChecklistIdOrderByDisplayOrderAsc(55L))
                .thenReturn(List.of());
        when(checklistAnswerRepository.findByChecklistItemIdIn(List.of()))
                .thenReturn(List.of());
        when(fieldRecordCountRepository.countByAuthorAndItemIds(eq(1L), any()))
                .thenReturn(Map.of());

        ChecklistService.GenerateResult result = service.generate(7L, 1L, ATTEMPT_ID);

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.responseCode())
                .isEqualTo(ChecklistResponseCode.CHECKLIST_ALREADY_EXISTS);
        assertThat(aiClient.getLastRequest()).isNull();
        verify(checklistWriter, never()).save(anyLong(), anyLong(), any());
        verify(generationProgressService, never()).restart(100L, 1L, ATTEMPT_ID);
        verify(generationProgressService).advance(
                100L,
                1L,
                ATTEMPT_ID,
                ChecklistGenerationStage.COMPLETED
        );
    }

    @Test
    void UNIQUE_충돌_시_기존_결과를_반환한다() {
        when(accessService.requireChecklistGenerationParticipation(
                eq(7L), eq(1L), eq(ErrorCode.CHECKLIST_GENERATE_FORBIDDEN)
        )).thenReturn(new FieldVisitParticipation(session, participant));
        when(checklistRepository.findBySessionIdAndMemberId(100L, 1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingChecklist));
        when(personalizationReader.read(1L, 7L))
                .thenReturn(new ChecklistPersonalizationResult(sampleInput(), false));
        when(checklistWriter.save(eq(100L), eq(1L), any()))
                .thenThrow(new DataIntegrityViolationException("unique"));
        when(existingChecklist.getId()).thenReturn(55L);
        when(existingChecklist.getSessionId()).thenReturn(100L);
        when(existingChecklist.isFallback()).thenReturn(false);
        when(existingChecklist.getGeneratedAt())
                .thenReturn(java.time.Instant.parse("2026-07-25T05:03:00Z"));
        when(checklistItemRepository.findByChecklistIdOrderByDisplayOrderAsc(55L))
                .thenReturn(List.of());
        when(checklistAnswerRepository.findByChecklistItemIdIn(List.of()))
                .thenReturn(List.of());
        when(fieldRecordCountRepository.countByAuthorAndItemIds(eq(1L), any()))
                .thenReturn(Map.of());

        ChecklistService.GenerateResult result = service.generate(7L, 1L, ATTEMPT_ID);

        assertThat(result.responseCode())
                .isEqualTo(ChecklistResponseCode.CHECKLIST_ALREADY_EXISTS);
        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void 권한_실패는_전파한다() {
        when(accessService.requireChecklistGenerationParticipation(
                eq(7L), eq(1L), eq(ErrorCode.CHECKLIST_GENERATE_FORBIDDEN)
        )).thenThrow(new BusinessException(ErrorCode.CHECKLIST_GENERATE_FORBIDDEN));

        assertThatThrownBy(() -> service.generate(7L, 1L, ATTEMPT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHECKLIST_GENERATE_FORBIDDEN);
    }

    @Test
    void generationProgressAdvancesWithSuccessfulBackendStages() {
        when(accessService.requireChecklistGenerationParticipation(
                eq(7L), eq(1L), eq(ErrorCode.CHECKLIST_GENERATE_FORBIDDEN)
        )).thenReturn(new FieldVisitParticipation(session, participant));
        when(checklistRepository.findBySessionIdAndMemberId(100L, 1L))
                .thenReturn(Optional.empty());
        when(personalizationReader.read(1L, 7L))
                .thenReturn(new ChecklistPersonalizationResult(sampleInput(), false));
        when(checklistWriter.save(eq(100L), eq(1L), any()))
                .thenReturn(new ChecklistWriter.SavedChecklist(existingChecklist, List.of()));
        stubGeneratedChecklist(false);

        ChecklistService.GenerateResult result = service.generate(7L, 1L, ATTEMPT_ID);

        assertThat(result.responseCode())
                .isEqualTo(ChecklistResponseCode.CHECKLIST_GENERATE_SUCCESS);
        InOrder progress = inOrder(generationProgressService);
        progress.verify(generationProgressService).restart(100L, 1L, ATTEMPT_ID);
        progress.verify(generationProgressService).advance(
                100L, 1L, ATTEMPT_ID, ChecklistGenerationStage.PERSONALIZATION
        );
        progress.verify(generationProgressService).advance(
                100L, 1L, ATTEMPT_ID, ChecklistGenerationStage.AI_GENERATION
        );
        progress.verify(generationProgressService).advance(
                100L, 1L, ATTEMPT_ID, ChecklistGenerationStage.CONTENT_READY
        );
        progress.verify(generationProgressService).advance(
                100L, 1L, ATTEMPT_ID, ChecklistGenerationStage.RESULT_SAVING
        );
        progress.verify(generationProgressService).advance(
                100L, 1L, ATTEMPT_ID, ChecklistGenerationStage.COMPLETED
        );
    }

    @Test
    void fallbackProgressSkipsAiStageAndStillCompletes() {
        when(accessService.requireChecklistGenerationParticipation(
                eq(7L), eq(1L), eq(ErrorCode.CHECKLIST_GENERATE_FORBIDDEN)
        )).thenReturn(new FieldVisitParticipation(session, participant));
        when(checklistRepository.findBySessionIdAndMemberId(100L, 1L))
                .thenReturn(Optional.empty());
        when(personalizationReader.read(1L, 7L))
                .thenReturn(new ChecklistPersonalizationResult(sampleInput(), true));
        when(checklistWriter.save(eq(100L), eq(1L), any()))
                .thenReturn(new ChecklistWriter.SavedChecklist(existingChecklist, List.of()));
        stubGeneratedChecklist(true);

        ChecklistService.GenerateResult result = service.generate(7L, 1L, ATTEMPT_ID);

        assertThat(result.responseCode())
                .isEqualTo(ChecklistResponseCode.CHECKLIST_FALLBACK_GENERATE_SUCCESS);
        verify(generationProgressService, never()).advance(
                100L, 1L, ATTEMPT_ID, ChecklistGenerationStage.AI_GENERATION
        );
        verify(generationProgressService).advance(
                100L, 1L, ATTEMPT_ID, ChecklistGenerationStage.COMPLETED
        );
    }

    @Test
    void aiFailureFallbackKeepsAiStageAndCompletes() {
        aiClient = FakeChecklistAiClient.throwing(
                new ChecklistAiConnectionException("AI unavailable")
        );
        service = new ChecklistService(
                accessService,
                personalizationReader,
                new ChecklistAiOrchestrator(aiClient, new ChecklistAiResponseValidator(), fallbackProvider, disabledAiProperties(), mock(ChecklistCatalogSelectionService.class)),
                checklistWriter,
                checklistRepository,
                checklistItemRepository,
                checklistAnswerRepository,
                fieldRecordCountRepository,
                generationProgressService
        );
        when(accessService.requireChecklistGenerationParticipation(
                eq(7L), eq(1L), eq(ErrorCode.CHECKLIST_GENERATE_FORBIDDEN)
        )).thenReturn(new FieldVisitParticipation(session, participant));
        when(checklistRepository.findBySessionIdAndMemberId(100L, 1L))
                .thenReturn(Optional.empty());
        when(personalizationReader.read(1L, 7L))
                .thenReturn(new ChecklistPersonalizationResult(sampleInput(), false));
        when(checklistWriter.save(eq(100L), eq(1L), any()))
                .thenReturn(new ChecklistWriter.SavedChecklist(existingChecklist, List.of()));
        stubGeneratedChecklist(true);

        ChecklistService.GenerateResult result = service.generate(7L, 1L, ATTEMPT_ID);

        assertThat(result.responseCode())
                .isEqualTo(ChecklistResponseCode.CHECKLIST_FALLBACK_GENERATE_SUCCESS);
        verify(generationProgressService).advance(
                100L, 1L, ATTEMPT_ID, ChecklistGenerationStage.AI_GENERATION
        );
        verify(generationProgressService).advance(
                100L, 1L, ATTEMPT_ID, ChecklistGenerationStage.COMPLETED
        );
        verify(generationProgressService, never()).fail(100L, 1L, ATTEMPT_ID);
    }

    @Test
    void generationFailureIsRecordedWithoutReportingCompletion() {
        when(accessService.requireChecklistGenerationParticipation(
                eq(7L), eq(1L), eq(ErrorCode.CHECKLIST_GENERATE_FORBIDDEN)
        )).thenReturn(new FieldVisitParticipation(session, participant));
        when(checklistRepository.findBySessionIdAndMemberId(100L, 1L))
                .thenReturn(Optional.empty());
        when(personalizationReader.read(1L, 7L))
                .thenThrow(new IllegalStateException("personalization failed"));

        assertThatThrownBy(() -> service.generate(7L, 1L, ATTEMPT_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(generationProgressService).fail(100L, 1L, ATTEMPT_ID);
        verify(generationProgressService, never()).advance(
                100L, 1L, ATTEMPT_ID, ChecklistGenerationStage.COMPLETED
        );
    }

    private void stubGeneratedChecklist(boolean fallback) {
        when(existingChecklist.getId()).thenReturn(55L);
        when(existingChecklist.getSessionId()).thenReturn(100L);
        when(existingChecklist.isFallback()).thenReturn(fallback);
        when(existingChecklist.getGeneratedAt())
                .thenReturn(java.time.Instant.parse("2026-07-25T05:03:00Z"));
        when(checklistAnswerRepository.findByChecklistItemIdIn(List.of()))
                .thenReturn(List.of());
        when(fieldRecordCountRepository.countByAuthorAndItemIds(eq(1L), any()))
                .thenReturn(Map.of());
    }

    private static FieldVisitAiProperties disabledAiProperties() {
        FieldVisitAiProperties properties = new FieldVisitAiProperties();
        properties.setCatalogSelectionEnabled(false);
        return properties;
    }

    private ChecklistPersonalizationInput sampleInput() {
        return new ChecklistPersonalizationInput(
                new ChecklistPersonalizationInput.MemberOnboardingSection(
                        "RESIDENCE", "SINGLE", true, false,
                        List.of("TRANSPORT"), "THIRTIES"
                ),
                new ChecklistPersonalizationInput.ApartmentSection(
                        1L, "단지", "서울", "강남", "역삼", 100, "2010", 50
                ),
                new ChecklistPersonalizationInput.StudySection(7L, "RESIDENCE", "goal")
        );
    }
}
