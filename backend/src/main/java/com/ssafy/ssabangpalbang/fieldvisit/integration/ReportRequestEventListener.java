package com.ssafy.ssabangpalbang.fieldvisit.integration;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReportRequestEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(ReportRequestEventListener.class);

    private final ObjectProvider<ReportRequestPort> portProvider;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(ReportRequestedEvent event) {
        ReportRequestPort port = portProvider.getIfAvailable();
        if (port == null) {
            log.warn(
                    "리포트 요청 어댑터가 없어 이벤트를 기록만 합니다. studyId={}, sessionId={}",
                    event.studyId(),
                    event.sessionId()
            );
            return;
        }
        try {
            port.request(event);
        } catch (RuntimeException exception) {
            log.error(
                    "리포트 요청 발행 실패. studyId={}, sessionId={}",
                    event.studyId(),
                    event.sessionId(),
                    exception
            );
        }
    }
}
