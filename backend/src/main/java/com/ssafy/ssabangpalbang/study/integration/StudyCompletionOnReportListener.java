package com.ssafy.ssabangpalbang.study.integration;

import com.ssafy.ssabangpalbang.report.integration.ReportCompletedEvent;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 리포트 최초 완료와 스터디 완료 상태를 같은 트랜잭션으로 묶는다(BE-031).
 */
@Component
@RequiredArgsConstructor
public class StudyCompletionOnReportListener {

    private final StudyRepository studyRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void completeBeforeCommit(ReportCompletedEvent event) {
        Study study = studyRepository
                .findForUpdateByIdAndDeletedAtIsNull(event.studyId())
                .orElseThrow(() -> new IllegalStateException(
                        "완료 리포트의 스터디를 찾을 수 없습니다."
                ));
        study.markCompleted();
    }
}
