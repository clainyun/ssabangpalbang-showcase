package com.ssafy.ssabangpalbang.fieldvisit.stt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SttRetryRequest(
        @NotBlank(message = "clientRequestId는 필수입니다.")
        @Schema(
                description = "재처리 요청의 중복 방지용 UUID",
                example = "55591972-492e-4c29-81bd-eb203f37be49"
        )
        String clientRequestId
) {
}
