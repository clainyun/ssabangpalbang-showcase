package com.ssafy.ssabangpalbang.report.integration;

import com.ssafy.ssabangpalbang.report.client.ReportRagIndexClient;
import com.ssafy.ssabangpalbang.report.client.ReportRagIndexException;
import com.ssafy.ssabangpalbang.report.client.ReportRagIndexResponse;
import com.ssafy.ssabangpalbang.report.config.ReportRagIndexProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportRagIndexListenerTest {

    private ReportRagIndexClient client;
    private ReportRagIndexProperties properties;
    private ReportRagIndexListener listener;

    @BeforeEach
    void setUp() {
        client = mock(ReportRagIndexClient.class);
        properties = new ReportRagIndexProperties();
        listener = new ReportRagIndexListener(client, properties);
    }

    @Test
    void 이벤트마다_클라이언트를_정확히_한_번_호출한다() {
        when(client.index(73L)).thenReturn(indexedResponse());

        listener.onReportCompleted(new ReportCompletedEvent(73L, 9L));

        verify(client).index(73L);
    }

    @Test
    void 색인_예외가_밖으로_나오지_않는다() {
        doThrow(new ReportRagIndexException("failed"))
                .when(client).index(73L);

        assertThatCode(() -> listener.onReportCompleted(
                new ReportCompletedEvent(73L, 9L)
        )).doesNotThrowAnyException();
    }

    @Test
    void 임의의_런타임_예외도_밖으로_나오지_않는다() {
        doThrow(new IllegalStateException("failed"))
                .when(client).index(73L);

        assertThatCode(() -> listener.onReportCompleted(
                new ReportCompletedEvent(73L, 9L)
        )).doesNotThrowAnyException();
    }

    @Test
    void 미색인_응답은_재호출하지_않는다() {
        when(client.index(73L)).thenReturn(new ReportRagIndexResponse(
                73L, null, false, 0, 0, "NOT_VISIBLE"
        ));

        assertThatCode(() -> listener.onReportCompleted(
                new ReportCompletedEvent(73L, 9L)
        )).doesNotThrowAnyException();

        verify(client).index(73L);
    }

    @Test
    void 비활성이면_클라이언트를_호출하지_않는다() {
        properties.setEnabled(false);

        listener.onReportCompleted(new ReportCompletedEvent(73L, 9L));

        verify(client, never()).index(73L);
    }

    private ReportRagIndexResponse indexedResponse() {
        return new ReportRagIndexResponse(73L, 44L, true, 1, 0, null);
    }
}
