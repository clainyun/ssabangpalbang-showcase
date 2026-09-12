package com.ssafy.ssabangpalbang.apartment.repository;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApartmentRepository extends JpaRepository<Apartment, Long> {

    Optional<Apartment> findByComplexCode(String complexCode);

    List<Apartment> findByDistrictCode(String districtCode);

    boolean existsByDistrictCode(String districtCode);

    boolean existsByLegalDongCode(String legalDongCode);

    @Query(value = """
            SELECT apartment_image.object_key
            FROM apartment_image
            WHERE apartment_image.apartment_id = :apartmentId
            """, nativeQuery = true)
    Optional<String> findImageObjectKeyByApartmentId(
            @Param("apartmentId") Long apartmentId
    );

    @Query(value = """
            SELECT apartment_image.apartment_id AS apartmentId,
                   apartment_image.object_key AS objectKey
            FROM apartment_image
            WHERE apartment_image.apartment_id IN (:apartmentIds)
            """, nativeQuery = true)
    List<ApartmentImageKeyRow> findImageObjectKeysByApartmentIds(
            @Param("apartmentIds") List<Long> apartmentIds
    );
}
