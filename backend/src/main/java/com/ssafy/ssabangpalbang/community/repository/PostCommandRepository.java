package com.ssafy.ssabangpalbang.community.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

public interface PostCommandRepository {

    OptionalLong incrementViewCountIfVisible(Long postId);

    Optional<Instant> insertLikeIfAbsent(Long postId, Long memberId);

    Optional<Instant> findLikedAt(Long postId, Long memberId);

    int deleteLike(Long postId, Long memberId);
}
