package com.ssafy.ssabangpalbang.member.repository;

import com.ssafy.ssabangpalbang.member.domain.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

    @Query(
            value = """
                    SELECT *
                    FROM member
                    WHERE id = :memberId
                    FOR NO KEY UPDATE
                    """,
            nativeQuery = true
    )
    Optional<Member> findByIdForNoKeyUpdate(
            @Param("memberId") Long memberId
    );

    @Query(
            value = """
                    SELECT *
                    FROM member
                    WHERE id IN (:memberIds)
                    ORDER BY id ASC
                    FOR NO KEY UPDATE
                    """,
            nativeQuery = true
    )
    List<Member> findAllByIdForNoKeyUpdate(
            @Param("memberIds") Collection<Long> memberIds
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT m FROM Member m WHERE m.id = :memberId")
    Optional<Member> findByIdForShare(@Param("memberId") Long memberId);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM study s
                    WHERE s.deleted_at IS NULL
                      AND (
                          s.leader_id = :memberId
                          OR EXISTS (
                              SELECT 1
                              FROM study_member sm
                              WHERE sm.study_id = s.id
                                AND sm.member_id = :memberId
                                AND sm.status = 'ACTIVE'
                          )
                      )
                    """,
            nativeQuery = true
    )
    long countAccessibleStudies(@Param("memberId") Long memberId);

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM report r
                    JOIN study s ON s.id = r.study_id
                    WHERE r.status = 'DONE'
                      AND s.deleted_at IS NULL
                      AND (
                          s.leader_id = :memberId
                          OR EXISTS (
                              SELECT 1
                              FROM study_member sm
                              WHERE sm.study_id = s.id
                                AND sm.member_id = :memberId
                                AND sm.status = 'ACTIVE'
                          )
                      )
                    """,
            nativeQuery = true
    )
    long countAccessibleDoneReports(@Param("memberId") Long memberId);

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM follow
                    WHERE follower_id = :memberId
                    """,
            nativeQuery = true
    )
    long countFollowing(@Param("memberId") Long memberId);

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM follow f
                    JOIN member m ON m.id = f.following_id
                    WHERE f.follower_id = :memberId
                      AND m.status = 'ACTIVE'
                      AND m.deleted_at IS NULL
                    """,
            nativeQuery = true
    )
    long countPublicProfileFollowings(
            @Param("memberId") Long memberId
    );

    @Query(
            value = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM follow
                        WHERE follower_id = :followerId
                          AND following_id = :followingId
                    )
                    """,
            nativeQuery = true
    )
    boolean existsFollow(
            @Param("followerId") Long followerId,
            @Param("followingId") Long followingId
    );

    @Query(
            value = """
                    SELECT m.id AS memberId,
                           m.nickname AS nickname,
                           m.profile_image_url AS profileImageUrl,
                           m.selected_character_id AS selectedCharacterId,
                           m.age_group AS ageGroup,
                           m.age_group_public_agreed
                               AS ageGroupPublicAgreed,
                           mp.interest_region AS interestRegion,
                           mp.interest_region_public_agreed
                               AS interestRegionPublicAgreed,
                           (
                               SELECT COUNT(*)
                               FROM study ps
                               WHERE ps.deleted_at IS NULL
                                 AND ps.status <> 'CANCELED'
                                 AND (
                                     ps.leader_id = m.id
                                     OR EXISTS (
                                         SELECT 1
                                         FROM study_member psm
                                         WHERE psm.study_id = ps.id
                                           AND psm.member_id = m.id
                                           AND (
                                               psm.status = 'ACTIVE'
                                               OR ps.status = 'COMPLETED'
                                           )
                                     )
                                 )
                           ) AS participatingStudyCount,
                           CASE WHEN vf.id IS NULL
                               THEN FALSE ELSE TRUE
                           END AS isFollowing,
                           f.created_at AS followedAt
                    FROM follow f
                    JOIN member m ON m.id = f.following_id
                    LEFT JOIN member_preference mp ON mp.member_id = m.id
                    LEFT JOIN follow vf
                      ON vf.follower_id = :viewerId
                     AND vf.following_id = m.id
                    WHERE f.follower_id = :profileMemberId
                      AND m.status = 'ACTIVE'
                      AND m.deleted_at IS NULL
                    ORDER BY f.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM follow f
                    JOIN member m ON m.id = f.following_id
                    WHERE f.follower_id = :profileMemberId
                      AND m.status = 'ACTIVE'
                      AND m.deleted_at IS NULL
                    """,
            nativeQuery = true
    )
    Page<PublicProfileFollowingRow> findPublicProfileFollowings(
            @Param("viewerId") Long viewerId,
            @Param("profileMemberId") Long profileMemberId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM member_preference
                        WHERE member_id = :memberId
                    )
                    """,
            nativeQuery = true
    )
    boolean existsPreferenceByMemberId(@Param("memberId") Long memberId);
}
