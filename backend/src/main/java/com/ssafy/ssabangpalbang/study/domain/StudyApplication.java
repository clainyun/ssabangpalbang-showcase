package com.ssafy.ssabangpalbang.study.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "study_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "study_id", nullable = false)
    private Long studyId;
    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;
    @Column(length = 200)
    private String intro;
    @Column(length = 20)
    private String purpose;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyApplicationStatus status = StudyApplicationStatus.PENDING;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "decided_at")
    private Instant decidedAt;

    public static StudyApplication create(
            Long studyId,
            Long applicantId,
            String intro,
            StudyPurpose purpose
    ) {
        StudyApplication application = new StudyApplication();
        application.studyId = studyId;
        application.applicantId = applicantId;
        application.intro = intro;
        application.purpose = purpose.name();
        application.status = StudyApplicationStatus.PENDING;
        application.decidedAt = null;
        return application;
    }

    public void approve(Instant decidedAt) {
        this.status = StudyApplicationStatus.APPROVED;
        this.decidedAt = decidedAt;
    }

    public void reject(Instant decidedAt) {
        this.status = StudyApplicationStatus.REJECTED;
        this.decidedAt = decidedAt;
    }
}
