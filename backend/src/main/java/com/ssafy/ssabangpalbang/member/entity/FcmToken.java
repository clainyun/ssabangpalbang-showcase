package com.ssafy.ssabangpalbang.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "fcm_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private FcmToken(
            Long memberId,
            String token,
            String deviceId,
            OffsetDateTime updatedAt
    ) {
        this.memberId = memberId;
        this.token = token;
        this.deviceId = deviceId;
        this.updatedAt = updatedAt;
    }

    public static FcmToken of(
            Long memberId,
            String token,
            String deviceId,
            OffsetDateTime updatedAt
    ) {
        return new FcmToken(memberId, token, deviceId, updatedAt);
    }

    public void updateToken(
            String token,
            OffsetDateTime updatedAt
    ) {
        this.token = token;
        this.updatedAt = updatedAt;
    }
}
