package com.ssafy.ssabangpalbang.member.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import java.util.Locale;
import java.util.Objects;

@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    private static final String DEFAULT_CHARACTER_ID = "PALBANG";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    @JsonIgnore
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "selected_character_id", nullable = false, length = 20)
    private String selectedCharacterId = DEFAULT_CHARACTER_ID;

    @Column(name = "age_group", length = 20)
    private String ageGroup;

    @Column(name = "age_group_public_agreed", nullable = false)
    private boolean ageGroupPublicAgreed;

    @Column(name = "service_notification_agreed", nullable = false)
    private boolean serviceNotificationAgreed = true;

    @Column(name = "ad_notification_agreed", nullable = false)
    private boolean adNotificationAgreed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Member(
            String email,
            String passwordHash,
            String nickname
    ) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.nickname = Objects.requireNonNull(nickname);
    }

    public void updateOnboardingProfile(
            String ageGroup,
            boolean ageGroupPublicAgreed,
            String selectedCharacterId
    ) {
        this.ageGroup = ageGroup;
        this.ageGroupPublicAgreed = ageGroupPublicAgreed;
        this.selectedCharacterId = Objects.requireNonNull(
                selectedCharacterId
        );
    }

    public void updateProfile(
            String nickname,
            String ageGroup,
            boolean ageGroupPublicAgreed,
            String selectedCharacterId
    ) {
        this.nickname = Objects.requireNonNull(nickname);
        this.ageGroup = ageGroup;
        this.ageGroupPublicAgreed = ageGroupPublicAgreed;
        this.selectedCharacterId = Objects.requireNonNull(
                selectedCharacterId
        );
    }

    public void updateNotificationSettings(
            Boolean serviceNotificationAgreed,
            Boolean adNotificationAgreed
    ) {
        if (serviceNotificationAgreed != null) {
            this.serviceNotificationAgreed = serviceNotificationAgreed;
        }
        if (adNotificationAgreed != null) {
            this.adNotificationAgreed = adNotificationAgreed;
        }
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }

    public void withdraw(Instant withdrawnAt) {
        this.status = MemberStatus.WITHDRAWN;
        this.deletedAt = Objects.requireNonNull(withdrawnAt);
    }

    private static String normalizeEmail(String email) {
        return Objects.requireNonNull(email)
                .strip()
                .toLowerCase(Locale.ROOT);
    }
}
