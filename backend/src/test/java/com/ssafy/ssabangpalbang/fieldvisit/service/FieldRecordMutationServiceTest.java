package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecord;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldRecordResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldRecordUpdateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FieldRecordMutationServiceTest {

    private FieldVisitAccessService accessService;
    private ChecklistItemRepository checklistItemRepository;
    private FieldRecordRepository fieldRecordRepository;
    private FieldRecordCountRepository fieldRecordCountRepository;
    private MemberRepository memberRepository;
    private MediaAccessUrlProvider urlProvider;
    private FieldRecordService service;

    @BeforeEach
    void setUp() {
        accessService = mock(FieldVisitAccessService.class);
        checklistItemRepository = mock(ChecklistItemRepository.class);
        fieldRecordRepository = mock(FieldRecordRepository.class);
        fieldRecordCountRepository = mock(FieldRecordCountRepository.class);
        memberRepository = mock(MemberRepository.class);
        urlProvider = mock(MediaAccessUrlProvider.class);
        when(urlProvider.issueAll(any())).thenReturn(Map.of());
        when(fieldRecordCountRepository.countByAuthorAndItemIds(any(), anyCollection()))
                .thenReturn(Map.of(501L, 1, 502L, 0));

        service = new FieldRecordService(
                accessService,
                checklistItemRepository,
                fieldRecordRepository,
                mock(FieldRecordWriter.class),
                mock(FieldPhotoValidator.class),
                fieldRecordCountRepository,
                mock(FileMetaRepository.class),
                urlProvider,
                memberRepository
        );

        FieldSession session = mock(FieldSession.class);
        when(session.getId()).thenReturn(100L);
        when(session.getStatus()).thenReturn(FieldSessionStatus.IN_PROGRESS);
        FieldParticipant participant = mock(FieldParticipant.class);
        when(participant.getStatus()).thenReturn(FieldParticipantStatus.IN_PROGRESS);
        when(accessService.requireWritableParticipation(any(), any(), any(), any()))
                .thenReturn(new FieldVisitParticipation(session, participant));
        when(accessService.readRecordStatus(any(), any(), any()))
                .thenReturn(new FieldVisitReadStatus(session, participant, false));
    }

    @Test
    void TEXT_내용을_수정한다() {
        FieldRecord record = textRecord(900L, 501L, 42L, "old");
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(900L, 100L))
                .thenReturn(Optional.of(record));
        when(fieldRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.update(7L, 42L, 900L, new FieldRecordUpdateRequest(
                null, "new text", null
        ));

        assertThat(response.sourceId()).isEqualTo(900L);
        assertThat(response.textContent()).isEqualTo("new text");
        assertThat(record.getClientRequestId())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void STT_DONE이면_텍스트_교정_가능하다() {
        FieldRecord record = FieldRecord.reconstructStt(
                100L, 501L, 42L, "원문", FieldRecord.STT_STATUS_DONE,
                "STT:abc", Instant.now()
        );
        ReflectionTestUtils.setField(record, "id", 901L);
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(901L, 100L))
                .thenReturn(Optional.of(record));
        when(fieldRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.update(7L, 42L, 901L, new FieldRecordUpdateRequest(
                null, "교정문", null
        ));

        assertThat(response.textContent()).isEqualTo("교정문");
        assertThat(response.sourceType()).isEqualTo("STT");
    }

    @Test
    void STT_진행중이면_수정을_차단한다() {
        FieldRecord record = FieldRecord.reconstructStt(
                100L, 501L, 42L, null, FieldRecord.STT_STATUS_PROCESSING,
                "STT:abc", Instant.now()
        );
        ReflectionTestUtils.setField(record, "id", 902L);
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(902L, 100L))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.update(7L, 42L, 902L, new FieldRecordUpdateRequest(
                null, "x", null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_STT_PROCESSING);
    }

    @Test
    void 타인_기록은_수정할_수_없다() {
        FieldRecord record = textRecord(903L, 501L, 99L, "old");
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(903L, 100L))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.update(7L, 42L, 903L, new FieldRecordUpdateRequest(
                null, "x", null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_UPDATE_FORBIDDEN);
    }

    @Test
    void checklistItemId를_변경한다() {
        FieldRecord record = textRecord(904L, 501L, 42L, "old");
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(904L, 100L))
                .thenReturn(Optional.of(record));
        when(checklistItemRepository.findOwnedItem(502L, 100L, 42L))
                .thenReturn(Optional.of(mock(ChecklistItem.class)));
        when(fieldRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.update(7L, 42L, 904L, new FieldRecordUpdateRequest(
                502L, null, null
        ));

        assertThat(response.checklistItemId()).isEqualTo(502L);
        assertThat(response.sourceId()).isEqualTo(904L);
    }

    @Test
    void 본인_기록을_soft_delete한다() {
        FieldRecord record = textRecord(905L, 501L, 42L, "old");
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(905L, 100L))
                .thenReturn(Optional.of(record));
        when(fieldRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.delete(7L, 42L, 905L);

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.responseCode()).isEqualTo(FieldRecordResponseCode.FIELD_RECORD_DELETE_SUCCESS);
        assertThat(record.isDeleted()).isTrue();
    }

    @Test
    void 이미_삭제된_기록_재요청은_멱등이다() {
        FieldRecord record = textRecord(906L, 501L, 42L, "old");
        record.softDelete(Instant.now());
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(906L, 100L))
                .thenReturn(Optional.of(record));

        var result = service.delete(7L, 42L, 906L);

        assertThat(result.responseCode())
                .isEqualTo(FieldRecordResponseCode.FIELD_RECORD_ALREADY_DELETED);
    }

    @Test
    void STT_진행중이면_삭제를_차단한다() {
        FieldRecord record = FieldRecord.reconstructStt(
                100L, 501L, 42L, null, FieldRecord.STT_STATUS_PENDING,
                "STT:pending", Instant.now()
        );
        ReflectionTestUtils.setField(record, "id", 907L);
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(907L, 100L))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.delete(7L, 42L, 907L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_STT_PROCESSING);
    }

    @Test
    void TEXT_공백_PATCH는_400이다() {
        FieldRecord record = textRecord(908L, 501L, 42L, "old");
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(908L, 100L))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.update(7L, 42L, 908L, new FieldRecordUpdateRequest(
                null, "   ", null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_PAYLOAD_INVALID);
    }

    @Test
    void STT_DONE_공백_PATCH는_400이다() {
        FieldRecord record = FieldRecord.reconstructStt(
                100L, 501L, 42L, "원문", FieldRecord.STT_STATUS_DONE,
                "STT:blank", Instant.now()
        );
        ReflectionTestUtils.setField(record, "id", 909L);
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(909L, 100L))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.update(7L, 42L, 909L, new FieldRecordUpdateRequest(
                null, "\t  \n", null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_PAYLOAD_INVALID);
    }

    @Test
    void TEXT_2001자_PATCH는_400이다() {
        FieldRecord record = textRecord(910L, 501L, 42L, "old");
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(910L, 100L))
                .thenReturn(Optional.of(record));
        String tooLong = "가".repeat(2001);

        assertThatThrownBy(() -> service.update(7L, 42L, 910L, new FieldRecordUpdateRequest(
                null, tooLong, null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_PAYLOAD_INVALID);
    }

    @Test
    void TEXT는_trim_후_저장한다() {
        FieldRecord record = textRecord(911L, 501L, 42L, "old");
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(911L, 100L))
                .thenReturn(Optional.of(record));
        when(fieldRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.update(7L, 42L, 911L, new FieldRecordUpdateRequest(
                null, "  교정문  ", null
        ));

        assertThat(response.textContent()).isEqualTo("교정문");
    }

    @Test
    void textContent_null이면_다른_필드만_수정한다() {
        FieldRecord record = textRecord(912L, 501L, 42L, "old");
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(912L, 100L))
                .thenReturn(Optional.of(record));
        when(checklistItemRepository.findOwnedItem(502L, 100L, 42L))
                .thenReturn(Optional.of(mock(ChecklistItem.class)));
        when(fieldRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.update(7L, 42L, 912L, new FieldRecordUpdateRequest(
                502L, null, null
        ));

        assertThat(response.checklistItemId()).isEqualTo(502L);
        assertThat(response.textContent()).isEqualTo("old");
    }

    @Test
    void 모든_수정_필드가_null이면_400이다() {
        FieldRecord record = textRecord(913L, 501L, 42L, "old");
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(913L, 100L))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.update(7L, 42L, 913L, new FieldRecordUpdateRequest(
                null, null, null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_UPDATE_EMPTY);
    }

    @Test
    void STT_FAILED는_수정_불가_삭제는_가능한다() {
        FieldRecord record = FieldRecord.reconstructStt(
                100L, 501L, 42L, null, FieldRecord.STT_STATUS_FAILED,
                "STT:failed", Instant.now()
        );
        ReflectionTestUtils.setField(record, "id", 914L);
        when(fieldRecordRepository.findByIdAndSessionIdForUpdate(914L, 100L))
                .thenReturn(Optional.of(record));
        when(fieldRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.update(7L, 42L, 914L, new FieldRecordUpdateRequest(
                null, "교정", null
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_STT_UPDATE_NOT_ALLOWED);

        assertThat(FieldRecordService.computePermissions(record, 42L, true))
                .isEqualTo(new FieldRecordService.Permissions(false, true));

        var deleted = service.delete(7L, 42L, 914L);
        assertThat(deleted.responseCode()).isEqualTo(FieldRecordResponseCode.FIELD_RECORD_DELETE_SUCCESS);
    }

    private static FieldRecord textRecord(Long id, Long itemId, Long authorId, String text) {
        FieldRecord record = FieldRecord.createText(
                100L, itemId, authorId, text, "11111111-1111-1111-1111-111111111111",
                "fp", Instant.now()
        );
        ReflectionTestUtils.setField(record, "id", id);
        return record;
    }
}
