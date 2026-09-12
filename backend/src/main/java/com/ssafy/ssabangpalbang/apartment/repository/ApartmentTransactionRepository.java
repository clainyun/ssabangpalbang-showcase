package com.ssafy.ssabangpalbang.apartment.repository;

import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ApartmentTransactionRepository extends JpaRepository<ApartmentTransaction, Long> {

    boolean existsByDedupKey(String dedupKey);

    @Query("""
            select t from ApartmentTransaction t
            where t.apartmentId = :apartmentId
              and t.canceled = false
              and (:areaMin is null or t.exclusiveArea between :areaMin and :areaMax)
              and (:startDate is null or t.dealDate between :startDate and :endDate)
            """)
    Page<ApartmentTransaction> search(
            @Param("apartmentId") Long apartmentId,
            @Param("areaMin") BigDecimal areaMin,
            @Param("areaMax") BigDecimal areaMax,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    @Query(value = """
            SELECT x.id, x.apartment_id, x.deal_date, x.exclusive_area,
                   x.price, x.floor, x.is_canceled, x.dedup_key, x.collected_at
            FROM (
              SELECT t.*, ROW_NUMBER() OVER (
                PARTITION BY t.apartment_id
                ORDER BY t.deal_date DESC, t.collected_at DESC, t.id DESC
              ) rn
              FROM apartment_transaction t
              WHERE t.apartment_id IN (:apartmentIds) AND t.is_canceled = false
            ) x WHERE x.rn = 1
            """, nativeQuery = true)
    List<ApartmentTransaction> findLatestNormalByApartmentIds(
            @Param("apartmentIds") Collection<Long> apartmentIds
    );
}
