package com.ssafy.ssabangpalbang.member.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Entity
@Table(name = "member_preference")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberPreference {

    private static final String SINGLE = "SINGLE";
    private static final String SINGLE_PERSON = "SINGLE_PERSON";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(length = 50)
    private String purpose;

    @Column(name = "household_type", length = 50)
    private String householdType;

    @Column(name = "marital_status", length = 20)
    private String maritalStatus;

    @Column(name = "has_vehicle")
    private Boolean hasVehicle;

    @Column(name = "has_children")
    private Boolean hasChildren;

    @Column(length = 50)
    private String budget;

    @Column(name = "interest_region", length = 100)
    private String interestRegion;

    @Column(name = "interest_region_public_agreed", nullable = false)
    private boolean interestRegionPublicAgreed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> priorities = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MemberPreference(Long memberId) {
        this.memberId = Objects.requireNonNull(memberId);
    }

    public void update(
            String purpose,
            String householdType,
            String maritalStatus,
            Boolean hasVehicle,
            Boolean hasChildren,
            String budget,
            String interestRegion,
            List<String> priorities
    ) {
        this.purpose = purpose;
        this.householdType = householdType;
        this.maritalStatus = maritalStatus;
        this.hasVehicle = hasVehicle;
        this.hasChildren = hasChildren;
        this.budget = budget;
        this.interestRegion = interestRegion;
        this.priorities = new ArrayList<>(
                Objects.requireNonNull(priorities)
        );
    }

    public void updateOnboarding(
            String purpose,
            String maritalStatus,
            Boolean hasVehicle,
            Boolean hasChildren,
            List<String> priorities
    ) {
        this.purpose = purpose;
        this.householdType = householdTypeFor(maritalStatus);
        this.maritalStatus = maritalStatus;
        this.hasVehicle = hasVehicle;
        this.hasChildren = hasChildren;
        this.budget = null;
        this.interestRegion = null;
        this.interestRegionPublicAgreed = false;
        this.priorities = new ArrayList<>(
                Objects.requireNonNull(priorities)
        );
    }

    public void updateProfile(
            String purpose,
            List<String> priorities,
            String maritalStatus,
            Boolean hasVehicle,
            Boolean hasChildren
    ) {
        this.purpose = purpose;
        this.householdType = householdTypeFor(maritalStatus);
        this.priorities = new ArrayList<>(
                Objects.requireNonNull(priorities)
        );
        this.maritalStatus = maritalStatus;
        this.hasVehicle = hasVehicle;
        this.hasChildren = hasChildren;
        this.budget = null;
        this.interestRegion = null;
        this.interestRegionPublicAgreed = false;
    }

    public List<String> getPriorities() {
        return priorities == null ? List.of() : List.copyOf(priorities);
    }

    private static String householdTypeFor(String maritalStatus) {
        return SINGLE.equals(maritalStatus) ? SINGLE_PERSON : null;
    }
}
