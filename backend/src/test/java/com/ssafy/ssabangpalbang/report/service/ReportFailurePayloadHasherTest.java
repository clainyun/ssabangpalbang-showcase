package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.report.dto.request.ReportFailRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportWorkerErrorCode;
import com.ssafy.ssabangpalbang.report.dto.request.ReportWorkerProgressStage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportFailurePayloadHasherTest {

    private final ReportFailurePayloadHasher hasher =
            new ReportFailurePayloadHasher();

    @Test
    void 처리권_메타데이터가_달라도_실패_payload가_같으면_동일한_해시다() {
        String first = hasher.hash(request(
                "token-1",
                1,
                ReportWorkerProgressStage.NORMALIZATION,
                ReportWorkerErrorCode.NORMALIZATION_FAILED,
                false
        ));
        String second = hasher.hash(request(
                "token-2",
                2,
                ReportWorkerProgressStage.NORMALIZATION,
                ReportWorkerErrorCode.NORMALIZATION_FAILED,
                false
        ));

        assertThat(first)
                .hasSize(64)
                .isEqualTo(second);
        assertThat(hasher.matches(first, second)).isTrue();
    }

    @Test
    void 실패_단계가_달라지면_다른_해시다() {
        assertDifferent(
                request(
                        "token",
                        1,
                        ReportWorkerProgressStage.NORMALIZATION,
                        ReportWorkerErrorCode.NORMALIZATION_FAILED,
                        false
                ),
                request(
                        "token",
                        1,
                        ReportWorkerProgressStage.REPORT_GENERATION,
                        ReportWorkerErrorCode.NORMALIZATION_FAILED,
                        false
                )
        );
    }

    @Test
    void 실패_코드가_달라지면_안전_문구를_포함한_다른_해시다() {
        assertDifferent(
                request(
                        "token",
                        1,
                        ReportWorkerProgressStage.NORMALIZATION,
                        ReportWorkerErrorCode.NORMALIZATION_FAILED,
                        false
                ),
                request(
                        "token",
                        1,
                        ReportWorkerProgressStage.NORMALIZATION,
                        ReportWorkerErrorCode.SOURCE_LOAD_FAILED,
                        false
                )
        );
    }

    @Test
    void retryable이_달라지면_다른_해시다() {
        assertDifferent(
                request(
                        "token",
                        1,
                        ReportWorkerProgressStage.NORMALIZATION,
                        ReportWorkerErrorCode.NORMALIZATION_FAILED,
                        false
                ),
                request(
                        "token",
                        1,
                        ReportWorkerProgressStage.NORMALIZATION,
                        ReportWorkerErrorCode.NORMALIZATION_FAILED,
                        true
                )
        );
    }

    @Test
    void null_해시는_일치하지_않는다() {
        String hash = hasher.hash(request(
                "token",
                1,
                ReportWorkerProgressStage.NORMALIZATION,
                ReportWorkerErrorCode.NORMALIZATION_FAILED,
                false
        ));

        assertThat(hasher.matches(hash, null)).isFalse();
        assertThat(hasher.matches(null, hash)).isFalse();
    }

    private void assertDifferent(
            ReportFailRequest first,
            ReportFailRequest second
    ) {
        String firstHash = hasher.hash(first);
        String secondHash = hasher.hash(second);

        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(hasher.matches(firstHash, secondHash)).isFalse();
    }

    private ReportFailRequest request(
            String token,
            int attempt,
            ReportWorkerProgressStage stage,
            ReportWorkerErrorCode errorCode,
            boolean retryable
    ) {
        return new ReportFailRequest(
                token,
                attempt,
                stage,
                errorCode,
                errorCode.safeMessage(),
                retryable
        );
    }
}
