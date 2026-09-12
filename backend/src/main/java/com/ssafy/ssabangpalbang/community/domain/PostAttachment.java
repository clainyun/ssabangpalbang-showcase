package com.ssafy.ssabangpalbang.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "post_attachment",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_post_attachment_file",
                        columnNames = "file_id"
                ),
                @UniqueConstraint(
                        name = "uq_post_attachment_post_file",
                        columnNames = {"post_id", "file_id"}
                ),
                @UniqueConstraint(
                        name = "uq_post_attachment_post_order",
                        columnNames = {"post_id", "display_order"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PostAttachment create(
            Long postId,
            Long fileId,
            int displayOrder
    ) {
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must be non-negative");
        }

        PostAttachment attachment = new PostAttachment();
        attachment.postId = Objects.requireNonNull(postId);
        attachment.fileId = Objects.requireNonNull(fileId);
        attachment.displayOrder = displayOrder;
        return attachment;
    }
}
