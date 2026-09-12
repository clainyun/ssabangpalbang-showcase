package com.ssafy.ssabangpalbang.home.repository;

import com.ssafy.ssabangpalbang.study.domain.Study;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface HomeQueryRepository extends Repository<Study, Long> {

    String ACCESSIBLE_STUDY = """
            s.deleted_at IS NULL
            AND (
                s.leader_id = :memberId
                OR EXISTS (
                    SELECT 1
                    FROM study_member member_sm
                    WHERE member_sm.study_id = s.id
                      AND member_sm.member_id = :memberId
                      AND member_sm.status = 'ACTIVE'
                )
            )
            """;

    @Query(
            value = """
                    SELECT sch.start_at AS startAt,
                           sch.meeting_place AS meetingPlace,
                           s.id AS studyId,
                           s.capacity AS capacity,
                           (
                               SELECT COUNT(*)
                               FROM study_member count_sm
                               WHERE count_sm.study_id = s.id
                                 AND count_sm.status = 'ACTIVE'
                           ) AS currentMemberCount,
                           a.name AS apartmentName,
                           a.address AS apartmentAddress,
                           a.district_name AS districtName,
                           a.latitude AS latitude,
                           a.longitude AS longitude
                    FROM schedule sch
                    JOIN study s ON s.id = sch.study_id
                    JOIN apartment a ON a.id = s.apartment_id
                    WHERE sch.status = 'SCHEDULED'
                      AND sch.start_at >= :startOfToday
                      AND s.status IN ('RECRUITING', 'CLOSED', 'IN_PROGRESS')
                      AND NOT EXISTS (
                          SELECT 1
                          FROM field_session fs
                          WHERE fs.study_id = s.id
                            AND fs.status = 'ENDED'
                      )
                      AND
                    """ + ACCESSIBLE_STUDY + """
                    ORDER BY sch.start_at ASC,
                             sch.id ASC
                    """,
            nativeQuery = true
    )
    List<HomeNextVisitRow> findNextVisit(
            @Param("memberId") Long memberId,
            @Param("startOfToday") Instant startOfToday,
            Pageable pageable
    );
}
