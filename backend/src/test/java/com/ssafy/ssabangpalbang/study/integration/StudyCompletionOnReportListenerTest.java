package com.ssafy.ssabangpalbang.study.integration;

import com.ssafy.ssabangpalbang.report.integration.ReportCompletedEvent;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyCompletionOnReportListenerTest {

    private static final long REPORT_ID = 48L;
    private static final long STUDY_ID = 7L;

    @Mock
    private StudyRepository studyRepository;

    @Test
    void 완료_리포트의_스터디를_잠그고_COMPLETED로_전환한다() {
        Study study = inProgressStudy();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        listener().completeBeforeCommit(event());

        assertThat(study.getStatus()).isEqualTo(StudyStatus.COMPLETED);
        verify(studyRepository)
                .findForUpdateByIdAndDeletedAtIsNull(STUDY_ID);
    }

    @Test
    void 이미_완료된_스터디의_중복_이벤트는_멱등_처리한다() {
        Study study = inProgressStudy();
        study.markCompleted();
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        listener().completeBeforeCommit(event());

        assertThat(study.getStatus()).isEqualTo(StudyStatus.COMPLETED);
    }

    @Test
    void 연결된_스터디가_없으면_완료_트랜잭션을_실패시킨다() {
        when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> listener().completeBeforeCommit(event()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 완료_처리는_원본_트랜잭션의_커밋_전에_실행된다()
            throws NoSuchMethodException {
        Method method = StudyCompletionOnReportListener.class
                .getDeclaredMethod(
                        "completeBeforeCommit",
                        ReportCompletedEvent.class
                );

        TransactionalEventListener annotation = method.getAnnotation(
                TransactionalEventListener.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
    }

    private StudyCompletionOnReportListener listener() {
        return new StudyCompletionOnReportListener(studyRepository);
    }

    private ReportCompletedEvent event() {
        return new ReportCompletedEvent(REPORT_ID, STUDY_ID);
    }

    private Study inProgressStudy() {
        Study study = Study.create(
                15L,
                3L,
                "완료 전이 스터디",
                "소개",
                "목표",
                5,
                StudyPurpose.RESIDENCE
        );
        study.closeRecruitment(Instant.parse("2026-08-03T01:00:00Z"));
        study.markInProgress();
        return study;
    }
}
