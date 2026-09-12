package com.ssafy.ssabangpalbang.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "study")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Study {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "apartment_id", nullable = false)
    private Long apartmentId;

    @Column(name = "leader_id", nullable = false)
    private Long leaderId;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String intro;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String goal;

    @Column(nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private StudyPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyStatus status = StudyStatus.RECRUITING;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "recruitment_closed_at")
    private Instant recruitmentClosedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    public static Study create(
            Long apartmentId,
            Long leaderId,
            String title,
            String intro,
            String goal,
            Integer capacity,
            StudyPurpose purpose
    ) {
        Study study = new Study();
        study.apartmentId = apartmentId;
        study.leaderId = leaderId;
        study.title = title;
        study.intro = intro;
        study.goal = goal;
        study.capacity = capacity;
        study.purpose = purpose;
        return study;
    }

    public void closeRecruitment(Instant closedAt) {
        this.status = StudyStatus.CLOSED;
        this.recruitmentClosedAt = closedAt;
    }

    /**
     * 조기 마감된 모집을 재개한다(BE-008). CLOSED → RECRUITING으로 전이하고
     * 조기 마감 시각을 초기화한다. 상태 전이 가능 여부는 서비스에서 검증한다.
     */
    public void reopenRecruitment() {
        this.status = StudyStatus.RECRUITING;
        this.recruitmentClosedAt = null;
    }
    public void cancel(Instant canceledAt) {
        this.status = StudyStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    public void updateDetails(String title, String intro, String goal) {
        if (title != null) {
            this.title = title;
        }
        if (intro != null) {
            this.intro = intro;
        }
        if (goal != null) {
            this.goal = goal;
        }
    }

    /**
     * 최초 임장 세션 생성 시 CLOSED → IN_PROGRESS 전이(BE-014).
     *
     * @throws IllegalStateException CLOSED가 아닌 경우
     */
    public void markInProgress() {
        if (this.status != StudyStatus.CLOSED) {
            throw new IllegalStateException(
                    "Study status must be CLOSED to start field visit, but was "
                            + this.status
            );
        }
        this.status = StudyStatus.IN_PROGRESS;
    }

    /**
     * 최초 리포트 완료 시 IN_PROGRESS → COMPLETED로 전이한다(BE-031).
     *
     * <p>동일 완료 이벤트 재처리는 멱등하게 무시한다. 그 외 상태를 완료로
     * 덮어쓰면 취소·모집 상태가 유실될 수 있으므로 허용하지 않는다.</p>
     *
     * @throws IllegalStateException IN_PROGRESS 또는 COMPLETED가 아닌 경우
     */
    public void markCompleted() {
        if (this.status == StudyStatus.COMPLETED) {
            return;
        }
        if (this.status != StudyStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Study status must be IN_PROGRESS to complete, but was "
                            + this.status
            );
        }
        this.status = StudyStatus.COMPLETED;
    }
}
