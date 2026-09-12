package com.ssafy.ssabangpalbang.fieldvisit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FieldVisitCloseRequestBody(
        Boolean closeConfirmed,
        @NotBlank
        @Size(max = 100)
        String clientRequestId
) {
}
