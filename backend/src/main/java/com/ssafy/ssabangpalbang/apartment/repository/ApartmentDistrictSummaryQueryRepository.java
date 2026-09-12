package com.ssafy.ssabangpalbang.apartment.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ApartmentDistrictSummaryQueryRepository {

    private final EntityManager entityManager;

    public List<ApartmentDistrictSummaryRow> findDistrictSummaries() {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT district_code,
                               COUNT(*)       AS apartment_count,
                               AVG(latitude)  AS center_latitude,
                               AVG(longitude) AS center_longitude
                        FROM apartment
                        WHERE district_code IS NOT NULL
                        GROUP BY district_code
                        """)
                .getResultList();

        return rows.stream()
                .map(this::toDistrictSummaryRow)
                .toList();
    }

    private ApartmentDistrictSummaryRow toDistrictSummaryRow(Object row) {
        Object[] values = (Object[]) row;
        return new ApartmentDistrictSummaryRow(
                (String) values[0],
                ((Number) values[1]).longValue(),
                values[2] == null ? null : ((Number) values[2]).doubleValue(),
                values[3] == null ? null : ((Number) values[3]).doubleValue()
        );
    }
}
