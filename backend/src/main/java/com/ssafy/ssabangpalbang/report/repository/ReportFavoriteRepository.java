package com.ssafy.ssabangpalbang.report.repository;

import com.ssafy.ssabangpalbang.report.domain.ReportFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReportFavoriteRepository
        extends JpaRepository<ReportFavorite, Long> {

    Optional<ReportFavorite> findByMemberIdAndReportId(
            Long memberId,
            Long reportId
    );

    long countByReportId(Long reportId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM ReportFavorite favorite
            WHERE favorite.memberId = :memberId
              AND favorite.reportId = :reportId
            """)
    int deleteByMemberIdAndReportId(
            @Param("memberId") Long memberId,
            @Param("reportId") Long reportId
    );
}
