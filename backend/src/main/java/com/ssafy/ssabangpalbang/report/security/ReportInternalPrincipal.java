package com.ssafy.ssabangpalbang.report.security;

public record ReportInternalPrincipal(String name) {

    private static final String WORKER_NAME = "REPORT_WORKER";

    public static ReportInternalPrincipal worker() {
        return new ReportInternalPrincipal(WORKER_NAME);
    }
}
