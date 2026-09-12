package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberPreference;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record MemberProfileUpdateResponse(
        Long memberId,
        String nickname,
        String ageGroup,
        boolean ageGroupPublicAgreed,
        String purpose,
        List<String> priorities,
        String selectedCharacterId,
        String maritalStatus,
        Boolean hasVehicle,
        Boolean hasChildren,
        OffsetDateTime updatedAt
) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static MemberProfileUpdateResponse from(
            Member member,
            MemberPreference preference,
            Instant updatedAt
    ) {
        return new MemberProfileUpdateResponse(
                member.getId(),
                member.getNickname(),
                member.getAgeGroup(),
                member.isAgeGroupPublicAgreed(),
                preference == null ? null : preference.getPurpose(),
                preference == null ? List.of() : preference.getPriorities(),
                member.getSelectedCharacterId(),
                preference == null ? null : preference.getMaritalStatus(),
                preference == null ? null : preference.getHasVehicle(),
                preference == null ? null : preference.getHasChildren(),
                OffsetDateTime.ofInstant(updatedAt, SEOUL_ZONE_ID)
        );
    }
}
