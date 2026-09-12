package com.ssafy.ssabangpalbang.apartment.repository;

import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentBoundsCondition;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ApartmentBoundsQueryRepository {

    private final EntityManager entityManager;

    public List<ApartmentBoundsRow> findApartments(ApartmentBoundsCondition condition) {
        double centerLat = (condition.southWestLat() + condition.northEastLat()) / 2;
        double centerLng = (condition.southWestLng() + condition.northEastLng()) / 2;
        double latSpan = condition.northEastLat() - condition.southWestLat();
        double lngSpan = condition.northEastLng() - condition.southWestLng();

        List<?> rows = entityManager.createNativeQuery("""
                        SELECT a.id, a.name, a.address, a.latitude, a.longitude,
                               t.price, t.exclusive_area, t.deal_date
                        FROM (
                            SELECT id, name, address, latitude, longitude
                            FROM apartment
                            WHERE latitude BETWEEN :southWestLat AND :northEastLat
                              AND longitude BETWEEN :southWestLng AND :northEastLng
                            ORDER BY
                                (latitude - :centerLat) * (latitude - :centerLat)
                                    / (:latSpan * :latSpan)
                              + (longitude - :centerLng) * (longitude - :centerLng)
                                    / (:lngSpan * :lngSpan)
                            LIMIT 500
                        ) a
                        LEFT JOIN LATERAL (
                            SELECT tx.price, tx.exclusive_area, tx.deal_date
                            FROM apartment_transaction tx
                            WHERE tx.apartment_id = a.id
                              AND tx.is_canceled = false
                            ORDER BY tx.deal_date DESC, tx.collected_at DESC, tx.id DESC
                            LIMIT 1
                        ) t ON TRUE
                        ORDER BY a.id ASC
                        """)
                .setParameter("southWestLat", condition.southWestLat())
                .setParameter("southWestLng", condition.southWestLng())
                .setParameter("northEastLat", condition.northEastLat())
                .setParameter("northEastLng", condition.northEastLng())
                .setParameter("centerLat", centerLat)
                .setParameter("centerLng", centerLng)
                .setParameter("latSpan", latSpan)
                .setParameter("lngSpan", lngSpan)
                .getResultList();
        return rows.stream().map(this::toApartmentRow).toList();
    }

    private ApartmentBoundsRow toApartmentRow(Object row) {
        Object[] values = (Object[]) row;
        return new ApartmentBoundsRow(
                ((Number) values[0]).longValue(),
                (String) values[1],
                (String) values[2],
                ((Number) values[3]).doubleValue(),
                ((Number) values[4]).doubleValue(),
                values[5] == null ? null : ((Number) values[5]).longValue(),
                (BigDecimal) values[6],
                values[7] == null ? null : toLocalDate(values[7])
        );
    }

    private LocalDate toLocalDate(Object value) {
        return value instanceof LocalDate localDate
                ? localDate
                : ((Date) value).toLocalDate();
    }
}
