package com.ssafy.ssabangpalbang.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

@Getter
@Entity
@DynamicUpdate
@Table(name = "post")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final int AUTO_REPORT_SUMMARY_MAX_CODE_POINTS = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 20)
    private BoardType boardType;

    @Column(name = "author_id")
    private Long authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(name = "is_auto_report", nullable = false)
    private boolean autoReport;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "apartment_id")
    private Long apartmentId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean explicitlyUpdated;

    public static Post createMemberPost(
            Long authorId,
            BoardType boardType,
            String title,
            String content,
            Long apartmentId
    ) {
        Post post = new Post();
        post.authorId = Objects.requireNonNull(authorId);
        post.boardType = Objects.requireNonNull(boardType);
        post.title = Objects.requireNonNull(title);
        post.content = Objects.requireNonNull(content);
        post.apartmentId = apartmentId;
        post.status = PostStatus.ACTIVE;
        post.autoReport = false;
        post.reportId = null;
        post.viewCount = 0L;
        post.deletedAt = null;
        return post;
    }

    /**
     * Creates the system-owned information post for one completed report.
     * reportId is the authority for all later navigation to report detail.
     */
    public static Post createAutomaticReportPost(
            Long reportId,
            Long apartmentId,
            String apartmentName,
            Instant fieldSessionStartedAt,
            String title,
            String summary
    ) {
        Post post = new Post();
        post.authorId = null;
        post.boardType = BoardType.INFORMATION;
        post.title = requireAutomaticText(title, "report title");
        post.content = "아파트: "
                + requireAutomaticText(apartmentName, "apartment name")
                + "\n임장일: "
                + Objects.requireNonNull(fieldSessionStartedAt)
                .atZone(SEOUL_ZONE_ID).toLocalDate()
                + "\n요약: "
                + summaryPortion(summary)
                + "\n\n리포트 상세: /api/v1/reports/"
                + Objects.requireNonNull(reportId);
        post.status = PostStatus.ACTIVE;
        post.autoReport = true;
        post.reportId = Objects.requireNonNull(reportId);
        post.apartmentId = Objects.requireNonNull(apartmentId);
        post.viewCount = 0L;
        post.deletedAt = null;
        return post;
    }

    private static String requireAutomaticText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty() || normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }

    private static String summaryPortion(String summary) {
        String normalized = requireAutomaticText(summary, "report summary");
        int length = normalized.codePointCount(0, normalized.length());
        if (length <= AUTO_REPORT_SUMMARY_MAX_CODE_POINTS) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(
                0,
                AUTO_REPORT_SUMMARY_MAX_CODE_POINTS
        );
        return normalized.substring(0, end) + "…";
    }

    public void updateBoardType(BoardType boardType) {
        this.boardType = Objects.requireNonNull(boardType);
    }

    public void updateTitle(String title) {
        this.title = Objects.requireNonNull(title);
    }

    public void updateContent(String content) {
        this.content = Objects.requireNonNull(content);
    }

    public void changeApartment(Long apartmentId) {
        this.apartmentId = apartmentId;
    }

    public boolean isOwnedBy(Long memberId) {
        return Objects.equals(authorId, memberId);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isHidden() {
        return status == PostStatus.HIDDEN;
    }

    public void delete(Instant now) {
        if (isDeleted()) {
            throw new IllegalStateException("이미 삭제된 게시글입니다.");
        }
        if (autoReport) {
            throw new IllegalStateException(
                    "자동 리포트 게시글은 회원이 삭제할 수 없습니다."
            );
        }

        Instant deletedAt = Objects.requireNonNull(now);
        this.deletedAt = deletedAt;
        markUpdated(deletedAt);
    }

    public void markUpdated(Instant now) {
        this.updatedAt = Objects.requireNonNull(now);
        this.explicitlyUpdated = true;
    }

    @PrePersist
    private void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() {
        if (!explicitlyUpdated) {
            updatedAt = Instant.now();
        }
        explicitlyUpdated = false;
    }
}
