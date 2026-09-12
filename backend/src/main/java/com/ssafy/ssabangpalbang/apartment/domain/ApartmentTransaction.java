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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "apartment_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApartmentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "apartment_id", nullable = false)
    private Long apartmentId;

    @Column(name = "deal_date", nullable = false)
    private LocalDate dealDate;

    @Column(name = "exclusive_area")
    private BigDecimal exclusiveArea;

    @Column(name = "price")
    private Long price;

    @Column(name = "floor")
    private Integer floor;

    @Column(name = "is_canceled", nullable = false)
    private boolean canceled;

    @Column(name = "dedup_key", nullable = false, unique = true, length = 200)
    private String dedupKey;

    @CreationTimestamp
    @Column(name = "collected_at", nullable = false, updatable = false)
    private Instant collectedAt;

    public static ApartmentTransaction create(
            Long apartmentId,
            LocalDate dealDate,
            BigDecimal exclusiveArea,
            Long price,
            Integer floor,
            boolean canceled,
            String dedupKey
    ) {
        ApartmentTransaction transaction = new ApartmentTransaction();
        transaction.apartmentId = apartmentId;
        transaction.dealDate = dealDate;
        transaction.exclusiveArea = exclusiveArea;
        transaction.price = price;
        transaction.floor = floor;
        transaction.canceled = canceled;
        transaction.dedupKey = dedupKey;
        return transaction;
    }
}
