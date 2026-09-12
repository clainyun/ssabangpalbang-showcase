package com.ssafy.ssabangpalbang.apartment.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.ssabangpalbang.apartment.domain.QApartment;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentSearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ApartmentSearchQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    public Page<ApartmentSearchRow> search(ApartmentSearchCondition condition) {
        if (condition.mode().name().equals("NEARBY")) {
            return searchNearby(condition);
        }
        QApartment apartment = QApartment.apartment;
        BooleanBuilder where = new BooleanBuilder();
        if (condition.keyword() != null) {
            String keywordPattern = "%" + condition.keyword() + "%";
            where.and(Expressions.booleanTemplate(
                            "{0} ilike {1}", apartment.name, keywordPattern)
                    .or(Expressions.booleanTemplate(
                            "{0} ilike {1}", apartment.dongName, keywordPattern))
                    .or(Expressions.booleanTemplate(
                            "{0} ilike {1}", apartment.address, keywordPattern)));
        }
        if (condition.districtCode() != null) {
            where.and(apartment.districtCode.eq(condition.districtCode()));
        }
        if (condition.dongCode() != null) {
            where.and(apartment.legalDongCode.eq(condition.dongCode()));
        }
        List<ApartmentSearchRow> content = queryFactory
                .select(Projections.constructor(ApartmentSearchRow.class,
                        apartment.id, apartment.name, apartment.address,
                        apartment.districtName, apartment.dongName,
                        apartment.latitude, apartment.longitude,
                        Expressions.nullExpression(Integer.class)))
                .from(apartment).where(where)
                .orderBy(apartment.name.asc(), apartment.id.asc())
                .offset((long) condition.page() * condition.size()).limit(condition.size()).fetch();
        Long count = queryFactory.select(apartment.count()).from(apartment).where(where).fetchOne();
        return new PageImpl<>(content, PageRequest.of(condition.page(), condition.size()),
                count == null ? 0 : count);
    }

    @SuppressWarnings("unchecked")
    private Page<ApartmentSearchRow> searchNearby(ApartmentSearchCondition condition) {
        String filters = """
                FROM apartment a
                WHERE ST_DWithin(
                  (ST_SetSRID(ST_MakePoint(a.longitude, a.latitude), 4326))::geography,
                  (ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))::geography, :radius)
                AND (CAST(:keyword AS text) IS NULL
                     OR a.name ILIKE CONCAT('%', CAST(:keyword AS text), '%')
                     OR a.dong_name ILIKE CONCAT('%', CAST(:keyword AS text), '%')
                     OR a.address ILIKE CONCAT('%', CAST(:keyword AS text), '%'))
                AND (CAST(:district AS text) IS NULL
                     OR a.district_code = CAST(:district AS text))
                AND (CAST(:dong AS text) IS NULL
                     OR a.legal_dong_code = CAST(:dong AS text))
                """;
        Query query = bind(entityManager.createNativeQuery("""
                SELECT a.id, a.name, a.address, a.district_name, a.dong_name,
                       a.latitude, a.longitude,
                       CAST(ROUND(ST_Distance(
                         (ST_SetSRID(ST_MakePoint(a.longitude, a.latitude), 4326))::geography,
                         (ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))::geography
                       )) AS INTEGER) distance_meters
                """ + filters + """
                ORDER BY (ST_SetSRID(ST_MakePoint(a.longitude, a.latitude), 4326))::geography
                         <-> (ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))::geography,
                         a.id ASC
                LIMIT :limit OFFSET :offset
                """), condition);
        query.setParameter("limit", condition.size());
        query.setParameter("offset", condition.page() * condition.size());
        List<Object[]> rows = query.getResultList();
        List<ApartmentSearchRow> content = rows.stream().map(row -> new ApartmentSearchRow(
                ((Number) row[0]).longValue(), (String) row[1], (String) row[2],
                (String) row[3], (String) row[4],
                // ST_DWithin 이 좌표 없는 행을 걸러 내므로 여기서는 null 이 올 수 없지만,
                // 컬럼이 nullable 이라 방어적으로 확인합니다.
                row[5] == null ? null : ((Number) row[5]).doubleValue(),
                row[6] == null ? null : ((Number) row[6]).doubleValue(),
                ((Number) row[7]).intValue()
        )).toList();
        Number total = (Number) bind(entityManager.createNativeQuery(
                "SELECT COUNT(*) " + filters), condition).getSingleResult();
        return new PageImpl<>(content, PageRequest.of(condition.page(), condition.size()),
                total.longValue());
    }

    private Query bind(Query query, ApartmentSearchCondition condition) {
        return query.setParameter("lng", condition.longitude())
                .setParameter("lat", condition.latitude())
                .setParameter("radius", condition.radiusMeters())
                .setParameter("keyword", condition.keyword())
                .setParameter("district", condition.districtCode())
                .setParameter("dong", condition.dongCode());
    }
}
