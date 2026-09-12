package com.ssafy.ssabangpalbang.member.repository;

import com.ssafy.ssabangpalbang.member.domain.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerIdAndFollowingId(
            Long followerId,
            Long followingId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            DELETE FROM Follow f
            WHERE f.followerId = :followerId
              AND f.followingId = :followingId
            """)
    int deleteRelation(
            @Param("followerId") Long followerId,
            @Param("followingId") Long followingId
    );

    @Query(
            value = """
                    SELECT f.id AS followId,
                           m.id AS memberId,
                           m.nickname AS nickname,
                           m.profile_image_url AS profileImageUrl,
                           m.selected_character_id AS selectedCharacterId,
                           m.age_group AS ageGroup,
                           m.age_group_public_agreed AS ageGroupPublicAgreed,
                           (
                               SELECT COUNT(*)
                               FROM study s
                               WHERE s.deleted_at IS NULL
                                 AND s.status <> 'CANCELED'
                                 AND (
                                     s.leader_id = m.id
                                     OR EXISTS (
                                         SELECT 1
                                         FROM study_member sm
                                         WHERE sm.study_id = s.id
                                           AND sm.member_id = m.id
                                           AND (
                                               sm.status = 'ACTIVE'
                                               OR s.status = 'COMPLETED'
                                           )
                                     )
                                 )
                           ) AS participatingStudyCount,
                           f.created_at AS followedAt
                    FROM follow f
                    JOIN member m ON m.id = f.following_id
                    WHERE f.follower_id = :followerId
                      AND (:cursor IS NULL OR f.id < :cursor)
                      AND m.status = 'ACTIVE'
                      AND m.deleted_at IS NULL
                    ORDER BY f.id DESC
                    """,
            nativeQuery = true
    )
    List<FollowingMemberRow> findFollowingPage(
            @Param("followerId") Long followerId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

}
