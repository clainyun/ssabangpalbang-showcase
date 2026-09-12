package com.ssafy.ssabangpalbang.study.repository;

import com.ssafy.ssabangpalbang.study.domain.Study;
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

public interface StudyRepository extends JpaRepository<Study, Long> {
    String MEMBER_STUDY_FROM = """
            FROM study s
            JOIN apartment a ON a.id = s.apartment_id
            LEFT JOIN schedule sch ON sch.study_id = s.id
            LEFT JOIN study_member sm
              ON sm.study_id = s.id
             AND sm.member_id = :memberId
             AND sm.status = 'ACTIVE'
            WHERE s.deleted_at IS NULL
              AND s.status <> 'CANCELED'
              AND (
                  :studyStatus = 'ALL'
                  OR (
                      :studyStatus = 'ACTIVE'
                      AND s.status IN (
                          'RECRUITING', 'CLOSED', 'IN_PROGRESS'
                      )
                  )
                  OR (
                      :studyStatus = 'IN_PROGRESS'
                      AND s.status = 'IN_PROGRESS'
                  )
                  OR (
                      :studyStatus = 'COMPLETED'
                      AND s.status = 'COMPLETED'
                  )
              )
              AND (s.leader_id = :memberId OR sm.id IS NOT NULL)
            """;

    String PUBLIC_PROFILE_STUDY_FROM = """
            FROM study s
            JOIN apartment a ON a.id = s.apartment_id
            WHERE s.deleted_at IS NULL
              AND s.status <> 'CANCELED'
              AND (
                  :studyStatus = 'ALL'
                  OR (
                      :studyStatus = 'ACTIVE'
                      AND s.status IN (
                          'RECRUITING', 'CLOSED', 'IN_PROGRESS'
                      )
                  )
                  OR (
                      :studyStatus = 'COMPLETED'
                      AND s.status = 'COMPLETED'
                  )
              )
              AND (
                  s.leader_id = :memberId
                  OR EXISTS (
                      SELECT 1
                      FROM study_member sm
                      WHERE sm.study_id = s.id
                        AND sm.member_id = :memberId
                        AND (
                            sm.status = 'ACTIVE'
                            OR s.status = 'COMPLETED'
                        )
                  )
              )
            """;

    String RECRUITING_FROM = """
            FROM study s
            JOIN LATERAL (
                SELECT count(*) AS cnt
                FROM study_member sm
                WHERE sm.study_id = s.id AND sm.status = 'ACTIVE'
            ) mc ON TRUE
            LEFT JOIN schedule sch
                ON sch.study_id = s.id AND sch.status = 'SCHEDULED'
            WHERE s.deleted_at IS NULL
              AND s.status = 'RECRUITING'
              AND mc.cnt < s.capacity
              AND (sch.id IS NULL OR sch.start_at > now())
            """;
    String RECRUITING_SELECT = """
            SELECT s.id AS studyId, s.title AS title, s.intro AS intro,
                   s.goal AS goal, s.purpose AS purpose, s.capacity AS capacity,
                   s.leader_id AS leaderId, s.created_at AS createdAt,
                   mc.cnt AS currentMemberCount,
                   (s.capacity - mc.cnt) AS remainingCapacity,
                   sch.id AS scheduleId, sch.start_at AS startAt,
                   sch.end_at AS endAt, sch.meeting_place AS meetingPlace
            """ + RECRUITING_FROM + " AND s.apartment_id = :apartmentId";
    String RECRUITING_COUNT =
            "SELECT count(*) " + RECRUITING_FROM
                    + " AND s.apartment_id = :apartmentId";

