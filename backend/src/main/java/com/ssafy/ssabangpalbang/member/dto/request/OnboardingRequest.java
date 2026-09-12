package com.ssafy.ssabangpalbang.member.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "필수 온보딩 정보 저장 요청")
public record OnboardingRequest(
        @Schema(allowableValues = {"RESIDENCE", "INVESTMENT", "STUDY"})
        @Size(max = 50) String purpose,
        @Schema(allowableValues = {"SINGLE", "MARRIED"})
        @Size(max = 20) String maritalStatus,
        Boolean hasVehicle,
        Boolean hasChildren,
        @ArraySchema(
                maxItems = 4,
                schema = @Schema(allowableValues = {
                        "TRANSPORT",
                        "SAFETY",
                        "EDUCATION",
                        "COMMERCIAL",
                        "WALKABILITY",
                        "GREEN_SPACE",
                        "PARKING",
                        "NOISE"
                })
        )
        @Size(max = 4) List<@Size(max = 20) String> priorities,
        @Schema(allowableValues = {
                "TEENS",
                "TWENTIES",
                "THIRTIES",
                "FORTIES",
                "FIFTIES",
                "SIXTIES_PLUS"
        })
        @Size(max = 20) String ageGroup,
        Boolean ageGroupPublicAgreed,
        @Schema(allowableValues = {
                "PALBANG",
                "PALBANG_RABBIT",
                "PALBANG_DOG"
        })
        @Size(max = 20) String selectedCharacterId
) {
}
