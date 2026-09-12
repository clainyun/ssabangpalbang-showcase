package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistAnswer;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.ChecklistAnswerSaveRequest;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistAnswerRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChecklistAnswerServiceTest {

    private FieldVisitAccessService accessService;
    private ChecklistRepository checklistRepository;
    private ChecklistItemRepository checklistItemRepository;
    private ChecklistAnswerRepository checklistAnswerRepository;
    private ChecklistAnswerService service;

    @BeforeEach
    void setUp() {
        accessService = mock(FieldVisitAccessService.class);
        checklistRepository = mock(ChecklistRepository.class);
        checklistItemRepository = mock(ChecklistItemRepository.class);
        checklistAnswerRepository = mock(ChecklistAnswerRepository.class);
        service = new ChecklistAnswerService(
                accessService,
                checklistRepository,
                checklistItemRepository,
                checklistAnswerRepository
        );
    }

    @Test
    void 일괄_저장에_성공한다() {
        FieldSession session = mock(FieldSession.class);
        when(session.getId()).thenReturn(100L);
        FieldParticipant participant = mock(FieldParticipant.class);
        when(accessService.requireWritableParticipation(any(), any(), any(), any()))
                .thenReturn(new FieldVisitParticipation(session, participant));

        Checklist checklist = Checklist.create(100L, 42L, false);
        // id via reflection-less: mock checklist
        Checklist checklistMock = mock(Checklist.class);
        when(checklistMock.getId()).thenReturn(55L);
        when(checklistRepository.findBySessionIdAndMemberIdForUpdate(100L, 42L))
                .thenReturn(Optional.of(checklistMock));

        ChecklistItem item1 = ChecklistItem.create(55L, "교통", "역", null, 1, null);
        ChecklistItem item2 = ChecklistItem.create(55L, "교통", "버스", null, 2, null);
        // need ids - use mocks
        ChecklistItem i1 = mock(ChecklistItem.class);
        when(i1.getId()).thenReturn(501L);
        when(i1.getCategory()).thenReturn("교통");
        ChecklistItem i2 = mock(ChecklistItem.class);
        when(i2.getId()).thenReturn(502L);
        when(i2.getCategory()).thenReturn("교통");

        when(checklistItemRepository.findByIdInAndChecklistId(List.of(501L, 502L), 55L))
                .thenReturn(List.of(i1, i2));
        when(checklistAnswerRepository.findByChecklistItemIdIn(anyCollection()))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(checklistItemRepository.findByChecklistIdOrderByDisplayOrderAsc(55L))
                .thenReturn(List.of(i1, i2));
        when(checklistAnswerRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ChecklistAnswerSaveRequest request = new ChecklistAnswerSaveRequest(List.of(
                new ChecklistAnswerSaveRequest.AnswerItem(501L, true),
                new ChecklistAnswerSaveRequest.AnswerItem(502L, false)
        ));

        var result = service.saveAnswers(7L, 42L, request);

        assertThat(result.body().savedCount()).isEqualTo(2);
        assertThat(result.body().checklistId()).isEqualTo(55L);
        ArgumentCaptor<List<ChecklistAnswer>> captor = ArgumentCaptor.forClass(List.class);
        verify(checklistAnswerRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void 중복_checklistItemId면_실패한다() {
        FieldSession session = mock(FieldSession.class);
        when(session.getId()).thenReturn(100L);
        when(accessService.requireWritableParticipation(any(), any(), any(), any()))
                .thenReturn(new FieldVisitParticipation(session, mock(FieldParticipant.class)));

        ChecklistAnswerSaveRequest request = new ChecklistAnswerSaveRequest(List.of(
                new ChecklistAnswerSaveRequest.AnswerItem(501L, true),
                new ChecklistAnswerSaveRequest.AnswerItem(501L, false)
        ));

        assertThatThrownBy(() -> service.saveAnswers(7L, 42L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHECKLIST_ITEM_DUPLICATED);
    }

    @Test
    void 타인_항목이면_전체_실패한다() {
        FieldSession session = mock(FieldSession.class);
        when(session.getId()).thenReturn(100L);
        when(accessService.requireWritableParticipation(any(), any(), any(), any()))
                .thenReturn(new FieldVisitParticipation(session, mock(FieldParticipant.class)));
        Checklist checklistMock = mock(Checklist.class);
        when(checklistMock.getId()).thenReturn(55L);
        when(checklistRepository.findBySessionIdAndMemberIdForUpdate(100L, 42L))
                .thenReturn(Optional.of(checklistMock));
        when(checklistItemRepository.findByIdInAndChecklistId(List.of(501L), 55L))
                .thenReturn(List.of());

        ChecklistAnswerSaveRequest request = new ChecklistAnswerSaveRequest(List.of(
                new ChecklistAnswerSaveRequest.AnswerItem(501L, true)
        ));

        assertThatThrownBy(() -> service.saveAnswers(7L, 42L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHECKLIST_ITEM_ACCESS_DENIED);
    }
}
