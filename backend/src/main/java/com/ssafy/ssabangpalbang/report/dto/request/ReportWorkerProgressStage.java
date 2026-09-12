package com.ssafy.ssabangpalbang.report.dto.request;

import com.ssafy.ssabangpalbang.report.domain.ReportProgressStage;

public enum ReportWorkerProgressStage {
    RECORD_COLLECTION,
    STT_VALIDATION,
    NORMALIZATION,
    REPORT_GENERATION,
    EVIDENCE_MAPPING,
    RESULT_SAVING;

    public ReportProgressStage toDomain() {
        return ReportProgressStage.valueOf(name());
    }
}
