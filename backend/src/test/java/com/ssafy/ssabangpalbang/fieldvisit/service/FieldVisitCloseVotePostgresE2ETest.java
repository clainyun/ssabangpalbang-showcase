package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitCloseRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitFinishRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitStartRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.integration.ReportRequestPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttAudioDeletionPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.GatewayMediaAccessUrlProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("postgres")
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
class FieldVisitCloseVotePostgresE2ETest {

    private static final double APT_LAT = 37.5;
    private static final double APT_LNG = 127.0;

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
    private FieldVisitCloseVoteService closeVoteService;

    @Autowired
    private FieldVisitCloseService closeService;

    @Autowired
    private FieldVisitFinishService finishService;

    @Autowired
    private FieldVisitStartService startService;

    @Autowired
    private FieldVisitStatusService statusService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ReportRequestPort reportRequestPort;

    @MockitoBean
    private SttDispatchPort sttDispatchPort;

    @MockitoBean
    private SttAudioDeletionPort sttAudioDeletionPort;

    @MockitoBean
    private GatewayMediaAccessUrlProvider mediaAccessUrlProvider;

    private Long studyId;
    private Long sessionId;
    private Long apartmentId;
    private Long leaderId;
    private List<Long> memberIds;
    private Long lateMemberId;

    @BeforeEach
    void setUp() {
        cleanup();
        clearInvocations(reportRequestPort);
        when(mediaAccessUrlProvider.issueAll(anyCollection()))
                .thenReturn(java.util.Map.of());
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void 불참_후보와_비멤버는_투표할_수_없다() {
        seed(2, true, false);

        Long outsider = insertMember("be0181-outsider");
        assertThatThrownBy(() -> closeVoteService.vote(studyId, outsider))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_CLOSE_VOTE_FORBIDDEN);

        Long candidateOnly = insertMember("be0181-candidate");
        jdbcTemplate.update("""
                INSERT INTO study_member (study_id, member_id, role, status)
                VALUES (?, ?, 'MEMBER', 'ACTIVE')
                """, studyId, candidateOnly);
        jdbcTemplate.update("""
                INSERT INTO field_visit_candidate (session_id, member_id)
                VALUES (?, ?)
                """, sessionId, candidateOnly);

        assertThatThrownBy(() -> closeVoteService.vote(studyId, candidateOnly))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_CLOSE_VOTE_FORBIDDEN);

