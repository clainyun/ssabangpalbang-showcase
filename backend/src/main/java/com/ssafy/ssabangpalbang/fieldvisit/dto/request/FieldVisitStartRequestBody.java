package com.ssafy.ssabangpalbang.fieldvisit.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "GPS 검증 후 임장 시작 요청")
public record FieldVisitStartRequestBody(
        @NotNull(message = "latitude는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상 90 이하이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 -90 이상 90 이하이어야 합니다.")
        @Schema(description = "현재 위도", example = "37.5133", minimum = "-90", maximum = "90")
        Double latitude,

        @NotNull(message = "longitude는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상 180 이하이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 -180 이상 180 이하이어야 합니다.")
        @Schema(description = "현재 경도", example = "127.0842", minimum = "-180", maximum = "180")
        Double longitude,

        @NotBlank(message = "clientRequestId는 필수입니다.")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "clientRequestId는 UUID 형식이어야 합니다."
        )
        @Size(max = 100, message = "clientRequestId는 100자 이하여야 합니다.")
        @Schema(description = "멱등 키", format = "uuid", maxLength = 100,
                example = "a1b2c3d4-1234-5678-90ab-cdef12345678")
        String clientRequestId,

        @Schema(
                description = "일정 없음/시작 시각 전 즉시 진행 확인 여부",
                defaultValue = "false"
        )
        Boolean scheduleOverrideConfirmed
) {
    public FieldVisitStartRequestBody(
            Double latitude,
            Double longitude,
            String clientRequestId
    ) {
        this(latitude, longitude, clientRequestId, false);
    }

    public boolean isScheduleOverrideConfirmed() {
        return Boolean.TRUE.equals(scheduleOverrideConfirmed);
    }
}