    Optional<Study> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            SELECT s.id
            FROM Study s
            WHERE s.leaderId = :memberId
              AND s.deletedAt IS NULL
              AND s.status IN (
                  com.ssafy.ssabangpalbang.study.domain.StudyStatus.RECRUITING,
                  com.ssafy.ssabangpalbang.study.domain.StudyStatus.CLOSED,
                  com.ssafy.ssabangpalbang.study.domain.StudyStatus.IN_PROGRESS
              )
            ORDER BY s.id ASC
            """)
    List<Long> findBlockingLeaderStudyIds(
            @Param("memberId") Long memberId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Study s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Study> findForUpdateByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query(
            value = """
                    SELECT *
                    FROM study
                    WHERE id = :id
                      AND deleted_at IS NULL
                    FOR NO KEY UPDATE
                    """,
            nativeQuery = true
    )
    Optional<Study> findForNoKeyUpdateByIdAndDeletedAtIsNull(
            @Param("id") Long id
    );

    @Query(
            value = """
                    SELECT s.id AS studyId,
                           s.title AS title,
                           s.intro AS intro,
                           s.goal AS goal,
                           s.status AS status,
                           CASE WHEN s.leader_id = :memberId
                               THEN 'LEADER' ELSE sm.role
                           END AS role,
                           a.id AS apartmentId,
                           a.name AS apartmentName,
                           sch.id AS scheduleId,
                           sch.start_at AS startAt,
                           sch.meeting_place AS meetingPlace,
                           (
                               SELECT COUNT(*)
                               FROM chat_message cm
                               LEFT JOIN chat_read_status crs
                                 ON crs.study_id = cm.study_id
                                AND crs.member_id = :memberId
                               WHERE cm.study_id = s.id
                                 AND (
                                     cm.sender_id IS NULL
                                     OR cm.sender_id <> :memberId
                                 )
                                 AND (
                                     crs.last_read_at IS NULL
                                     OR cm.created_at > crs.last_read_at
                                 )
                           ) AS unreadChatCount,
                           (
                               SELECT COUNT(*)
                               FROM study_member review_target
                               JOIN member review_target_member
                                 ON review_target_member.id = review_target.member_id
                                AND review_target_member.status = 'ACTIVE'
                                AND review_target_member.deleted_at IS NULL
                               WHERE review_target.study_id = s.id
                                 AND review_target.status = 'ACTIVE'
                                 AND review_target.member_id <> :memberId
                                 AND NOT EXISTS (
                                     SELECT 1
                                     FROM member_review mr
                                     WHERE mr.study_id = s.id
                                       AND mr.reviewer_id = :memberId
                                       AND mr.reviewee_id = review_target.member_id
                                 )
                           ) AS pendingReviewCount,
                           EXISTS (
                               SELECT 1
                               FROM field_session fs
                               JOIN field_participant fp
                                 ON fp.session_id = fs.id
                                AND fp.member_id = :memberId
                                AND fp.status IN ('IN_PROGRESS', 'ENDED')
                               WHERE fs.study_id = s.id
                                 AND fs.status = 'IN_PROGRESS'
                           ) AS hasReturnableFieldVisit
                    """
                    + MEMBER_STUDY_FROM
                    + """
                     ORDER BY CASE WHEN s.status IN (
                                  'RECRUITING', 'CLOSED', 'IN_PROGRESS'
                              ) THEN 0 ELSE 1 END,
                              COALESCE(sm.joined_at, s.created_at) DESC,
                              s.id DESC
                    """,
            countQuery = "SELECT COUNT(*) " + MEMBER_STUDY_FROM,
            nativeQuery = true
    )
    Page<MemberStudyRow> findMemberStudies(
            @Param("memberId") Long memberId,
            @Param("studyStatus") String studyStatus,
            Pageable pageable
    );

    @Query(value = RECRUITING_SELECT
            + " ORDER BY sch.start_at ASC NULLS LAST, s.id ASC",
            countQuery = RECRUITING_COUNT, nativeQuery = true)
    Page<RecruitingStudyRow> findRecruitingOrderByScheduleAsc(
            @Param("apartmentId") Long apartmentId, Pageable pageable);

    @Query(value = RECRUITING_SELECT
            + " ORDER BY s.created_at DESC, s.id DESC",
            countQuery = RECRUITING_COUNT, nativeQuery = true)
    Page<RecruitingStudyRow> findRecruitingOrderByCreatedDesc(
            @Param("apartmentId") Long apartmentId, Pageable pageable);

    @Query(value = RECRUITING_SELECT
            + " ORDER BY (s.capacity - mc.cnt) DESC, s.id DESC",
            countQuery = RECRUITING_COUNT, nativeQuery = true)
    Page<RecruitingStudyRow> findRecruitingOrderByRemainingCapacityDesc(
            @Param("apartmentId") Long apartmentId, Pageable pageable);

    @Query(value = RECRUITING_COUNT, nativeQuery = true)
    long countRecruitingByApartmentId(
            @Param("apartmentId") Long apartmentId);

    @Query(value = "SELECT s.apartment_id AS apartmentId, count(*) AS count "
            + RECRUITING_FROM
            + " AND s.apartment_id IN (:apartmentIds)"
            + " GROUP BY s.apartment_id", nativeQuery = true)
    List<ApartmentStudyCountRow> countRecruitingByApartmentIds(
            @Param("apartmentIds") Collection<Long> apartmentIds);

    interface ApartmentStudyCountRow {
        Long getApartmentId();

        Long getCount();
    }

    @Query(
            value = """
                    SELECT s.id AS studyId,
                           s.title AS title,
                           s.status AS status,
                           CASE WHEN s.leader_id = :memberId
                               THEN 'LEADER'
                               ELSE COALESCE((
                                   SELECT sm.role
                                   FROM study_member sm
                                   WHERE sm.study_id = s.id
                                     AND sm.member_id = :memberId
                               ), 'MEMBER')
                           END AS role,
                           a.id AS apartmentId,
                           a.name AS apartmentName
                    """
                    + PUBLIC_PROFILE_STUDY_FROM
                    + """
                     ORDER BY CASE WHEN s.status IN (
                                  'RECRUITING', 'CLOSED', 'IN_PROGRESS'
                              ) THEN 0 ELSE 1 END,
                              s.updated_at DESC,
                              s.id DESC
                    """,
            countQuery = "SELECT COUNT(*) " + PUBLIC_PROFILE_STUDY_FROM,
            nativeQuery = true
    )
    Page<PublicProfileStudyRow> findPublicProfileStudies(
            @Param("memberId") Long memberId,
            @Param("studyStatus") String studyStatus,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM study s
                    WHERE s.deleted_at IS NULL
                      AND s.status <> 'CANCELED'
                      AND (
                          s.leader_id = :memberId
                          OR EXISTS (
                              SELECT 1
                              FROM study_member sm
                              WHERE sm.study_id = s.id
                                AND sm.member_id = :memberId
                                AND (
                                    sm.status = 'ACTIVE'
                                    OR s.status = 'COMPLETED'
                                )
                          )
                      )
                    """,
            nativeQuery = true
    )
    long countPublicProfileStudies(@Param("memberId") Long memberId);
}
