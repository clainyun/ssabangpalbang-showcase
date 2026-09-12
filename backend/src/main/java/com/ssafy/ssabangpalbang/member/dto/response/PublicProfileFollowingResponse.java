package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.repository.PublicProfileFollowingRow;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record PublicProfileFollowingResponse(
        Long memberId,
        String nickname,
        String profileImageUrl,
        String selectedCharacterId,
        String ageGroup,
        String interestRegion,
        long participatingStudyCount,
        boolean isMe,
        boolean isFollowing,
        boolean canFollow,
        boolean canSendMessage,
        OffsetDateTime followedAt
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static PublicProfileFollowingResponse from(
            PublicProfileFollowingRow row,
            Long viewerId
    ) {
        boolean isMe = viewerId.equals(row.getMemberId());
        boolean isFollowing = !isMe
                && Boolean.TRUE.equals(row.getIsFollowing());
        return new PublicProfileFollowingResponse(
                row.getMemberId(),
                row.getNickname(),
                row.getProfileImageUrl(),
                row.getSelectedCharacterId(),
                Boolean.TRUE.equals(row.getAgeGroupPublicAgreed())
                        ? row.getAgeGroup()
                        : null,
                Boolean.TRUE.equals(row.getInterestRegionPublicAgreed())
                        ? row.getInterestRegion()
                        : null,
                row.getParticipatingStudyCount(),
                isMe,
                isFollowing,
                !isMe && !isFollowing,
                !isMe && isFollowing,
                row.getFollowedAt() == null
                        ? null
                        : row.getFollowedAt()
                                .atZone(SEOUL)
                                .toOffsetDateTime()
        );
    }
}
