package com.ssafy.ssabangpalbang.auth.domain;

import com.ssafy.ssabangpalbang.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "social_account",
        uniqueConstraints = @UniqueConstraint(
                name = "social_account_provider_social_user_id_key",
                columnNames = {"provider", "social_user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "social_user_id", nullable = false, length = 255)
    private String socialUserId;

    @Column(length = 255)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SocialAccount(
            Member member,
            SocialProvider provider,
            String socialUserId,
            String email
    ) {
        this.member = Objects.requireNonNull(member);
        this.provider = Objects.requireNonNull(provider);
        this.socialUserId = Objects.requireNonNull(socialUserId);
        this.email = email;
    }
}
