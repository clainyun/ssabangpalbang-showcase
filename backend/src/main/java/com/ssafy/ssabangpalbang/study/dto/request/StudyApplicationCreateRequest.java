package com.ssafy.ssabangpalbang.study.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudyApplicationCreateRequest(
        @NotBlank @Size(max = 200) String intro,
        @NotBlank
        @Schema(
                example = "RESIDENCE",
                allowableValues = {"RESIDENCE", "INVESTMENT", "STUDY"}
        )
        String purpose
) {
    public StudyApplicationCreateRequest {
        intro = intro == null ? null : intro.trim();
        purpose = purpose == null ? null : purpose.trim();
    }
}
