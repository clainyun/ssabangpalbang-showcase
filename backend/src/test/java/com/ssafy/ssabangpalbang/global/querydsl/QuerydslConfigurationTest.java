package com.ssafy.ssabangpalbang.global.querydsl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.ssabangpalbang.apartment.domain.QApartment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfiguration.class)
@Sql(statements = {
        "DROP TABLE IF EXISTS apartment",
        "CREATE TABLE apartment ("
                + "id BIGINT PRIMARY KEY,"
                + "complex_code VARCHAR(50) NOT NULL,"
                + "name VARCHAR(200) NOT NULL,"
                + "address VARCHAR(300),"
                + "district_code VARCHAR(10),"
                + "district_name VARCHAR(50),"
                + "dong_name VARCHAR(50),"
                + "legal_dong_code VARCHAR(20),"
                + "longitude DOUBLE PRECISION NOT NULL,"
                + "latitude DOUBLE PRECISION NOT NULL,"
                + "household_count INT,"
                + "completion_year_month VARCHAR(7),"
                + "parking_space_count INT,"
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL,"
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)"
})
class QuerydslConfigurationTest {

    private static final QApartment APARTMENT = QApartment.apartment;

    @Autowired
    private JPAQueryFactory queryFactory;

    @Test
    void JPAQueryFactory_빈을_주입한다() {
        assertThat(queryFactory).isNotNull();
    }

    @Test
    void Q타입으로_빈_아파트_ID_목록을_조회한다() {
        List<Long> apartmentIds = queryFactory
                .select(APARTMENT.id)
                .from(APARTMENT)
                .fetch();

        assertThat(apartmentIds).isEmpty();
    }

    @Test
    void Q타입의_지역_코드_조건으로_빈_목록을_조회한다() {
        List<Long> apartmentIds = queryFactory
                .select(APARTMENT.id)
                .from(APARTMENT)
                .where(APARTMENT.districtCode.eq("11680"))
                .fetch();

        assertThat(apartmentIds).isEmpty();
    }
}
