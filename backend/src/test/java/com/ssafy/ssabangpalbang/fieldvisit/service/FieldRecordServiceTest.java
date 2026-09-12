package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecord;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldRecordResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldRecordCreateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FieldRecordServiceTest {

    private FieldVisitAccessService accessService;
    private ChecklistItemRepository checklistItemRepository;
    private FieldRecordRepository fieldRecordRepository;
    private FieldRecordWriter fieldRecordWriter;
    private FieldPhotoValidator fieldPhotoValidator;
    private FieldRecordCountRepository fieldRecordCountRepository;
    private MediaAccessUrlProvider mediaAccessUrlProvider;
    private MemberRepository memberRepository;
    private FieldRecordService service;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger fieldRecordLogger;

    @BeforeEach
    void setUp() {
        fieldRecordLogger = (Logger) LoggerFactory.getLogger(FieldRecordService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        fieldRecordLogger.addAppender(logAppender);

        accessService = mock(FieldVisitAccessService.class);
        checklistItemRepository = mock(ChecklistItemRepository.class);
        fieldRecordRepository = mock(FieldRecordRepository.class);
        fieldRecordWriter = mock(FieldRecordWriter.class);
        fieldPhotoValidator = mock(FieldPhotoValidator.class);
        fieldRecordCountRepository = mock(FieldRecordCountRepository.class);
        mediaAccessUrlProvider = mock(MediaAccessUrlProvider.class);
        memberRepository = mock(MemberRepository.class);
        when(mediaAccessUrlProvider.issueAll(any())).thenReturn(Map.of());
        when(fieldRecordCountRepository.countByAuthorAndItemIds(any(), anyCollection()))
                .thenReturn(Map.of(501L, 1));
        when(memberRepository.findAllById(any())).thenReturn(java.util.List.of());

        service = new FieldRecordService(
                accessService,
                checklistItemRepository,
                fieldRecordRepository,
                fieldRecordWriter,
                fieldPhotoValidator,
                fieldRecordCountRepository,
                mock(FileMetaRepository.class),
                mediaAccessUrlProvider,
                memberRepository
        );

        FieldSession session = mock(FieldSession.class);
        when(session.getId()).thenReturn(100L);
        when(accessService.requireWritableParticipation(any(), any(), any(), any()))
                .thenReturn(new FieldVisitParticipation(session, mock(FieldParticipant.class)));
        when(checklistItemRepository.findOwnedItem(501L, 100L, 42L))
                .thenReturn(Optional.of(mock(ChecklistItem.class)));
    }

    @AfterEach
    void tearDown() {
        if (fieldRecordLogger != null && logAppender != null) {
            fieldRecordLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    private String capturedLogs() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void TEXT_저장에_성공한다() {
        when(fieldRecordWriter.saveAndFlush(any())).thenAnswer(inv -> {
            FieldRecord draft = inv.getArgument(0);
            FieldRecord saved = FieldRecord.createText(
                    draft.getSessionId(),
                    draft.getChecklistItemId(),
                    draft.getAuthorId(),
                    draft.getTextContent(),
                    draft.getClientRequestId(),
                    draft.getRequestFingerprint(),
                    draft.getCreatedAt()
            );
            ReflectionTestUtils.setField(saved, "id", 880L);
            return saved;
        });

        var result = service.create(7L, 42L, new FieldRecordCreateRequest(
                501L,
                "TEXT",
                "메모입니다",
                null,
                "11111111-1111-1111-1111-111111111111"
        ));

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.responseCode()).isEqualTo(FieldRecordResponseCode.FIELD_RECORD_CREATE_SUCCESS);
        assertThat(result.body().record().textContent()).isEqualTo("메모입니다");
        assertThat(result.body().record().sourceId()).isEqualTo(880L);
        assertThat(result.body().record().canEdit()).isTrue();
        assertThat(result.body().record().canDelete()).isTrue();
        assertThat(result.body().itemRecordCount()).isEqualTo(1);
    }

    @Test
    void STT_POST는_거절한다() {
        assertThatThrownBy(() -> service.create(7L, 42L, new FieldRecordCreateRequest(
                501L,
                "STT",
                "x",
                null,
                "11111111-1111-1111-1111-111111111111"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_SOURCE_TYPE_NOT_ALLOWED);
    }

    @Test
    void 동일_clientRequestId_동일_fingerprint면_200을_반환한다() {
        FieldRecord existing = FieldRecord.createText(
                100L,
                501L,
                42L,
                "메모입니다",
                "11111111-1111-1111-1111-111111111111",
                FieldRecordFingerprint.ofText(501L, "메모입니다"),
                Instant.now()
        );
        when(fieldRecordWriter.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique"));
        when(fieldRecordRepository.findByClientRequestId(
                "11111111-1111-1111-1111-111111111111"
        )).thenReturn(Optional.of(existing));

        var result = service.create(7L, 42L, new FieldRecordCreateRequest(
                501L,
                "TEXT",
                "메모입니다",
                null,
                "11111111-1111-1111-1111-111111111111"
        ));

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.responseCode())
                .isEqualTo(FieldRecordResponseCode.FIELD_RECORD_ALREADY_CREATED);
        assertThat(capturedLogs()).doesNotContain("FIELD_RECORD_IDEMPOTENCY_MISMATCH");
    }

    @Test
    void 동일_clientRequestId_다른_payload면_충돌한다() {
        FieldRecord existing = FieldRecord.createText(
                100L,
                501L,
                42L,
                "다른내용",
                "11111111-1111-1111-1111-111111111111",
                FieldRecordFingerprint.ofText(501L, "다른내용"),
                Instant.now()
        );
        when(fieldRecordWriter.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique"));
        when(fieldRecordRepository.findByClientRequestId(
                "11111111-1111-1111-1111-111111111111"
        )).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(7L, 42L, new FieldRecordCreateRequest(
                501L,
                "TEXT",
                "메모입니다",
                null,
                "11111111-1111-1111-1111-111111111111"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_IDEMPOTENCY_KEY_REUSED);

        String logs = capturedLogs();
        assertThat(logs).contains("event=FIELD_RECORD_IDEMPOTENCY_MISMATCH");
        assertThat(logs).contains("mismatchReasons=");
        assertThat(logs).contains("fingerprint");
        assertThat(logs).doesNotContain("다른내용");
        assertThat(logs).doesNotContain("메모입니다");
    }

    @Test
    void 다른_회원_동일_clientRequestId면_충돌한다() {
        FieldRecord existing = FieldRecord.createText(
                100L,
                501L,
                99L,
                "메모입니다",
                "11111111-1111-1111-1111-111111111111",
                FieldRecordFingerprint.ofText(501L, "메모입니다"),
                Instant.now()
        );
        when(fieldRecordWriter.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique"));
        when(fieldRecordRepository.findByClientRequestId(
                "11111111-1111-1111-1111-111111111111"
        )).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(7L, 42L, new FieldRecordCreateRequest(
                501L,
                "TEXT",
                "메모입니다",
                null,
                "11111111-1111-1111-1111-111111111111"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void 다른_세션_동일_clientRequestId면_충돌한다() {
        FieldRecord existing = FieldRecord.createText(
                999L,
                501L,
                42L,
                "메모입니다",
                "11111111-1111-1111-1111-111111111111",
                FieldRecordFingerprint.ofText(501L, "메모입니다"),
                Instant.now()
        );
        when(fieldRecordWriter.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique"));
        when(fieldRecordRepository.findByClientRequestId(
                "11111111-1111-1111-1111-111111111111"
        )).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(7L, 42L, new FieldRecordCreateRequest(
                501L,
                "TEXT",
                "메모입니다",
                null,
                "11111111-1111-1111-1111-111111111111"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void 다른_checklistItemId_동일_clientRequestId면_충돌한다() {
        FieldRecord existing = FieldRecord.createText(
                100L,
                501L,
                42L,
                "메모입니다",
                "11111111-1111-1111-1111-111111111111",
                FieldRecordFingerprint.ofText(501L, "메모입니다"),
                Instant.now()
        );
        when(checklistItemRepository.findOwnedItem(502L, 100L, 42L))
                .thenReturn(Optional.of(mock(ChecklistItem.class)));
        when(fieldRecordWriter.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique"));
        when(fieldRecordRepository.findByClientRequestId(
                "11111111-1111-1111-1111-111111111111"
        )).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(7L, 42L, new FieldRecordCreateRequest(
                502L,
                "TEXT",
                "메모입니다",
                null,
                "11111111-1111-1111-1111-111111111111"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_IDEMPOTENCY_KEY_REUSED);

        String logs = capturedLogs();
        assertThat(logs).contains("event=FIELD_RECORD_IDEMPOTENCY_MISMATCH");
        assertThat(logs).contains("checklistItemId");
        assertThat(logs).doesNotContain("메모입니다");
    }

    @Test
    void 다른_sourceType_동일_clientRequestId면_충돌한다() {
        FieldRecord existing = FieldRecord.createText(
                100L,
                501L,
                42L,
                "메모입니다",
                "11111111-1111-1111-1111-111111111111",
                FieldRecordFingerprint.ofText(501L, "메모입니다"),
                Instant.now()
        );
        FileMeta photo = mock(FileMeta.class);
        when(photo.getId()).thenReturn(89L);
        when(fieldPhotoValidator.requireValidFieldPhoto(eq(89L), eq(42L), eq(7L)))
                .thenReturn(photo);
        when(fieldRecordWriter.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique"));
        when(fieldRecordRepository.findByClientRequestId(
                "11111111-1111-1111-1111-111111111111"
        )).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(7L, 42L, new FieldRecordCreateRequest(
                501L,
                "PHOTO",
                null,
                89L,
                "11111111-1111-1111-1111-111111111111"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_IDEMPOTENCY_KEY_REUSED);

        String logs = capturedLogs();
        assertThat(logs).contains("event=FIELD_RECORD_IDEMPOTENCY_MISMATCH");
        assertThat(logs).contains("sourceType");
        assertThat(logs).doesNotContain("메모입니다");
    }
}
