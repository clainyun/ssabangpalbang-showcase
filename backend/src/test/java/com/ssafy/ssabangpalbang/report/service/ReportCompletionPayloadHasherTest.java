package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.report.dto.request.ReportCompleteRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportEvidenceResultRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCompletionPayloadHasherTest {

    private final ReportCompletionPayloadHasher hasher =
            new ReportCompletionPayloadHasher(
                    new ObjectMapper().findAndRegisterModules()
            );

    @Test
    void 처리권_메타데이터가_달라도_AI_결과가_같으면_동일한_해시를_만든다() {
        String first = hasher.hash(request("동일 결과", "token-1", 1));
        String second = hasher.hash(request("동일 결과", "token-2", 2));

        assertThat(first)
                .hasSize(64)
                .isEqualTo(second);
        assertThat(hasher.matches(first, second)).isTrue();
    }

    @Test
    void AI_결과가_달라지면_다른_해시를_만든다() {
        String first = hasher.hash(request("최초 결과", "token", 1));
        String second = hasher.hash(request("변경 결과", "token", 1));

        assertThat(first).isNotEqualTo(second);
        assertThat(hasher.matches(first, second)).isFalse();
        assertThat(hasher.matches(first, null)).isFalse();
    }

    private ReportCompleteRequest request(
            String summary,
            String token,
            int attempt
    ) {
        return new ReportCompleteRequest(
                token,
                attempt,
                new ReportGenerationResultRequest(
                        "테스트 리포트",
                        summary,
                        new ReportGenerationResultRequest.Metrics(
                                0,
                                0,
                                0.0,
                                0
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new ReportEvidenceResultRequest(List.of())
        );
    }
}
