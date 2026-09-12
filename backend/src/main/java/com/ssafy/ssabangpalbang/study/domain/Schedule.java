package com.ssafy.ssabangpalbang.study.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "study_id", nullable = false, unique = true)
    private Long studyId;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    @Column(name = "end_at")
    private Instant endAt;
    @Column(name = "meeting_place", length = 200)
    private String meetingPlace;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Schedule create(Long studyId, Instant startAt, Instant endAt, String meetingPlace) {
        Schedule schedule = new Schedule(); schedule.studyId = studyId; schedule.startAt = startAt;
        schedule.endAt = endAt; schedule.meetingPlace = meetingPlace; schedule.status = ScheduleStatus.SCHEDULED;
        return schedule;
    }
    public void reactivate(Instant startAt, Instant endAt, String meetingPlace) { this.startAt=startAt; this.endAt=endAt; this.meetingPlace=meetingPlace; this.status=ScheduleStatus.SCHEDULED; }
    public void update(Instant startAt, Instant endAt, String meetingPlace) { if(startAt!=null)this.startAt=startAt; if(endAt!=null)this.endAt=endAt; if(meetingPlace!=null)this.meetingPlace=meetingPlace; }
    public void cancel() { this.status = ScheduleStatus.CANCELED; }
}
