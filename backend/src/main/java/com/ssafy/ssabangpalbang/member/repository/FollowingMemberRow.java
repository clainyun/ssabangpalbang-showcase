package com.ssafy.ssabangpalbang.member.repository;

import java.time.Instant;

public interface FollowingMemberRow {

    Long getFollowId();

    Long getMemberId();

    String getNickname();

    String getProfileImageUrl();

    String getSelectedCharacterId();

    String getAgeGroup();

    Boolean getAgeGroupPublicAgreed();

    Long getParticipatingStudyCount();

    Instant getFollowedAt();
}
