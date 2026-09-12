package com.ssafy.ssabangpalbang.community.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostTest {

    @Test
    void createsMemberPostWithServerOwnedDefaults() {
        Post post = Post.createMemberPost(
                7L,
                BoardType.INFORMATION,
                "제목",
                "본문",
                15L
        );

        assertThat(post.getAuthorId()).isEqualTo(7L);
        assertThat(post.getBoardType()).isEqualTo(BoardType.INFORMATION);
        assertThat(post.getStatus()).isEqualTo(PostStatus.ACTIVE);
        assertThat(post.isAutoReport()).isFalse();
        assertThat(post.getReportId()).isNull();
        assertThat(post.getViewCount()).isZero();
        assertThat(post.getDeletedAt()).isNull();
    }

    @Test
    void createsAutomaticInformationPostWithReportDetailPath() {
        Post post = Post.createAutomaticReportPost(
                48L,
                15L,
                "래미안 옥수 리버젠",
                Instant.parse("2026-08-02T03:30:00Z"),
                "옥수 임장 리포트",
                "교통 접근성이 좋고 보행 환경을 함께 확인했습니다."
        );

        assertThat(post.getBoardType()).isEqualTo(BoardType.INFORMATION);
        assertThat(post.getAuthorId()).isNull();
        assertThat(post.isAutoReport()).isTrue();
        assertThat(post.getReportId()).isEqualTo(48L);
        assertThat(post.getApartmentId()).isEqualTo(15L);
        assertThat(post.getTitle()).isEqualTo("옥수 임장 리포트");
        assertThat(post.getContent()).contains(
                "아파트: 래미안 옥수 리버젠",
                "임장일: 2026-08-02",
                "요약: 교통 접근성이 좋고 보행 환경을 함께 확인했습니다.",
                "리포트 상세: /api/v1/reports/48"
        );
    }

    @Test
    void initializesCreatedAndUpdatedAtToSameInstant() {
        Post post = Post.createMemberPost(
                7L,
                BoardType.FREE,
                "제목",
                "본문",
                null
        );

        ReflectionTestUtils.invokeMethod(post, "initializeTimestamps");

        assertThat(post.getCreatedAt()).isNotNull();
        assertThat(post.getUpdatedAt()).isEqualTo(post.getCreatedAt());
    }

    @Test
    void attachmentUsesZeroBasedOrderAndRejectsNegativeOrder() {
        PostAttachment attachment =
                PostAttachment.create(154L, 401L, 0);

        assertThat(attachment.getPostId()).isEqualTo(154L);
        assertThat(attachment.getFileId()).isEqualTo(401L);
        assertThat(attachment.getDisplayOrder()).isZero();
        assertThatThrownBy(
                () -> PostAttachment.create(154L, 401L, -1)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatesOnlyExplicitDomainFieldsAndTimestamp() {
        Post post = Post.createMemberPost(
                7L,
                BoardType.INFORMATION,
                "기존 제목",
                "기존 본문",
                15L
        );
        Instant updatedAt = Instant.parse("2026-07-29T07:50:00Z");

        post.updateBoardType(BoardType.FREE);
        post.updateTitle("수정 제목");
        post.updateContent("수정 본문");
        post.changeApartment(null);
        post.markUpdated(updatedAt);
        ReflectionTestUtils.invokeMethod(post, "updateTimestamp");

        assertThat(post.getBoardType()).isEqualTo(BoardType.FREE);
        assertThat(post.getTitle()).isEqualTo("수정 제목");
        assertThat(post.getContent()).isEqualTo("수정 본문");
        assertThat(post.getApartmentId()).isNull();
        assertThat(post.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(post.isOwnedBy(7L)).isTrue();
        assertThat(post.isOwnedBy(8L)).isFalse();
    }

    @Test
    void softDeletesWithoutChangingPostContentOrStatus() {
        Post post = Post.createMemberPost(
                7L,
                BoardType.FREE,
                "기존 제목",
                "기존 본문",
                15L
        );
        Instant deletedAt = Instant.parse("2026-07-29T08:00:00Z");

        post.delete(deletedAt);
        ReflectionTestUtils.invokeMethod(post, "updateTimestamp");

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(post.getUpdatedAt()).isEqualTo(deletedAt);
        assertThat(post.getStatus()).isEqualTo(PostStatus.ACTIVE);
        assertThat(post.isHidden()).isFalse();
        assertThat(post.getTitle()).isEqualTo("기존 제목");
        assertThat(post.getContent()).isEqualTo("기존 본문");
        assertThat(post.getApartmentId()).isEqualTo(15L);
    }

    @Test
    void rejectsRepeatedAndAutomaticPostDeletion() {
        Instant deletedAt = Instant.parse("2026-07-29T08:00:00Z");
        Post deleted = Post.createMemberPost(
                7L,
                BoardType.FREE,
                "제목",
                "본문",
                null
        );
        deleted.delete(deletedAt);

        assertThatThrownBy(() -> deleted.delete(deletedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);

        Post automatic = Post.createMemberPost(
                7L,
                BoardType.INFORMATION,
                "리포트",
                "본문",
                15L
        );
        ReflectionTestUtils.setField(automatic, "autoReport", true);

        assertThatThrownBy(() -> automatic.delete(deletedAt))
                .isInstanceOf(IllegalStateException.class);
        assertThat(automatic.isDeleted()).isFalse();
    }
}
