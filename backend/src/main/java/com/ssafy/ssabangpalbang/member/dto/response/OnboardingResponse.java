package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberPreference;

import java.util.List;

public record OnboardingResponse(
        Long memberId,
        String purpose,
        String maritalStatus,
        Boolean hasVehicle,
        Boolean hasChildren,
        List<String> priorities,
        String ageGroup,
        boolean ageGroupPublicAgreed,
        String selectedCharacterId,
        boolean onboardingCompleted
) {

    public static OnboardingResponse from(
            Member member,
            MemberPreference preference
    ) {
        return new OnboardingResponse(
                member.getId(),
                preference.getPurpose(),
                preference.getMaritalStatus(),
                preference.getHasVehicle(),
                preference.getHasChildren(),
                preference.getPriorities(),
                member.getAgeGroup(),
                member.isAgeGroupPublicAgreed(),
                member.getSelectedCharacterId(),
                true
        );
    }
}
