package com.ssafy.ssabangpalbang.auth.repository;

import com.ssafy.ssabangpalbang.member.domain.MemberStatus;

public record SocialMemberSnapshot(
        Long memberId,
        String email,
        String nickname,
        String profileImageUrl,
        String selectedCharacterId,
        MemberStatus status
) {
}
