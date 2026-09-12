package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.auth.token.RefreshTokenStore;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitStartRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.member.dto.request.MemberWithdrawalRequest;
import com.ssafy.ssabangpalbang.member.service.MemberWithdrawalService;
import com.ssafy.ssabangpalbang.study.service.StudyScheduleService;
import com.ssafy.ssabangpalbang.study.service.port.StudyNotificationPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false",
        "ssabangpalbang.fieldvisit.start.allowed-radius-meters=150"
})
@ActiveProfiles("test")
@Testcontainers
class FieldVisitBe014PostgresE2ETest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = buildContainer();

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

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 6379);
        registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:9092");
    }

    @Autowired
    private FieldVisitStartService startService;

    @Autowired
    private FieldVisitStatusService statusService;

    @Autowired
    private MemberWithdrawalService memberWithdrawalService;

    @Autowired
    private StudyScheduleService studyScheduleService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SttDispatchPort sttDispatchPort;

    @MockitoBean
    private SttAudioDeletionPort sttAudioDeletionPort;

    @MockitoBean
    private GatewayMediaAccessUrlProvider mediaAccessUrlProvider;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @MockitoBean
    private StudyNotificationPort studyNotificationPort;

    private Long studyId;
    private Long leaderId;
    private Long memberId;
    private static final double APT_LAT = 37.5133;
    private static final double APT_LNG = 127.0842;

    @BeforeEach
    void setUp() {
        cleanup();
        seedClosedStudyWithSchedule();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void 최초_시작은_201이고_study가_IN_PROGRESS가_된다() {
        FieldVisitStartService.StartResult result = startService.start(
                studyId, leaderId, body(APT_LAT, APT_LNG, UUID.randomUUID().toString())
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.responseCode())
                .isEqualTo(FieldVisitResponseCode.FIELD_VISIT_START_SUCCESS);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM study WHERE id = ?", String.class, studyId
        );
        assertThat(status).isEqualTo("IN_PROGRESS");
        Integer sessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_session WHERE study_id = ?",
                Integer.class, studyId
        );
        Integer candidates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_candidate fvc "
                        + "JOIN field_session fs ON fs.id = fvc.session_id "
                        + "WHERE fs.study_id = ?",
                Integer.class, studyId
        );
        assertThat(sessions).isEqualTo(1);
        assertThat(candidates).isEqualTo(2);
    }

    @Test
    void 후발_참여자_시작은_201이고_세션은_1건이다() {
        startService.start(
                studyId, leaderId, body(APT_LAT, APT_LNG, UUID.randomUUID().toString())
        );

        FieldVisitStartService.StartResult late = startService.start(
                studyId, memberId, body(APT_LAT, APT_LNG, UUID.randomUUID().toString())
        );

        assertThat(late.httpStatus()).isEqualTo(HttpStatus.CREATED);
        Integer sessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_session WHERE study_id = ?",
                Integer.class, studyId
        );
        Integer participants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_participant fp "
                        + "JOIN field_session fs ON fs.id = fp.session_id "
                        + "WHERE fs.study_id = ?",
                Integer.class, studyId
        );
        assertThat(sessions).isEqualTo(1);
        assertThat(participants).isEqualTo(2);
    }

    @Test
    void 동일_clientRequestId_재요청은_200이다() {
        String clientId = UUID.randomUUID().toString();
        FieldVisitStartService.StartResult first = startService.start(
                studyId, leaderId, body(APT_LAT, APT_LNG, clientId)
        );
        FieldVisitStartService.StartResult second = startService.start(
                studyId, leaderId, body(APT_LAT, APT_LNG, clientId)
        );

        assertThat(first.httpStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(second.body().session().sessionId())
                .isEqualTo(first.body().session().sessionId());
        assertThat(second.body().participant().participantId())
                .isEqualTo(first.body().participant().participantId());
    }

    @Test
    void 반경_밖은_422이고_이력이_없다() {
        assertThatThrownBy(() -> startService.start(
                studyId, leaderId, body(37.5200, APT_LNG, UUID.randomUUID().toString())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_OUT_OF_RANGE);

        Integer sessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_session WHERE study_id = ?",
                Integer.class, studyId
        );
        Integer history = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_start_request WHERE study_id = ?",
                Integer.class, studyId
        );
        assertThat(sessions).isZero();
        assertThat(history).isZero();
    }

    @Test
    void 상태_조회_NOT_STARTED와_후발_canStart를_검증한다() {
        FieldVisitStatusService.StatusResult before =
                statusService.getStatus(studyId, leaderId);
        assertThat(before.body().status()).isEqualTo("NOT_STARTED");
        assertThat(before.body().permissions().canStart()).isTrue();

        startService.start(
                studyId, leaderId, body(APT_LAT, APT_LNG, UUID.randomUUID().toString())
        );

        FieldVisitStatusService.StatusResult leaderStatus =
                statusService.getStatus(studyId, leaderId);
        assertThat(leaderStatus.body().permissions().canStart()).isFalse();
        assertThat(leaderStatus.body().permissions().canFinish()).isTrue();

        FieldVisitStatusService.StatusResult memberStatus =
                statusService.getStatus(studyId, memberId);
        assertThat(memberStatus.body().status()).isEqualTo("IN_PROGRESS");
        assertThat(memberStatus.body().participant()).isNull();
        assertThat(memberStatus.body().permissions().canStart()).isTrue();
    }

    @Test
    void 세션_시작_후_승인된_회원은_고정_후보가_아니므로_참여할_수_없다() {
        startService.start(
                studyId,
                leaderId,
                body(APT_LAT, APT_LNG, UUID.randomUUID().toString())
        );
        Long lateMemberId = insertMember("be014-late");
        jdbcTemplate.update("""
                INSERT INTO study_member (study_id, member_id, role, status)
                VALUES (?, ?, 'MEMBER', 'ACTIVE')
                """, studyId, lateMemberId);

        assertThatThrownBy(() -> startService.start(
                studyId,
                lateMemberId,
                body(APT_LAT, APT_LNG, UUID.randomUUID().toString())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_START_FORBIDDEN);

        FieldVisitStatusService.StatusResult status =
                statusService.getStatus(studyId, lateMemberId);
        assertThat(status.body().participant()).isNull();
        assertThat(status.body().permissions().canStart()).isFalse();
    }

    @Test
    void 동일_회원_동일_clientRequestId_동시_5건은_세션_1_참여자_1이다() throws Exception {
        String clientId = UUID.randomUUID().toString();
        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<FieldVisitStartService.StartResult>> futures = new ArrayList<>();
        AtomicInteger created = new AtomicInteger();
        AtomicInteger already = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                FieldVisitStartService.StartResult result = startService.start(
                        studyId, leaderId, body(APT_LAT, APT_LNG, clientId)
                );
                if (result.httpStatus() == HttpStatus.CREATED) {
                    created.incrementAndGet();
                } else {
                    already.incrementAndGet();
                }
                return result;
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<FieldVisitStartService.StartResult> results = new ArrayList<>();
        for (Future<FieldVisitStartService.StartResult> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(created.get()).isEqualTo(1);
        assertThat(already.get()).isEqualTo(4);
        Long sessionId = results.get(0).body().session().sessionId();
        Long participantId = results.get(0).body().participant().participantId();
        assertThat(results).allMatch(r ->
                r.body().session().sessionId().equals(sessionId)
                        && r.body().participant().participantId().equals(participantId)
        );
        Integer sessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_session WHERE study_id = ?",
                Integer.class, studyId
        );
        Integer participants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_participant fp "
                        + "JOIN field_session fs ON fs.id = fp.session_id "
                        + "WHERE fs.study_id = ? AND fp.member_id = ?",
                Integer.class, studyId, leaderId
        );
        Integer history = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_start_request "
                        + "WHERE member_id = ? AND client_request_id = ?",
                Integer.class, leaderId, clientId
        );
        assertThat(sessions).isEqualTo(1);
        assertThat(participants).isEqualTo(1);
        assertThat(history).isEqualTo(1);
    }

    @Test
    void 동일_회원과_멱등키로_다른_스터디를_동시_시작하면_한쪽은_멱등_충돌이다()
            throws Exception {
        Long otherStudyId = insertAdditionalClosedStudyWithSchedule();
        String clientId = UUID.randomUUID().toString();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<StartAttempt> first = pool.submit(() -> startAttempt(
                studyId, leaderId, clientId, ready, start
        ));
        Future<StartAttempt> second = pool.submit(() -> startAttempt(
                otherStudyId, leaderId, clientId, ready, start
        ));

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<StartAttempt> attempts = List.of(
                first.get(30, TimeUnit.SECONDS),
                second.get(30, TimeUnit.SECONDS)
        );
        pool.shutdown();

        assertThat(attempts)
                .filteredOn(attempt -> attempt.result() != null)
                .hasSize(1);
        assertThat(attempts)
                .extracting(StartAttempt::errorCode)
                .containsExactlyInAnyOrder(
                        null,
                        ErrorCode.FIELD_VISIT_START_IDEMPOTENCY_MISMATCH
                );
        Integer history = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_start_request "
                        + "WHERE member_id = ? AND client_request_id = ?",
                Integer.class, leaderId, clientId
        );
        assertThat(history).isEqualTo(1);
    }

    @Test
    void 후보_명단이_겹치는_다른_스터디를_서로_다른_회원이_동시_시작해도_데드락이_없다()
            throws Exception {
        Long otherStudyId = insertAdditionalClosedStudyWithSchedule();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<FieldVisitStartService.StartResult> first = pool.submit(() -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return startService.start(
                    studyId,
                    leaderId,
                    body(APT_LAT, APT_LNG, UUID.randomUUID().toString())
            );
        });
        Future<FieldVisitStartService.StartResult> second = pool.submit(() -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return startService.start(
                    otherStudyId,
                    memberId,
                    body(APT_LAT, APT_LNG, UUID.randomUUID().toString())
            );
        });

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        FieldVisitStartService.StartResult firstResult =
                first.get(30, TimeUnit.SECONDS);
        FieldVisitStartService.StartResult secondResult =
                second.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(firstResult.httpStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondResult.httpStatus()).isEqualTo(HttpStatus.CREATED);
        Integer sessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_session WHERE study_id IN (?, ?)",
                Integer.class, studyId, otherStudyId
        );
        Integer candidates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_candidate fvc "
                        + "JOIN field_session fs ON fs.id = fvc.session_id "
                        + "WHERE fs.study_id IN (?, ?)",
                Integer.class, studyId, otherStudyId
        );
        assertThat(sessions).isEqualTo(2);
        assertThat(candidates).isEqualTo(4);
    }

    @Test
    void 임장_시작과_회원_탈퇴가_동시에_실행돼도_모순_상태가_남지_않는다()
            throws Exception {
        String clientId = UUID.randomUUID().toString();
        String refreshToken = jwtTokenProvider.issue(memberId).refreshToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<StartAttempt> startFuture = pool.submit(() -> startAttempt(
                studyId, memberId, clientId, ready, start
        ));
        Future<WithdrawalAttempt> withdrawalFuture = pool.submit(() -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try {
                memberWithdrawalService.withdraw(
                        memberId,
                        new MemberWithdrawalRequest(refreshToken, "회원탈퇴")
                );
                return new WithdrawalAttempt(true, null);
            } catch (BusinessException exception) {
                return new WithdrawalAttempt(false, exception.getErrorCode());
            }
        });

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        StartAttempt startAttempt = startFuture.get(30, TimeUnit.SECONDS);
        WithdrawalAttempt withdrawalAttempt =
                withdrawalFuture.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        String memberStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM member WHERE id = ?",
                String.class,
                memberId
        );
        String membershipStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM study_member "
                        + "WHERE study_id = ? AND member_id = ?",
                String.class,
                studyId,
                memberId
        );
        Integer participants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_participant fp "
                        + "JOIN field_session fs ON fs.id = fp.session_id "
                        + "WHERE fs.study_id = ? AND fp.member_id = ?",
                Integer.class,
                studyId,
                memberId
        );

        if (startAttempt.result() != null) {
            assertThat(withdrawalAttempt.success()).isFalse();
            assertThat(withdrawalAttempt.errorCode()).isEqualTo(
                    ErrorCode.MEMBER_WITHDRAWAL_FIELD_SESSION_IN_PROGRESS
            );
            assertThat(memberStatus).isEqualTo("ACTIVE");
            assertThat(membershipStatus).isEqualTo("ACTIVE");
            assertThat(participants).isEqualTo(1);
        } else {
            assertThat(startAttempt.errorCode())
                    .isEqualTo(ErrorCode.FIELD_VISIT_START_FORBIDDEN);
            assertThat(withdrawalAttempt.success()).isTrue();
            assertThat(memberStatus).isEqualTo("WITHDRAWN");
            assertThat(membershipStatus).isEqualTo("REMOVED");
            assertThat(participants).isZero();
        }
    }

    @Test
    void 임장_최초_시작과_일정_취소가_동시에_실행돼도_모순_상태가_남지_않는다()
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<StartAttempt> startFuture = pool.submit(() -> startAttempt(
                studyId,
                leaderId,
                UUID.randomUUID().toString(),
                ready,
                start
        ));
        Future<ScheduleDeleteAttempt> deleteFuture = pool.submit(() -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try {
                studyScheduleService.deleteSchedule(leaderId, studyId);
                return new ScheduleDeleteAttempt(true, null);
            } catch (BusinessException exception) {
                return new ScheduleDeleteAttempt(false, exception.getErrorCode());
            }
        });

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        StartAttempt startAttempt = startFuture.get(30, TimeUnit.SECONDS);
        ScheduleDeleteAttempt deleteAttempt = deleteFuture.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        String studyStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM study WHERE id = ?",
                String.class,
                studyId
        );
        String scheduleStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM schedule WHERE study_id = ?",
                String.class,
                studyId
        );
        Integer sessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_session WHERE study_id = ?",
                Integer.class,
                studyId
        );

        if (startAttempt.result() != null) {
            assertThat(deleteAttempt.success()).isFalse();
            assertThat(deleteAttempt.errorCode())
                    .isEqualTo(ErrorCode.STUDY_SCHEDULE_DELETE_NOT_ALLOWED);
            assertThat(studyStatus).isEqualTo("IN_PROGRESS");
            assertThat(scheduleStatus).isEqualTo("SCHEDULED");
            assertThat(sessions).isEqualTo(1);
        } else {
            assertThat(startAttempt.errorCode())
                    .isEqualTo(ErrorCode.FIELD_VISIT_START_FORBIDDEN);
            assertThat(deleteAttempt.success()).isTrue();
            assertThat(studyStatus).isEqualTo("CLOSED");
            assertThat(scheduleStatus).isEqualTo("CANCELED");
            assertThat(sessions).isZero();
        }
    }

    @Test
    void 서로_다른_회원_동시_최초_시작은_세션_1건이다() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<FieldVisitStartService.StartResult> f1 = pool.submit(() -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return startService.start(
                    studyId, leaderId, body(APT_LAT, APT_LNG, UUID.randomUUID().toString())
            );
        });
        Future<FieldVisitStartService.StartResult> f2 = pool.submit(() -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return startService.start(
                    studyId, memberId, body(APT_LAT, APT_LNG, UUID.randomUUID().toString())
            );
        });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        FieldVisitStartService.StartResult r1 = f1.get(30, TimeUnit.SECONDS);
        FieldVisitStartService.StartResult r2 = f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(r1.body().session().sessionId())
                .isEqualTo(r2.body().session().sessionId());
        assertThat(r1.body().participant().participantId())
                .isNotEqualTo(r2.body().participant().participantId());
        Integer sessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_session WHERE study_id = ?",
                Integer.class, studyId
        );
        assertThat(sessions).isEqualTo(1);
        String studyStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM study WHERE id = ?", String.class, studyId
        );
        assertThat(studyStatus).isEqualTo("IN_PROGRESS");
    }

    private FieldVisitStartRequestBody body(double lat, double lng, String clientId) {
        return new FieldVisitStartRequestBody(lat, lng, clientId);
    }

    private StartAttempt startAttempt(
            Long targetStudyId,
            Long actorId,
            String clientId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        try {
            return new StartAttempt(
                    startService.start(
                            targetStudyId,
                            actorId,
                            body(APT_LAT, APT_LNG, clientId)
                    ),
                    null
            );
        } catch (BusinessException exception) {
            return new StartAttempt(null, exception.getErrorCode());
        }
    }

    private Long insertAdditionalClosedStudyWithSchedule() {
        Long apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE014-APT', ?, ?) RETURNING id
                """, Long.class, "BE014-" + UUID.randomUUID(), APT_LNG, APT_LAT);
        Long otherStudyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (apartment_id, leader_id, goal, capacity, title, status)
                VALUES (?, ?, 'be014-goal', 5, 'be014-study', 'CLOSED') RETURNING id
                """, Long.class, apartmentId, leaderId);
        jdbcTemplate.update("""
                INSERT INTO study_member (study_id, member_id, role, status)
                VALUES (?, ?, 'LEADER', 'ACTIVE'), (?, ?, 'MEMBER', 'ACTIVE')
                """, otherStudyId, leaderId, otherStudyId, memberId);
        Instant startAt = Instant.now().minusSeconds(60);
        jdbcTemplate.update("""
                INSERT INTO schedule (study_id, start_at, end_at, meeting_place, status)
                VALUES (?, ?, ?, 'gate', 'SCHEDULED')
                """, otherStudyId, java.sql.Timestamp.from(startAt),
                java.sql.Timestamp.from(startAt.plusSeconds(7200)));
        return otherStudyId;
    }

    private void seedClosedStudyWithSchedule() {
        Long apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE014-APT', ?, ?) RETURNING id
                """, Long.class, "BE014-" + UUID.randomUUID(), APT_LNG, APT_LAT);
        leaderId = insertMember("be014-leader");
        memberId = insertMember("be014-member");
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (apartment_id, leader_id, goal, capacity, title, status)
                VALUES (?, ?, 'be014-goal', 5, 'be014-study', 'CLOSED') RETURNING id
                """, Long.class, apartmentId, leaderId);
        jdbcTemplate.update("""
                INSERT INTO study_member (study_id, member_id, role, status)
                VALUES (?, ?, 'LEADER', 'ACTIVE'), (?, ?, 'MEMBER', 'ACTIVE')
                """, studyId, leaderId, studyId, memberId);
        Instant startAt = Instant.now().minusSeconds(60);
        jdbcTemplate.update("""
                INSERT INTO schedule (study_id, start_at, end_at, meeting_place, status)
                VALUES (?, ?, ?, 'gate', 'SCHEDULED')
                """, studyId, java.sql.Timestamp.from(startAt),
                java.sql.Timestamp.from(startAt.plusSeconds(7200)));
    }

    private Long insertMember(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (
                    email, nickname, age_group_public_agreed,
                    service_notification_agreed, ad_notification_agreed
                ) VALUES (?, ?, false, true, false) RETURNING id
                """, Long.class, prefix + "-" + suffix + "@test.local", prefix + suffix);
    }

    private void cleanup() {
        jdbcTemplate.update("""
                DELETE FROM notification
                WHERE recipient_id IN (
                    SELECT id FROM member
                    WHERE email LIKE 'be014-%@test.local'
                )
                   OR actor_id IN (
                    SELECT id FROM member
                    WHERE email LIKE 'be014-%@test.local'
                )
                """);
        jdbcTemplate.update("DELETE FROM field_visit_start_request");
        jdbcTemplate.update("DELETE FROM field_record");
        jdbcTemplate.update("DELETE FROM checklist_answer");
        jdbcTemplate.update("DELETE FROM checklist_item");
        jdbcTemplate.update("DELETE FROM checklist");
        jdbcTemplate.update("DELETE FROM field_participant");
        jdbcTemplate.update("DELETE FROM field_visit_candidate");
        jdbcTemplate.update("DELETE FROM field_session");
        jdbcTemplate.update("DELETE FROM schedule WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be014-study')");
        jdbcTemplate.update("DELETE FROM study_member WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be014-study')");
        jdbcTemplate.update("DELETE FROM study WHERE title = 'be014-study'");
        jdbcTemplate.update("DELETE FROM apartment WHERE name = 'BE014-APT'");
        jdbcTemplate.update("DELETE FROM member WHERE email LIKE 'be014-%@test.local'");
    }

    private record StartAttempt(
            FieldVisitStartService.StartResult result,
            ErrorCode errorCode
    ) {
    }

    private record WithdrawalAttempt(boolean success, ErrorCode errorCode) {
    }

    private record ScheduleDeleteAttempt(boolean success, ErrorCode errorCode) {
    }
}
