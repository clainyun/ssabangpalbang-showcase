package com.ssafy.ssabangpalbang.apartment.repository;

import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentSearchCondition;
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
class ApartmentSearchQueryRepositoryLocalIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            Be033PostgresContainerFactory.create();

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry, POSTGRES);
    }

    @Autowired
    private ApartmentSearchQueryRepository repository;

    @Autowired
    private EntityManager entityManager;

    private static void registerPostgresProperties(
            DynamicPropertyRegistry registry,
            PostgreSQLContainer<?> postgres
    ) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    @Transactional
    void 주변검색은_반경내_정확한_건수와_목록을_거리순으로_중복없이_반환한다() {
        String keyword = "CodexRadius" + UUID.randomUUID().toString().replace("-", "");
        insertApartment(keyword + "A", 37.4979, 127.0276);
        insertApartment(keyword + "B", 37.5010, 127.0276);
        insertApartment(keyword + "C", 37.5050, 127.0276);
        insertApartment(keyword + "OUT", 37.5200, 127.0276);

        var page = repository.search(ApartmentSearchCondition.of(
                keyword.toLowerCase(), null, null,
                37.4979, 127.0276, 1000, 0, 100));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent())
                .extracting(ApartmentSearchRow::apartmentId)
                .doesNotHaveDuplicates();
        assertThat(page.getContent())
                .extracting(ApartmentSearchRow::distanceMeters)
                .isSorted();
        assertThat(page.getContent())
                .extracting(ApartmentSearchRow::name)
                .containsExactly(keyword + "A", keyword + "B", keyword + "C");
    }

    @Test
    @Transactional
    void 키워드_검색은_영문_대소문자를_구분하지_않는다() {
        String keyword = "CaseSensitive" + UUID.randomUUID().toString().replace("-", "");
        insertApartment(keyword, 37.5, 127.0);

        var page = repository.search(ApartmentSearchCondition.of(
                keyword.toLowerCase(), null, null,
                null, null, null, 0, 20
        ));

        assertThat(page.getContent())
                .extracting(ApartmentSearchRow::name)
                .containsExactly(keyword);
    }

    private Long insertApartment(String name, double latitude, double longitude) {
        return ((Number) entityManager.createNativeQuery("""
                        INSERT INTO apartment (
                            complex_code, name, address, district_code, district_name,
                            dong_name, legal_dong_code, longitude, latitude
                        )
                        VALUES (
                            :complexCode, :name, '테스트 주소', '11680', '강남구',
                            '역삼동', '1168010100', :longitude, :latitude
                        )
                        RETURNING id
                        """)
                .setParameter("complexCode", UUID.randomUUID().toString())
                .setParameter("name", name)
                .setParameter("longitude", longitude)
                .setParameter("latitude", latitude)
                .getSingleResult()).longValue();
    }
}
