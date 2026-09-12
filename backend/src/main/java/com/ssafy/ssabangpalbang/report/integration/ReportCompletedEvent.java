package com.ssafy.ssabangpalbang.report.integration;

import java.util.Objects;

/**
 * Published only after a report's first successful completion has been
 * prepared inside the enclosing transaction.
 */
public record ReportCompletedEvent(Long reportId, Long studyId) {

    public ReportCompletedEvent {
        Objects.requireNonNull(reportId);
        Objects.requireNonNull(studyId);
    }
}
