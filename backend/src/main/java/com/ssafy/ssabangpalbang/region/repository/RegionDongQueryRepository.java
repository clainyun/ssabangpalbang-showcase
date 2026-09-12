package com.ssafy.ssabangpalbang.region.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.ssabangpalbang.apartment.domain.QApartment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RegionDongQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<RegionDongRow> findByDistrictCode(String districtCode) {
        QApartment apartment = QApartment.apartment;
        return queryFactory
                .select(Projections.constructor(
                        RegionDongRow.class,
                        apartment.legalDongCode,
                        apartment.dongName,
                        apartment.count()
                ))
                .from(apartment)
                .where(
                        apartment.districtCode.eq(districtCode),
                        apartment.legalDongCode.isNotNull(),
                        apartment.dongName.isNotNull()
                )
                .groupBy(apartment.legalDongCode, apartment.dongName)
                .orderBy(apartment.dongName.asc(), apartment.legalDongCode.asc())
                .fetch();
    }
}
