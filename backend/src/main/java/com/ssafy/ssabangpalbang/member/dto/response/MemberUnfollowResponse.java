package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.domain.Member;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record MemberUnfollowResponse(
        Long memberId,
        String nickname,
        String selectedCharacterId,
        boolean isFollowing,
        boolean canSendMessage,
        Long followingCount,
        OffsetDateTime unfollowedAt
) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static MemberUnfollowResponse of(
            Member targetMember,
            long followingCount,
            Instant unfollowedAt
    ) {
        return new MemberUnfollowResponse(
                targetMember.getId(),
                targetMember.getNickname(),
                targetMember.getSelectedCharacterId(),
                false,
                false,
                followingCount,
                unfollowedAt.atZone(SEOUL_ZONE_ID).toOffsetDateTime()
        );
    }
}
