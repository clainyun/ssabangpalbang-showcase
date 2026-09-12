package com.ssafy.ssabangpalbang.report.domain;

import java.util.Locale;

public enum ReportProgressStage {
    RECORD_COLLECTION(20, "임장 기록을 수집하고 있습니다."),
    STT_VALIDATION(40, "음성 기록의 변환 결과를 확인하고 있습니다."),
    NORMALIZATION(55, "참여자별 현장 기록을 정규화하고 있습니다."),
    REPORT_GENERATION(70, "참여자들의 현장 의견을 분석하고 있습니다."),
    EVIDENCE_MAPPING(90, "분석 결과와 현장 기록의 근거를 연결하고 있습니다."),
    RESULT_SAVING(95, "완성된 리포트 결과를 저장하고 있습니다."),
    COMPLETED(100, "AI 임장 리포트가 완성되었습니다.");

    private final int progressRate;
    private final String progressMessage;

    ReportProgressStage(int progressRate, String progressMessage) {
        this.progressRate = progressRate;
        this.progressMessage = progressMessage;
    }

    public int progressRate() {
        return progressRate;
    }

    public String progressMessage() {
        return progressMessage;
    }

    public static ReportProgressStage from(String value) {
        if (value == null || value.isBlank()) {
            return RECORD_COLLECTION;
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        ReportProgressStage legacy = switch (normalized) {
            case "COLLECT" -> RECORD_COLLECTION;
            case "STT" -> STT_VALIDATION;
            case "ANALYZE" -> REPORT_GENERATION;
            case "EVIDENCE" -> EVIDENCE_MAPPING;
            case "DONE" -> COMPLETED;
            default -> null;
        };
        if (legacy != null) {
            return legacy;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return RECORD_COLLECTION;
        }
    }
}
