package com.ssafy.ssabangpalbang.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.Objects;

@Entity
@Table(name = "study_notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyNotice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "study_id", nullable = false)
    private Long studyId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static StudyNotice create(Long studyId, String content) {
        StudyNotice notice = new StudyNotice();
        notice.studyId = Objects.requireNonNull(studyId);
        notice.content = Objects.requireNonNull(content);
        return notice;
    }

    public void updateContent(String content) {
        this.content = Objects.requireNonNull(content);
    }

    public void delete(Instant deletedAt) {
        this.deletedAt = Objects.requireNonNull(deletedAt);
    }
}
