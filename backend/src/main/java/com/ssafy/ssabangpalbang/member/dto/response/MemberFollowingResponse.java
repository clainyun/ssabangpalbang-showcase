package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.repository.FollowingMemberRow;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record MemberFollowingResponse(
        Long memberId,
        String nickname,
        String profileImageUrl,
        String selectedCharacterId,
        String ageGroup,
        long participatingStudyCount,
        boolean isFollowing,
        boolean canSendMessage,
        OffsetDateTime followedAt
) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static MemberFollowingResponse from(FollowingMemberRow row) {
        return new MemberFollowingResponse(
                row.getMemberId(),
                row.getNickname(),
                row.getProfileImageUrl(),
                row.getSelectedCharacterId(),
                Boolean.TRUE.equals(row.getAgeGroupPublicAgreed())
                        ? row.getAgeGroup()
                        : null,
                row.getParticipatingStudyCount(),
                true,
                true,
                row.getFollowedAt()
                        .atZone(SEOUL_ZONE_ID)
                        .toOffsetDateTime()
        );
    }
}