        Integer voteRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_close_vote WHERE field_session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(voteRows).isZero();
    }

    @Test
    void candidate만_있는_불참자는_분모에서_제외되고_1표로_종료된다() {
        seed(1, false, false);

        Long candidateB = insertMember("be0181-cand-b");
        Long candidateC = insertMember("be0181-cand-c");
        for (Long candidateId : List.of(candidateB, candidateC)) {
            jdbcTemplate.update("""
                    INSERT INTO study_member (study_id, member_id, role, status)
                    VALUES (?, ?, 'MEMBER', 'ACTIVE')
                    """, studyId, candidateId);
        }
        for (Long memberId : List.of(memberIds.get(0), candidateB, candidateC)) {
            jdbcTemplate.update("""
                    INSERT INTO field_visit_candidate (session_id, member_id)
                    VALUES (?, ?)
                    """, sessionId, memberId);
        }

        Integer candidateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_candidate WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        Integer participantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_participant WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(candidateCount).isEqualTo(3);
        assertThat(participantCount).isEqualTo(1);

        FieldVisitCloseVoteService.VoteResult result =
                closeVoteService.vote(studyId, memberIds.get(0));

        assertThat(result.body().startedParticipantCount()).isEqualTo(1);
        assertThat(result.body().requiredVoteCount()).isEqualTo(1);
        assertThat(result.body().voteCount()).isEqualTo(1);
        assertThat(result.body().sessionEnded()).isTrue();
        assertThat(result.body().sessionEndReason()).isEqualTo("MAJORITY_FORCED");
        assertThat(sessionStatus()).isEqualTo("ENDED");
        assertThat(sessionEndReason()).isEqualTo("MAJORITY_FORCED");
        verify(reportRequestPort, times(1)).request(any());
    }

    @Test
    void 시작_참여자_3명_중_2표면_MAJORITY_FORCED로_종료된다() {
        seed(3, false, false);

        FieldVisitCloseVoteService.VoteResult first =
                closeVoteService.vote(studyId, memberIds.get(0));
        assertThat(first.body().sessionEnded()).isFalse();
        assertThat(first.body().requiredVoteCount()).isEqualTo(2);

        FieldVisitCloseVoteService.VoteResult second =
                closeVoteService.vote(studyId, memberIds.get(1));
        assertThat(second.body().sessionEnded()).isTrue();
        assertThat(second.body().sessionEndReason()).isEqualTo("MAJORITY_FORCED");
        assertThat(second.body().reportTriggered()).isTrue();

        assertThat(sessionStatus()).isEqualTo("ENDED");
        assertThat(sessionEndReason()).isEqualTo("MAJORITY_FORCED");
        Integer ended = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_participant "
                        + "WHERE session_id = ? AND status = 'ENDED'",
                Integer.class,
                sessionId
        );
        assertThat(ended).isEqualTo(3);
        verify(reportRequestPort, times(1)).request(any());
    }

    @Test
    void ENDED_참여자도_세션_진행중이면_투표하고_분모에_포함된다() {
        seed(2, true, false);

        FieldVisitCloseVoteService.VoteResult result =
                closeVoteService.vote(studyId, leaderId);
        assertThat(result.body().startedParticipantCount()).isEqualTo(2);
        assertThat(result.body().voteCount()).isEqualTo(1);
        assertThat(result.body().requiredVoteCount()).isEqualTo(2);
        assertThat(result.body().sessionEnded()).isFalse();
        assertThat(result.body().hasVoted()).isTrue();
    }

    @Test
    void 동일_사용자_중복_투표는_한_표만_저장한다() {
        seed(2, false, false);

        closeVoteService.vote(studyId, memberIds.get(0));
        FieldVisitCloseVoteService.VoteResult again =
                closeVoteService.vote(studyId, memberIds.get(0));

        assertThat(again.body().voteCount()).isEqualTo(1);
        Integer voteRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_close_vote WHERE field_session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(voteRows).isEqualTo(1);
        assertThat(sessionStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void 동일_사용자_동시_중복_투표도_한_표로_수렴한다() throws Exception {
        seed(3, false, false);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<FieldVisitCloseVoteService.VoteResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    await(ready, start);
                    return closeVoteService.vote(studyId, memberIds.get(0));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<FieldVisitCloseVoteService.VoteResult> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS).body().voteCount())
                        .isEqualTo(1);
            }
        } finally {
            shutdown(pool);
        }

        Integer voteRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_close_vote WHERE field_session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(voteRows).isEqualTo(1);
        assertThat(sessionStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void 마지막_두_표_동시_요청도_세션_종료와_이벤트는_한_번이다() throws Exception {
        seed(2, false, false);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<FieldVisitCloseVoteService.VoteResult> first = pool.submit(() -> {
                await(ready, start);
                return closeVoteService.vote(studyId, memberIds.get(0));
            });
            Future<FieldVisitCloseVoteService.VoteResult> second = pool.submit(() -> {
                await(ready, start);
                return closeVoteService.vote(studyId, memberIds.get(1));
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            shutdown(pool);
        }

        assertThat(sessionStatus()).isEqualTo("ENDED");
        assertThat(sessionEndReason()).isEqualTo("MAJORITY_FORCED");
        verify(reportRequestPort, times(1)).request(any());
    }

    @Test
    void 투표와_리더_close_경합_시_종료와_이벤트는_한_번이다() throws Exception {
        seed(3, false, false);
        closeVoteService.vote(studyId, memberIds.get(1));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> voteFuture = pool.submit(() -> {
                await(ready, start);
                closeVoteService.vote(studyId, memberIds.get(2));
            });
            Future<?> closeFuture = pool.submit(() -> {
                await(ready, start);
                closeService.close(
                        studyId,
                        leaderId,
                        new FieldVisitCloseRequestBody(
                                true,
                                UUID.randomUUID().toString()
                        )
                );
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            voteFuture.get(30, TimeUnit.SECONDS);
            closeFuture.get(30, TimeUnit.SECONDS);
        } finally {
            shutdown(pool);
        }

        assertThat(sessionStatus()).isEqualTo("ENDED");
        verify(reportRequestPort, times(1)).request(any());
    }

    @Test
    void 투표와_마지막_개인_finish_경합_시_이벤트는_한_번이다() throws Exception {
        seed(2, false, false);
        finishService.finish(
                studyId,
                memberIds.get(0),
                new FieldVisitFinishRequestBody(true, UUID.randomUUID().toString())
        );
        closeVoteService.vote(studyId, memberIds.get(0));
        assertThat(sessionStatus()).isEqualTo("IN_PROGRESS");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> voteFuture = pool.submit(() -> {
                await(ready, start);
                closeVoteService.vote(studyId, memberIds.get(1));
            });
            Future<?> finishFuture = pool.submit(() -> {
                await(ready, start);
                finishService.finish(
                        studyId,
                        memberIds.get(1),
                        new FieldVisitFinishRequestBody(
                                true,
                                UUID.randomUUID().toString()
                        )
                );
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            voteFuture.get(30, TimeUnit.SECONDS);
            finishFuture.get(30, TimeUnit.SECONDS);
        } finally {
            shutdown(pool);
        }

        assertThat(sessionStatus()).isEqualTo("ENDED");
        verify(reportRequestPort, times(1)).request(any());
    }

    @Test
    void 과반수_종료_후_후발_start는_거부된다() {
        seed(1, false, true);
        FieldVisitCloseVoteService.VoteResult vote =
                closeVoteService.vote(studyId, memberIds.get(0));
        assertThat(vote.body().sessionEnded()).isTrue();

        assertThatThrownBy(() -> startService.start(
                studyId,
                lateMemberId,
                new FieldVisitStartRequestBody(
                        APT_LAT,
                        APT_LNG,
                        UUID.randomUUID().toString()
                )
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void 투표와_후발_start_동시_요청은_허용된_두_결과_중_하나로_수렴한다()
            throws Exception {
        seed(1, false, true);
        clearInvocations(reportRequestPort);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<Object> voteFuture;
        Future<Object> startFuture;
        try {
            voteFuture = pool.submit(() -> {
                await(ready, start);
                return closeVoteService.vote(studyId, memberIds.get(0));
            });
            startFuture = pool.submit(() -> {
                await(ready, start);
                try {
                    return startService.start(
                            studyId,
                            lateMemberId,
                            new FieldVisitStartRequestBody(
                                    APT_LAT,
                                    APT_LNG,
                                    UUID.randomUUID().toString()
                            )
                    );
                } catch (BusinessException exception) {
                    return exception.getErrorCode();
                }
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            voteFuture.get(30, TimeUnit.SECONDS);
            startFuture.get(30, TimeUnit.SECONDS);
        } finally {
            shutdown(pool);
        }

        Integer participantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_participant WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        Integer voteCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_close_vote "
                        + "WHERE field_session_id = ?",
                Integer.class,
                sessionId
        );
        boolean lateStarted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_participant "
                        + "WHERE session_id = ? AND member_id = ?",
                Integer.class,
                sessionId,
                lateMemberId
        ) == 1;

        if ("ENDED".equals(sessionStatus())) {
            assertThat(sessionEndReason()).isEqualTo("MAJORITY_FORCED");
            assertThat(participantCount).isEqualTo(1);
            assertThat(voteCount).isEqualTo(1);
            assertThat(lateStarted).isFalse();
            assertThat(startFuture.get()).isEqualTo(ErrorCode.FIELD_VISIT_ALREADY_ENDED);
            FieldVisitCloseVoteService.VoteResult voteResult =
                    (FieldVisitCloseVoteService.VoteResult) voteFuture.get();
            assertThat(voteResult.body().startedParticipantCount()).isEqualTo(1);
            assertThat(voteResult.body().requiredVoteCount()).isEqualTo(1);
            assertThat(voteResult.body().sessionEnded()).isTrue();
            verify(reportRequestPort, times(1)).request(any());
        } else {
            assertThat(sessionStatus()).isEqualTo("IN_PROGRESS");
            assertThat(participantCount).isEqualTo(2);
            assertThat(voteCount).isEqualTo(1);
            assertThat(lateStarted).isTrue();
            FieldVisitCloseVoteService.VoteResult voteResult =
                    (FieldVisitCloseVoteService.VoteResult) voteFuture.get();
            assertThat(voteResult.body().startedParticipantCount()).isEqualTo(2);
            assertThat(voteResult.body().requiredVoteCount()).isEqualTo(2);
            assertThat(voteResult.body().voteCount()).isEqualTo(1);
            assertThat(voteResult.body().sessionEnded()).isFalse();
            verify(reportRequestPort, times(0)).request(any());
        }
    }

    @Test
    void 상태_조회에_closeVote가_반영된다() {
        seed(3, false, false);
        closeVoteService.vote(studyId, memberIds.get(0));

        var status = statusService.getStatus(studyId, memberIds.get(0)).body();
        assertThat(status.closeVote().startedParticipantCount()).isEqualTo(3);
        assertThat(status.closeVote().voteCount()).isEqualTo(1);
        assertThat(status.closeVote().requiredVoteCount()).isEqualTo(2);
        assertThat(status.closeVote().hasVoted()).isTrue();
        assertThat(status.closeVote().canVote()).isFalse();

        var other = statusService.getStatus(studyId, memberIds.get(1)).body();
        assertThat(other.closeVote().hasVoted()).isFalse();
        assertThat(other.closeVote().canVote()).isTrue();
    }

    @Test
    void 이미_종료된_세션_재투표는_추가_저장_없이_상태를_반환한다() {
        seed(1, false, false);
        closeVoteService.vote(studyId, memberIds.get(0));

        FieldVisitCloseVoteService.VoteResult again =
                closeVoteService.vote(studyId, memberIds.get(0));
        assertThat(again.responseCode()).isEqualTo(
                com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode
                        .FIELD_VISIT_ALREADY_CLOSED
        );
        assertThat(again.body().sessionEnded()).isTrue();
        Integer voteRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM field_visit_close_vote WHERE field_session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(voteRows).isEqualTo(1);
        verify(reportRequestPort, times(1)).request(any());
    }

    private void seed(
            int participantCount,
            boolean leaderAlreadyEnded,
            boolean withLateMember
    ) {
        apartmentId = jdbcTemplate.queryForObject("""
                INSERT INTO apartment (complex_code, name, longitude, latitude)
                VALUES (?, 'BE0181-APT', ?, ?) RETURNING id
                """, Long.class, "BE0181-" + UUID.randomUUID(), APT_LNG, APT_LAT);
        memberIds = new ArrayList<>();
        for (int i = 0; i < participantCount; i++) {
            memberIds.add(insertMember("be0181-member-" + i));
        }
        leaderId = memberIds.get(0);
        studyId = jdbcTemplate.queryForObject("""
                INSERT INTO study (
                    apartment_id, leader_id, goal, capacity, title, status
                ) VALUES (
                    ?, ?, 'be0181-goal', 8, 'be0181-study', 'IN_PROGRESS'
                ) RETURNING id
                """, Long.class, apartmentId, leaderId);

        for (int i = 0; i < memberIds.size(); i++) {
            jdbcTemplate.update("""
                    INSERT INTO study_member (
                        study_id, member_id, role, status
                    ) VALUES (?, ?, ?, 'ACTIVE')
                    """,
                    studyId,
                    memberIds.get(i),
                    i == 0 ? "LEADER" : "MEMBER"
            );
        }

        if (withLateMember) {
            lateMemberId = insertMember("be0181-late");
            jdbcTemplate.update("""
                    INSERT INTO study_member (
                        study_id, member_id, role, status
                    ) VALUES (?, ?, 'MEMBER', 'ACTIVE')
                    """, studyId, lateMemberId);
            Instant startAt = Instant.now().minusSeconds(3600);
            jdbcTemplate.update("""
                    INSERT INTO schedule (
                        study_id, start_at, end_at, meeting_place
                    ) VALUES (?, ?, ?, 'gate')
                    """,
                    studyId,
                    Timestamp.from(startAt),
                    Timestamp.from(startAt.plusSeconds(7200))
            );
        }

        Instant startedAt = Instant.now().minusSeconds(3600);
        sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO field_session (study_id, status, started_at)
                VALUES (?, 'IN_PROGRESS', ?) RETURNING id
                """, Long.class, studyId, Timestamp.from(startedAt));
        for (Long memberId : memberIds) {
            jdbcTemplate.update("""
                    INSERT INTO field_participant (
                        session_id, member_id, status, started_at
                    ) VALUES (?, ?, 'IN_PROGRESS', ?)
                    """, sessionId, memberId, Timestamp.from(startedAt));
        }
        if (withLateMember) {
            for (Long memberId : memberIds) {
                jdbcTemplate.update("""
                        INSERT INTO field_visit_candidate (session_id, member_id)
                        VALUES (?, ?)
                        """, sessionId, memberId);
            }
            jdbcTemplate.update("""
                    INSERT INTO field_visit_candidate (session_id, member_id)
                    VALUES (?, ?)
                    """, sessionId, lateMemberId);
        }
        if (leaderAlreadyEnded) {
            jdbcTemplate.update("""
                    UPDATE field_participant
                    SET status = 'ENDED',
                        ended_at = ?,
                        end_reason = 'SELF_ENDED',
                        stay_duration_sec = 3590
                    WHERE session_id = ? AND member_id = ?
                    """,
                    Timestamp.from(Instant.now().minusSeconds(10)),
                    sessionId,
                    leaderId
            );
        }
    }

    private Long insertMember(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (
                    email, nickname, age_group_public_agreed,
                    service_notification_agreed, ad_notification_agreed
                ) VALUES (?, ?, false, true, false) RETURNING id
                """,
                Long.class,
                prefix + "-" + suffix + "@test.local",
                prefix + suffix
        );
    }

    private String sessionStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM field_session WHERE id = ?",
                String.class,
                sessionId
        );
    }

    private String sessionEndReason() {
        return jdbcTemplate.queryForObject(
                "SELECT end_reason FROM field_session WHERE id = ?",
                String.class,
                sessionId
        );
    }

    private static void await(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("start latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static void shutdown(ExecutorService pool) throws Exception {
        pool.shutdownNow();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("executor did not terminate");
        }
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM report");
        jdbcTemplate.update("DELETE FROM field_visit_close_vote");
        jdbcTemplate.update("DELETE FROM field_visit_start_request");
        jdbcTemplate.update("DELETE FROM field_record");
        jdbcTemplate.update("DELETE FROM checklist_answer");
        jdbcTemplate.update("DELETE FROM checklist_item");
        jdbcTemplate.update("DELETE FROM checklist");
        jdbcTemplate.update("DELETE FROM field_participant");
        jdbcTemplate.update("DELETE FROM field_visit_candidate");
        jdbcTemplate.update("DELETE FROM field_session");
        jdbcTemplate.update("DELETE FROM schedule WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be0181-study')");
        jdbcTemplate.update("DELETE FROM study_member WHERE study_id IN "
                + "(SELECT id FROM study WHERE title = 'be0181-study')");
        jdbcTemplate.update("DELETE FROM study WHERE title = 'be0181-study'");
        jdbcTemplate.update("DELETE FROM apartment WHERE name = 'BE0181-APT'");
        jdbcTemplate.update("DELETE FROM member WHERE email LIKE 'be0181-%@test.local'");
    }
}
