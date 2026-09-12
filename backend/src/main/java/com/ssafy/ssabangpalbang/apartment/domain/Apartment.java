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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "apartment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Apartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complex_code", nullable = false, unique = true, length = 50)
    private String complexCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(name = "district_code", length = 10)
    private String districtCode;

    @Column(name = "district_name", length = 50)
    private String districtName;

    @Column(name = "dong_name", length = 50)
    private String dongName;

    @Column(name = "legal_dong_code", length = 20)
    private String legalDongCode;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Double latitude;

    @Column(name = "household_count")
    private Integer householdCount;

    @Column(name = "completion_year_month", length = 7)
    private String completionYearMonth;

    @Column(name = "parking_space_count")
    private Integer parkingSpaceCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Apartment create(
            String complexCode, String name, String address,
            String districtCode, String districtName, String dongName,
            String legalDongCode, Double longitude, Double latitude,
            Integer householdCount, String completionYearMonth, Integer parkingSpaceCount
    ) {
        Apartment apartment = new Apartment();
        apartment.complexCode = complexCode;
        apartment.updateMasterInfo(
                name, address, districtCode, districtName, dongName, legalDongCode,
                longitude, latitude, householdCount, completionYearMonth, parkingSpaceCount
        );
        return apartment;
    }

    public void updateMasterInfo(
            String name, String address,
            String districtCode, String districtName, String dongName,
            String legalDongCode, Double longitude, Double latitude,
            Integer householdCount, String completionYearMonth, Integer parkingSpaceCount
    ) {
        this.name = name;
        this.address = address;
        this.districtCode = districtCode;
        this.districtName = districtName;
        this.dongName = dongName;
        this.legalDongCode = legalDongCode;
        this.longitude = longitude;
        this.latitude = latitude;
        this.householdCount = householdCount;
        this.completionYearMonth = completionYearMonth;
        this.parkingSpaceCount = parkingSpaceCount;
    }
}
