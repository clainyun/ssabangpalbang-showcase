package com.ssafy.ssabangpalbang.member.repository;

import java.time.Instant;

public interface PublicProfileFollowingRow {

    Long getMemberId();

    String getNickname();

    String getProfileImageUrl();

    String getSelectedCharacterId();

    String getAgeGroup();

    Boolean getAgeGroupPublicAgreed();

    String getInterestRegion();

    Boolean getInterestRegionPublicAgreed();

    Long getParticipatingStudyCount();

    Boolean getIsFollowing();

    Instant getFollowedAt();
}
