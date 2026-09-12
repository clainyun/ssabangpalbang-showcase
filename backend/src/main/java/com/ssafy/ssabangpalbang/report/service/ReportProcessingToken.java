package com.ssafy.ssabangpalbang.report.service;

final class ReportProcessingToken {

    private final String rawValue;
    private final String sha256Hash;

    ReportProcessingToken(String rawValue, String sha256Hash) {
        this.rawValue = rawValue;
        this.sha256Hash = sha256Hash;
    }

    String rawValue() {
        return rawValue;
    }

    String sha256Hash() {
        return sha256Hash;
    }

    @Override
    public String toString() {
        return "ReportProcessingToken[REDACTED]";
    }
}
