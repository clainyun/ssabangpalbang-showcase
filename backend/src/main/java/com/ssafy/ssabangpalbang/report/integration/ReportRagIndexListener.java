package com.ssafy.ssabangpalbang.report.integration;

import com.ssafy.ssabangpalbang.report.client.ReportRagIndexClient;
import com.ssafy.ssabangpalbang.report.client.ReportRagIndexResponse;
import com.ssafy.ssabangpalbang.report.config.ReportRagIndexAsyncConfig;
import com.ssafy.ssabangpalbang.report.config.ReportRagIndexProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportRagIndexListener {

    private final ReportRagIndexClient client;
    private final ReportRagIndexProperties properties;

    @Async(ReportRagIndexAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportCompleted(ReportCompletedEvent event) {
        if (!properties.enabled()) {
            return;
        }

        try {
            ReportRagIndexResponse response = client.index(event.reportId());
            if (response.indexed()) {
                log.debug(
                        "리포트 RAG 색인을 완료했습니다. reportId={}, chunkCount={}, deletedCount={}",
                        event.reportId(),
                        response.chunkCount(),
                        response.deletedCount()
                );
            } else {
                log.info(
                        "리포트 RAG 색인을 건너뜁니다. reportId={}, skipReason={}",
                        event.reportId(),
                        response.skipReason()
                );
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "리포트 RAG 색인에 실패했습니다. reportId={}",
                    event.reportId(),
                    exception
            );
        }
    }
}
