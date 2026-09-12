package com.ssafy.ssabangpalbang.fieldvisit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FieldVisitFinishRequestBody(
        Boolean finishConfirmed,
        @NotBlank
        @Size(max = 100)
        String clientRequestId
) {
}
