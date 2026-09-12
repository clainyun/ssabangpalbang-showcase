package com.ssafy.ssabangpalbang.report.domain;

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

import java.time.Instant;

@Getter
@Entity
@Table(name = "report_favorite")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private ReportFavorite(Long memberId, Long reportId) {
        this.memberId = memberId;
        this.reportId = reportId;
    }

    public static ReportFavorite of(Long memberId, Long reportId) {
        return new ReportFavorite(memberId, reportId);
    }
}
