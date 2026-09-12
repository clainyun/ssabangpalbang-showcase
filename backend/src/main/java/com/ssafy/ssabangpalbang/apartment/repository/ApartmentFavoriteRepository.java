package com.ssafy.ssabangpalbang.apartment.repository;

import com.ssafy.ssabangpalbang.apartment.domain.ApartmentFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ApartmentFavoriteRepository extends JpaRepository<ApartmentFavorite, Long> {

    boolean existsByMemberIdAndApartmentId(Long memberId, Long apartmentId);

    long deleteByMemberIdAndApartmentId(Long memberId, Long apartmentId);

    long countByApartmentId(Long apartmentId);

    Page<ApartmentFavorite> findByMemberIdOrderByCreatedAtDescIdDesc(
            Long memberId,
            Pageable pageable
    );

    @Query("select f.apartmentId from ApartmentFavorite f "
            + "where f.memberId = :memberId and f.apartmentId in :apartmentIds")
    List<Long> findFavoritedApartmentIds(
            @Param("memberId") Long memberId,
            @Param("apartmentIds") Collection<Long> apartmentIds
    );
}
