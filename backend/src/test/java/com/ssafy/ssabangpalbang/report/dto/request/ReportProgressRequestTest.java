package com.ssafy.ssabangpalbang.report.dto.request;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportProgressRequestTest {

    @Test
    void toString은_Token_원문을_노출하지_않는다() {
        String rawToken = "opaque-secret-token";
        ReportProgressRequest request = new ReportProgressRequest(
                rawToken,
                1,
                ReportWorkerProgressStage.NORMALIZATION
        );

        assertThat(request.toString())
                .doesNotContain(rawToken)
                .contains("processingToken=[REDACTED]");
    }
}
