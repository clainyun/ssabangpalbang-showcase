package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.domain.Follow;
import com.ssafy.ssabangpalbang.member.domain.Member;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record MemberFollowResponse(
        Long memberId,
        String nickname,
        String selectedCharacterId,
        boolean isFollowing,
        boolean canSendMessage,
        Long followingCount,
        OffsetDateTime followedAt
) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static MemberFollowResponse of(
            Member targetMember,
            Follow follow,
            long followingCount
    ) {
        return new MemberFollowResponse(
                targetMember.getId(),
                targetMember.getNickname(),
                targetMember.getSelectedCharacterId(),
                true,
                true,
                followingCount,
                follow.getCreatedAt()
                        .atZone(SEOUL_ZONE_ID)
                        .toOffsetDateTime()
        );
    }
}
