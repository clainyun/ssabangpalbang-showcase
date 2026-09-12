package com.ssafy.ssabangpalbang.apartment.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ApartmentReportQueryRepository {

    public static final String APARTMENT_REPORT_PUBLIC_FROM = """
            FROM report r
            JOIN study s ON s.id = r.study_id
            WHERE r.status = 'DONE'
              AND s.deleted_at IS NULL
              AND s.status <> 'CANCELED'
            """;

    public static final String APARTMENT_REPORT_COUNT_FROM =
            APARTMENT_REPORT_PUBLIC_FROM
                    + " AND r.apartment_id = :apartmentId";

    private final EntityManager entityManager;

    public Page<ApartmentReportRow> findPublicReports(
            Long apartmentId,
            Long memberId,
            int page,
            int size
    ) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT r.id,
                               CAST(r.result_json AS TEXT),
                               r.completed_at,
                               CASE WHEN rf.id IS NULL THEN FALSE ELSE TRUE END
                        FROM report r
                        JOIN study s ON s.id = r.study_id
                        LEFT JOIN report_favorite rf
                          ON rf.report_id = r.id
                         AND rf.member_id = :memberId
                        WHERE r.apartment_id = :apartmentId
                          AND r.status = 'DONE'
                          AND s.deleted_at IS NULL
                          AND s.status <> 'CANCELED'
                        ORDER BY r.completed_at DESC NULLS LAST, r.id DESC
                        LIMIT :size OFFSET :offset
                        """)
                .setParameter("memberId", memberId)
                .setParameter("apartmentId", apartmentId)
                .setParameter("size", size)
                .setParameter("offset", page * size)
                .getResultList();
        long total = countByApartmentId(apartmentId);
        List<ApartmentReportRow> content = rows.stream()
                .map(this::toRow)
                .toList();
        return new PageImpl<>(
                content,
                PageRequest.of(page, size),
                total
        );
    }

    public long countByApartmentId(Long apartmentId) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) " + APARTMENT_REPORT_COUNT_FROM)
                .setParameter("apartmentId", apartmentId)
                .getSingleResult();
        return count.longValue();
    }

    public List<ApartmentCountRow> countByApartmentIds(
            Collection<Long> apartmentIds
    ) {
        if (apartmentIds.isEmpty()) {
            return List.of();
        }
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT r.apartment_id, COUNT(*) "
                                + APARTMENT_REPORT_PUBLIC_FROM
                                + " AND r.apartment_id IN (:apartmentIds)"
                                + " GROUP BY r.apartment_id")
                .setParameter("apartmentIds", apartmentIds)
                .getResultList();
        return rows.stream().map(row -> {
            Object[] values = (Object[]) row;
            return new ApartmentCountRow(
                    ((Number) values[0]).longValue(),
                    ((Number) values[1]).longValue()
            );
        }).toList();
    }

    private ApartmentReportRow toRow(Object row) {
        Object[] values = (Object[]) row;
        return new ApartmentReportRow(
                ((Number) values[0]).longValue(),
                (String) values[1],
                toInstant(values[2]),
                Boolean.TRUE.equals(values[3])
        );
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return ((Timestamp) value).toInstant();
    }
}
