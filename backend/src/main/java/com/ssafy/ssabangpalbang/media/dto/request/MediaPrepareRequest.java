package com.ssafy.ssabangpalbang.media.dto.request;

import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MediaPrepareRequest(
        @NotNull FileUsage fileUsage,
        @Size(max = 255) String originalName,
        @NotBlank @Size(max = 100) String contentType,
        @NotNull @Positive Long sizeBytes,
        @Positive Long studyId
) {
}
