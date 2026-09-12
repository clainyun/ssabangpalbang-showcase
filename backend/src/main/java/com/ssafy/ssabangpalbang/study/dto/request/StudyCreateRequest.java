package com.ssafy.ssabangpalbang.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudyCreateRequest(
        @NotNull
        Long apartmentId,

        @NotBlank
        @Size(min = 1, max = 200)
        String title,

        String intro,

        @NotBlank
        String goal,

        Integer capacity,

        String purpose
) {
}
