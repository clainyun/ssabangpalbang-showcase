package com.ssafy.ssabangpalbang.report.repository;

import com.ssafy.ssabangpalbang.report.domain.Report;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByStudyId(Long studyId);

    Optional<Report> findByStudyId(Long studyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.studyId = :studyId")
    Optional<Report> findByStudyIdForUpdate(
            @Param("studyId") Long studyId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.id = :reportId")
    Optional<Report> findByIdForUpdate(
            @Param("reportId") Long reportId
    );

    @Query("""
            SELECT r
            FROM Report r
            JOIN Study s ON s.id = r.studyId
            WHERE r.apartmentId = :apartmentId
              AND r.status = com.ssafy.ssabangpalbang.report.domain.ReportStatus.DONE
              AND s.deletedAt IS NULL
              AND s.status <> com.ssafy.ssabangpalbang.study.domain.StudyStatus.CANCELED
            ORDER BY r.completedAt DESC NULLS LAST, r.id DESC
            """)
    List<Report> findDoneByApartmentId(
            @Param("apartmentId") Long apartmentId,
            Pageable pageable
    );

    String FAVORITE_REPORT_FROM = """
            FROM report_favorite rf
            JOIN report r ON r.id = rf.report_id
            JOIN study s ON s.id = r.study_id
            JOIN apartment a ON a.id = r.apartment_id
            WHERE rf.member_id = :memberId
              AND r.status = 'DONE'
              AND s.deleted_at IS NULL
              AND s.status <> 'CANCELED'
            """;

    String PUBLIC_PROFILE_REPORT_FROM = """
            FROM report r
            JOIN study s ON s.id = r.study_id
            JOIN apartment a ON a.id = r.apartment_id
            LEFT JOIN report_favorite rf
              ON rf.report_id = r.id
             AND rf.member_id = :viewerId
            WHERE r.status = 'DONE'
              AND s.deleted_at IS NULL
              AND s.status <> 'CANCELED'
              AND (
                  s.leader_id = :profileMemberId
                  OR EXISTS (
                      SELECT 1
                      FROM study_member sm
                      WHERE sm.study_id = s.id
                        AND sm.member_id = :profileMemberId
                  )
              )
            """;

    String MEMBER_REPORT_SELECT = """
            SELECT r.id AS reportId,
                   CAST(r.result_json AS TEXT) AS resultJson,
                   r.status AS status,
                   CASE WHEN rf.id IS NULL THEN FALSE ELSE TRUE END
                       AS favoritedByMe,
                   a.id AS apartmentId,
                   a.name AS apartmentName,
                   s.id AS studyId,
                   s.title AS studyTitle,
                   fs.started_at AS visitedAt,
                   (
                       SELECT COUNT(*)
                       FROM field_session count_fs
                       JOIN field_participant count_fp
                         ON count_fp.session_id = count_fs.id
                       WHERE count_fs.study_id = s.id
                   ) AS participantCount,
                   CASE WHEN EXISTS (
                       SELECT 1
                       FROM field_session viewer_fs
                       JOIN field_participant viewer_fp
                         ON viewer_fp.session_id = viewer_fs.id
                       WHERE viewer_fs.study_id = s.id
                         AND viewer_fp.member_id = :memberId
                   ) THEN TRUE ELSE FALSE END AS canViewEvidence,
                   r.completed_at AS completedAt
            FROM report r
            JOIN study s ON s.id = r.study_id
            JOIN apartment a ON a.id = r.apartment_id
            LEFT JOIN field_session fs ON fs.study_id = s.id
            LEFT JOIN report_favorite rf
              ON rf.report_id = r.id
             AND rf.member_id = :memberId
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
            """;

    String MEMBER_REPORT_COUNT = """
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
            """;

    @Query(
            value = MEMBER_REPORT_SELECT
                    + " ORDER BY r.completed_at DESC NULLS LAST, r.id DESC",
            countQuery = MEMBER_REPORT_COUNT,
            nativeQuery = true
    )
    Page<MemberReportRow> findAccessibleDoneReports(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT r.id AS reportId,
                           CAST(r.result_json AS TEXT) AS resultJson,
                           a.id AS apartmentId,
                           a.name AS apartmentName,
                           a.address AS apartmentAddress,
                           a.dong_name AS dongName,
                           r.completed_at AS completedAt,
                           rf.created_at AS favoritedAt
                    """
                    + FAVORITE_REPORT_FROM
                    + " ORDER BY rf.created_at DESC, rf.id DESC",
            countQuery = "SELECT COUNT(*) " + FAVORITE_REPORT_FROM,
            nativeQuery = true
    )
    Page<FavoriteReportRow> findFavoriteDoneReports(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT r.id AS reportId,
                           CAST(r.result_json AS TEXT) AS resultJson,
                           CASE WHEN rf.id IS NULL
                               THEN FALSE ELSE TRUE
                           END AS favoritedByMe,
                           a.id AS apartmentId,
                           a.name AS apartmentName,
                           s.id AS studyId,
                           s.title AS studyTitle,
                           (
                               SELECT COUNT(*)
                               FROM study_member count_sm
                               WHERE count_sm.study_id = s.id
                                 AND count_sm.status = 'ACTIVE'
                           ) AS participantCount,
                           r.completed_at AS completedAt
                    """
                    + PUBLIC_PROFILE_REPORT_FROM
                    + " ORDER BY r.completed_at DESC NULLS LAST, r.id DESC",
            countQuery = "SELECT COUNT(*) "
                    + PUBLIC_PROFILE_REPORT_FROM,
            nativeQuery = true
    )
    Page<PublicProfileReportRow> findPublicProfileReports(
            @Param("viewerId") Long viewerId,
            @Param("profileMemberId") Long profileMemberId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM report r
                    JOIN study s ON s.id = r.study_id
                    WHERE r.status = 'DONE'
                      AND s.deleted_at IS NULL
                      AND s.status <> 'CANCELED'
                      AND (
                          s.leader_id = :profileMemberId
                          OR EXISTS (
                              SELECT 1
                              FROM study_member sm
                              WHERE sm.study_id = s.id
                                AND sm.member_id = :profileMemberId
                          )
                      )
                    """,
            nativeQuery = true
    )
    long countPublicProfileReports(
            @Param("profileMemberId") Long profileMemberId
    );
}
