package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationProgress;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationProgressStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationStage;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.ChecklistGenerationStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistGenerationProgressRepository;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChecklistGenerationProgressServiceTest {

    private static final String ATTEMPT_ID = "attempt-1";

    @Mock ChecklistGenerationProgressWriter writer;
    @Mock ChecklistGenerationProgressRepository repository;
    @Mock FieldVisitAccessService accessService;
    @Mock FieldSession session;
    @Mock ChecklistGenerationProgress progress;

    private ChecklistGenerationProgressService service;

    @BeforeEach
    void setUp() {
        service = new ChecklistGenerationProgressService(writer, repository, accessService);
    }

    @Test
    void returnsPendingBeforeTheFieldSessionStarts() {
        when(accessService.readStatus(7L, 1L, ErrorCode.CHECKLIST_ACCESS_DENIED))
                .thenReturn(new FieldVisitReadStatus(null, null, false));

        ChecklistGenerationStatusResponse response = service.getStatus(7L, 1L, ATTEMPT_ID);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.progressRate()).isZero();
        verify(repository, never())
                .findBySessionIdAndMemberIdAndAttemptId(100L, 1L, ATTEMPT_ID);
    }

    @Test
    void returnsTheLatestPersistedBackendStage() {
        Instant updatedAt = Instant.parse("2026-08-04T05:03:00Z");
        when(accessService.readStatus(7L, 1L, ErrorCode.CHECKLIST_ACCESS_DENIED))
                .thenReturn(new FieldVisitReadStatus(session, null, false));
        when(session.getId()).thenReturn(100L);
        when(repository.findBySessionIdAndMemberIdAndAttemptId(100L, 1L, ATTEMPT_ID))
                .thenReturn(Optional.of(progress));
        when(progress.getAttemptId()).thenReturn(ATTEMPT_ID);
        when(progress.getStatus())
                .thenReturn(ChecklistGenerationProgressStatus.IN_PROGRESS);
        when(progress.getStage()).thenReturn(ChecklistGenerationStage.AI_GENERATION);
        when(progress.getProgressRate()).thenReturn(55);
        when(progress.getMessage()).thenReturn("맞춤 체크 항목을 만들고 있어요.");
        when(progress.getUpdatedAt()).thenReturn(updatedAt);

        ChecklistGenerationStatusResponse response = service.getStatus(7L, 1L, ATTEMPT_ID);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.attemptId()).isEqualTo(ATTEMPT_ID);
        assertThat(response.progressRate()).isEqualTo(55);
        assertThat(response.progressStage()).isEqualTo("AI_GENERATION");
        assertThat(response.updatedAt().toInstant()).isEqualTo(updatedAt);
    }

    @Test
    void progressStorageFailureDoesNotBreakChecklistGeneration() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(writer)
                .advance(100L, 1L, ATTEMPT_ID, ChecklistGenerationStage.AI_GENERATION);

        assertThatCode(() -> service.advance(
                100L,
                1L,
                ATTEMPT_ID,
                ChecklistGenerationStage.AI_GENERATION
        )).doesNotThrowAnyException();

        verify(writer).advance(
                100L,
                1L,
                ATTEMPT_ID,
                ChecklistGenerationStage.AI_GENERATION
        );
    }
}
