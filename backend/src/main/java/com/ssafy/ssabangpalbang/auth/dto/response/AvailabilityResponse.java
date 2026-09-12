package com.ssafy.ssabangpalbang.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 이메일·닉네임 사용 가능 여부 확인 응답.
 * {@code available=true}이면 중복이 없어 사용 가능함을 의미한다.
 */
@Schema(name = "AuthAvailabilityResponse")
public record AvailabilityResponse(
        @Schema(description = "사용 가능 여부(true=미중복)", example = "true")
        boolean available
) {

    public static AvailabilityResponse of(boolean available) {
        return new AvailabilityResponse(available);
    }
}
