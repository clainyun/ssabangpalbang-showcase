package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRoute;
import com.ssafy.ssabangpalbang.fieldvisit.service.RouteCandidate;
import com.ssafy.ssabangpalbang.fieldvisit.service.RouteWriter;
import com.ssafy.ssabangpalbang.fieldvisit.service.WalkingRoutePlan;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** V16/V17 경로 스키마의 UNIQUE, cascade, nullable JSONB를 PostgreSQL에서 검증한다. */
@Tag("postgres")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(RouteWriter.class)
class FieldVisitRoutePostgresConstraintTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FieldVisitRouteRepository routeRepository;
    @Autowired
    private RouteWriter routeWriter;
    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long sessionId;
    private Long memberId;
    private Long checklistItemId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, '경로 테스트 단지', 127.0, 37.5) RETURNING id
                """, Long.class, "ROUTE-" + suffix);
        memberId = jdbcTemplate.queryForObject("""
                INSERT INTO member (email, nickname, age_group_public_agreed,
                                    service_notification_agreed, ad_notification_agreed)
                VALUES (?, ?, false, true, false) RETURNING id
                """, Long.class, "route-" + suffix + "@example.com", "route-" + suffix);
        Long studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (apartment_id, leader_id, goal, capacity)
                VALUES (?, ?, 'route goal', 5) RETURNING id
                """, Long.class, apartmentId, memberId);
        sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO field_session (study_id, status)
                VALUES (?, 'IN_PROGRESS') RETURNING id
                """, Long.class, studyId);
        Long checklistId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist (session_id, member_id, is_fallback)
                VALUES (?, ?, false) RETURNING id
                """, Long.class, sessionId, memberId);
        checklistItemId = jdbcTemplate.queryForObject("""
                INSERT INTO checklist_item (checklist_id, category, title, display_order)
                VALUES (?, '교통', '역 접근성', 1) RETURNING id
                """, Long.class, checklistId);
    }

    @Test
    void 세션당_추천_경로는_하나만_저장된다() {
        insertRoute();

        assertThatThrownBy(this::insertRoute).isInstanceOf(DataAccessException.class);
    }

    @Test
    void V16_경로의_geometry는_null을_허용하고_LineString_JSONB로_갱신된다() {
        Long routeId = insertRoute();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT geometry IS NULL FROM field_visit_route WHERE id = ?",
                Boolean.class,
                routeId
        )).isTrue();

        jdbcTemplate.update(
                "UPDATE field_visit_route SET geometry = CAST(? AS jsonb) WHERE id = ?",
                "{\"type\":\"LineString\",\"coordinates\":[[127.0,37.5],[127.1,37.6]]}",
                routeId
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT geometry ->> 'type' FROM field_visit_route WHERE id = ?",
                String.class,
                routeId
        )).isEqualTo("LineString");
    }

    @Test
    void LineString_JSONB는_JPA_엔티티로_저장하고_다시_읽는다() throws Exception {
        JsonNode geometry = objectMapper.readTree(
                "{\"type\":\"LineString\",\"coordinates\":[[127.0,37.5],[127.1,37.6]]}"
        );
        FieldVisitRoute saved = routeRepository.saveAndFlush(FieldVisitRoute.create(
                sessionId,
                memberId,
                120,
                12,
                geometry
        ));
        entityManager.clear();

        FieldVisitRoute reloaded = routeRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getGeometry()).isEqualTo(geometry);
        assertThat(reloaded.getTotalDistanceMeters()).isEqualTo(120);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void V16_경로_교체는_기존_waypoint와_link를_지우고_새_경로를_원자_저장한다()
            throws Exception {
        Long routeId = insertRoute();
        Long oldWaypointId = jdbcTemplate.queryForObject("""
                INSERT INTO field_visit_route_waypoint (
                    route_id, sequence, facility_type, name, latitude, longitude
                ) VALUES (?, 1, 'HOSPITAL', '기존 동물병원', 37.51, 127.01) RETURNING id
                """, Long.class, routeId);
        jdbcTemplate.update("""
                INSERT INTO field_visit_route_waypoint_item (waypoint_id, checklist_item_id)
                VALUES (?, ?)
                """, oldWaypointId, checklistItemId);
        JsonNode geometry = objectMapper.readTree(
                "{\"type\":\"LineString\",\"coordinates\":[[127.0,37.5],[127.2,37.7]]}"
        );
        ChecklistItem item = mock(ChecklistItem.class);
        when(item.getId()).thenReturn(checklistItemId);
        RouteCandidate first = candidate(
                FacilityType.CONVENIENCE_STORE, "store", "새 편의점", 37.5001, 127.0001,
                item
        );
        RouteCandidate second = candidate(
                FacilityType.HOSPITAL, "hospital", "새 내과", 37.51, 127.02, item
        );
        WalkingRoutePlan plan = new WalkingRoutePlan(
                List.of(
                        new WalkingRoutePlan.Waypoint(first, 20, 25, 1),
                        new WalkingRoutePlan.Waypoint(second, 150, 180, 3)
                ),
                205,
                17,
                geometry
        );

        FieldVisitRoute replaced = routeWriter.replaceLegacy(routeId, plan);

        assertThat(replaced.getGeometry()).isEqualTo(geometry);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_route_waypoint WHERE id = ?",
                Integer.class,
                oldWaypointId
        )).isZero();
        assertThat(jdbcTemplate.queryForList(
                "SELECT name FROM field_visit_route_waypoint WHERE route_id = ? ORDER BY sequence",
                String.class,
                routeId
        )).containsExactly("새 편의점", "새 내과");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_route_waypoint_item wi "
                        + "JOIN field_visit_route_waypoint w ON w.id = wi.waypoint_id "
                        + "WHERE w.route_id = ?",
                Integer.class,
                routeId
        )).isEqualTo(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 동시_저장도_세션당_한_경로만_성공한다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> insertConcurrently(start));
            Future<Boolean> second = pool.submit(() -> insertConcurrently(start));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM field_visit_route WHERE session_id = ?",
                    Integer.class,
                    sessionId
            )).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 경로_삭제시_경유지와_항목_연결도_함께_삭제된다() {
        Long routeId = insertRoute();
        Long waypointId = jdbcTemplate.queryForObject("""
                INSERT INTO field_visit_route_waypoint (
                    route_id, sequence, facility_type, name, latitude, longitude
                ) VALUES (?, 1, 'MART', '테스트 마트', 37.51, 127.01) RETURNING id
                """, Long.class, routeId);
        jdbcTemplate.update("""
                INSERT INTO field_visit_route_waypoint_item (waypoint_id, checklist_item_id)
                VALUES (?, ?)
                """, waypointId, checklistItemId);

        jdbcTemplate.update("DELETE FROM field_visit_route WHERE id = ?", routeId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_route_waypoint WHERE route_id = ?",
                Integer.class, routeId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_route_waypoint_item WHERE waypoint_id = ?",
                Integer.class, waypointId
        )).isZero();
    }

    private Long insertRoute() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO field_visit_route (
                    session_id, generated_by_id, total_distance_meters,
                    estimated_duration_minutes
                ) VALUES (?, ?, 100, 10) RETURNING id
                """, Long.class, sessionId, memberId);
    }

    private boolean insertConcurrently(CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            insertRoute();
            return true;
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private static RouteCandidate candidate(
            FacilityType type,
            String id,
            String name,
            double latitude,
            double longitude,
            ChecklistItem item
    ) {
        return new RouteCandidate(
                type,
                new KakaoLocalPoi(id, name, "", null, latitude, longitude, 0),
                List.of(item),
                0
        );
    }

    private static PostgreSQLContainer<?> buildContainer() {
        ImageFromDockerfile image = new ImageFromDockerfile(
                "ssabangpalbang-postgres-test", false
        ).withDockerfile(Path.of("..", "infra", "postgres", "Dockerfile"));
        String imageId = image.get();
        return new PostgreSQLContainer<>(
                DockerImageName.parse(imageId).asCompatibleSubstituteFor("postgres")
        )
                .withDatabaseName("ssabangpalbang_test")
                .withUsername("test")
                .withPassword("test");
    }
}
