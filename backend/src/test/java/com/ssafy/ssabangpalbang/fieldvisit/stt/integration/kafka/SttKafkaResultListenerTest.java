package com.ssafy.ssabangpalbang.fieldvisit.stt.integration.kafka;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttFailureResult;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttProcessingResult;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttResultHandler;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttSuccessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SttKafkaResultListenerTest {

    private static final String STT_ID = "stt-123";
    private static final String TOPIC = "field-visit.stt.result.v1";
    private static final OffsetDateTime EVENT_TIME =
            OffsetDateTime.parse("2026-07-30T11:00:00+09:00");

    private final SttResultHandler resultHandler = mock();
    private SttKafkaResultListener listener;

    @BeforeEach
    void setUp() {
        listener = new SttKafkaResultListener(
                JsonMapper.builder()
                        .addModule(new JavaTimeModule())
                        .build(),
                resultHandler
        );
    }

    @Test
    void PROCESSING은_startedAt과_함께_처리_시작으로_전달한다() {
        SttProcessingResult expected =
                new SttProcessingResult(STT_ID, 1, EVENT_TIME);
        when(resultHandler.markProcessing(expected))
                .thenReturn(SttResultHandler.HandlingOutcome.APPLIED);

        receive("""
                {
                  "schemaVersion": 1,
                  "sttId": "stt-123",
                  "attemptNo": 1,
                  "status": "PROCESSING",
                  "startedAt": "2026-07-30T11:00:00+09:00"
                }
                """);

        verify(resultHandler).markProcessing(expected);
    }

    @Test
    void DONE은_변환문과_완료시각으로_전달한다() {
        SttSuccessResult expected = new SttSuccessResult(
                STT_ID,
                1,
                "현장 메모입니다.",
                EVENT_TIME
        );
        when(resultHandler.handleSuccess(expected))
                .thenReturn(SttResultHandler.HandlingOutcome.APPLIED);

        receive("""
                {
                  "schemaVersion": 1,
                  "sttId": "stt-123",
                  "attemptNo": 1,
                  "status": "DONE",
                  "textContent": "현장 메모입니다.",
                  "completedAt": "2026-07-30T11:00:00+09:00"
                }
                """);

        verify(resultHandler).handleSuccess(expected);
    }

    @Test
    void FAILED는_안전한_실패_필드로_전달한다() {
        SttFailureResult expected = new SttFailureResult(
                STT_ID,
                1,
                "AUDIO_DOWNLOAD_FAILED",
                "음성 원본을 불러오지 못했습니다.",
                true,
                EVENT_TIME
        );
        when(resultHandler.handleFailure(expected))
                .thenReturn(SttResultHandler.HandlingOutcome.APPLIED);

        receive("""
                {
                  "schemaVersion": 1,
                  "sttId": "stt-123",
                  "attemptNo": 1,
                  "status": "FAILED",
                  "failCode": "AUDIO_DOWNLOAD_FAILED",
                  "failReason": "음성 원본을 불러오지 못했습니다.",
                  "retryable": true,
                  "failedAt": "2026-07-30T11:00:00+09:00"
                }
                """);

        verify(resultHandler).handleFailure(expected);
    }

    @Test
    void Kafka_key와_sttId가_다르면_거부한다() {
        assertThatThrownBy(() -> listener.receive(
                """
                {
                  "schemaVersion": 1,
                  "sttId": "stt-123",
                  "attemptNo": 1,
                  "status": "PROCESSING",
                  "startedAt": "2026-07-30T11:00:00+09:00"
                }
                """,
                "stt-other",
                TOPIC,
                0,
                1L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kafka key");
    }

    @Test
    void 다른_schemaVersion과_알_수_없는_필드는_거부한다() {
        assertThatThrownBy(() -> receive("""
                {
                  "schemaVersion": 2,
                  "sttId": "stt-123",
                  "attemptNo": 1,
                  "status": "PROCESSING",
                  "startedAt": "2026-07-30T11:00:00+09:00"
                }
                """)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");

        assertThatThrownBy(() -> receive("""
                {
                  "schemaVersion": 1,
                  "sttId": "stt-123",
                  "attemptNo": 1,
                  "status": "PROCESSING",
                  "startedAt": "2026-07-30T11:00:00+09:00",
                  "unexpected": "value"
                }
                """)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("STT Kafka 결과 JSON 형식이 올바르지 않습니다.");
    }

    @Test
    void 상태별_필수_필드가_없으면_거부한다() {
        assertThatThrownBy(() -> receive("""
                {
                  "schemaVersion": 1,
                  "sttId": "stt-123",
                  "attemptNo": 1,
                  "status": "DONE",
                  "completedAt": "2026-07-30T11:00:00+09:00"
                }
                """)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("textContent");
    }

    private void receive(String payload) {
        listener.receive(payload, STT_ID, TOPIC, 0, 1L);
    }
}
