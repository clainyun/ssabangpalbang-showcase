package com.ssafy.ssabangpalbang.apartment.domain;

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
@Table(name = "apartment_favorite")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApartmentFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "apartment_id", nullable = false)
    private Long apartmentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private ApartmentFavorite(Long memberId, Long apartmentId) {
        this.memberId = memberId;
        this.apartmentId = apartmentId;
    }

    public static ApartmentFavorite of(Long memberId, Long apartmentId) {
        return new ApartmentFavorite(memberId, apartmentId);
    }
}
