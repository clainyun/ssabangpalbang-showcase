package com.ssafy.ssabangpalbang.study.repository;

import com.ssafy.ssabangpalbang.study.domain.Schedule;
import com.ssafy.ssabangpalbang.study.domain.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    Optional<Schedule> findByStudyId(Long studyId);
    Optional<Schedule> findByStudyIdAndStatus(Long studyId, ScheduleStatus status);

    @Query(value = """
            SELECT schedule.id AS scheduleId,
                   study.id AS studyId,
                   study.title AS studyTitle,
                   study.leader_id AS leaderId,
                   schedule.start_at AS startAt,
                   schedule.meeting_place AS meetingPlace
            FROM schedule
            JOIN study ON study.id = schedule.study_id
            WHERE schedule.status = 'SCHEDULED'
              AND schedule.start_at > :now
              AND schedule.start_at <= :until
              AND study.deleted_at IS NULL
              AND study.status IN ('RECRUITING', 'CLOSED', 'IN_PROGRESS')
            ORDER BY schedule.start_at ASC, schedule.id ASC
            """, nativeQuery = true)
    List<ScheduleReminderRow> findRemindableSchedules(
            @Param("now") Instant now,
            @Param("until") Instant until
    );

    @Query(value = """
            SELECT schedule.id AS scheduleId,
                   study.id AS studyId,
                   study.title AS studyTitle,
                   apartment.name AS apartmentName,
                   schedule.start_at AS startAt,
                   schedule.status AS scheduleStatus
            FROM schedule
            JOIN study ON study.id = schedule.study_id
            JOIN apartment ON apartment.id = study.apartment_id
            WHERE schedule.start_at >= :startInclusive
              AND schedule.start_at < :endExclusive
              AND schedule.status IN ('SCHEDULED', 'COMPLETED')
              AND study.deleted_at IS NULL
              AND study.status <> 'CANCELED'
              AND (
                  study.leader_id = :memberId
                  OR EXISTS (
                      SELECT 1
                      FROM study_member
                      WHERE study_member.study_id = study.id
                        AND study_member.member_id = :memberId
                        AND study_member.status = 'ACTIVE'
                  )
              )
            ORDER BY schedule.start_at ASC, schedule.id ASC
            """, nativeQuery = true)
    List<VisitCalendarRow> findVisitCalendar(
            @Param("memberId") Long memberId,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive
    );
}
