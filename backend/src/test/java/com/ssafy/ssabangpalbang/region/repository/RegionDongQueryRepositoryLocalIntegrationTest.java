package com.ssafy.ssabangpalbang.region.repository;

import com.ssafy.ssabangpalbang.quality.Be033PostgresContainerFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RegionDongQueryRepositoryLocalIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            Be033PostgresContainerFactory.create();

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private RegionDongQueryRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void 동을_집계하고_이름과_코드순으로_정렬하며_null_코드를_제외한다() {
        String districtCode = "11998";
        insertApartment(districtCode, "1199810300", "나동");
        insertApartment(districtCode, "1199810200", "가동");
        insertApartment(districtCode, "1199810100", "가동");
        insertApartment(districtCode, "1199810100", "가동");
        insertApartment(districtCode, null, "제외동");

        var rows = repository.findByDistrictCode(districtCode);

        assertThat(rows).hasSize(3);
        assertThat(rows)
                .extracting(RegionDongRow::dongCode)
                .containsExactly("1199810100", "1199810200", "1199810300");
        assertThat(rows.get(0).apartmentCount()).isEqualTo(2L);
        assertThat(repository.findByDistrictCode("11997")).isEmpty();
    }

    private void insertApartment(
            String districtCode,
            String dongCode,
            String dongName
    ) {
        entityManager.createNativeQuery("""
                        INSERT INTO apartment (
                            complex_code, name, address, district_code, district_name,
                            dong_name, legal_dong_code, longitude, latitude
                        )
                        VALUES (
                            :complexCode, '지역 테스트', '테스트 주소', :districtCode, '테스트구',
                            :dongName, :dongCode, 127.0, 37.5
                        )
                        """)
                .setParameter("complexCode", UUID.randomUUID().toString())
                .setParameter("districtCode", districtCode)
                .setParameter("dongName", dongName)
                .setParameter("dongCode", dongCode)
                .executeUpdate();
    }
}
