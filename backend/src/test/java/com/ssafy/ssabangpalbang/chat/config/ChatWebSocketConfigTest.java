package com.ssafy.ssabangpalbang.chat.config;

import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 커밋 2 범위(CONNECT 인증)의 최소 통합 검증이다.
 *
 * <p>SUBSCRIBE·SEND 권한 분기와 회원 상태별 세부 분기는
 * {@link com.ssafy.ssabangpalbang.chat.security.ChatChannelInterceptorTest}에서
 * 단위 테스트로 충분히 검증한다. 이 통합 테스트는 실제 WebSocket 연결에서
 * 인터셉터·에러 핸들러가 올바르게 연결되어 있는지만 확인한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(statements = {
        "DROP TABLE IF EXISTS member",
        "CREATE TABLE member ("
                + "id BIGINT PRIMARY KEY, "
                + "email VARCHAR(255) NOT NULL, "
                + "password_hash VARCHAR(255), "
                + "nickname VARCHAR(50) NOT NULL, "
                + "profile_image_url VARCHAR(500), "
                + "selected_character_id VARCHAR(20) NOT NULL, "
                + "age_group VARCHAR(20), "
                + "age_group_public_agreed BOOLEAN NOT NULL, "
                + "service_notification_agreed BOOLEAN NOT NULL, "
                + "ad_notification_agreed BOOLEAN NOT NULL, "
                + "status VARCHAR(20) NOT NULL, "
                + "deleted_at TIMESTAMP WITH TIME ZONE, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL"
                + ")"
})
@Sql(
        statements = "DROP TABLE IF EXISTS member",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class ChatWebSocketConfigTest {

    private static final long CONNECT_TIMEOUT_SECONDS = 5;
    private static final Long ACTIVE_MEMBER_ID = 1L;

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler taskScheduler;
    private StompSession session;

    @BeforeEach
    void setUp() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.afterPropertiesSet();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setTaskScheduler(taskScheduler);
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }

    @Test
    void Authorization_헤더가_없으면_CONNECT에_실패한다() {
        CompletableFuture<StompSession> connectFuture = connect(null);

        assertThatThrownBy(() ->
                connectFuture.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(Exception.class);
    }

    @Test
    void 유효하지_않은_토큰이면_CONNECT에_실패한다() {
        CompletableFuture<StompSession> connectFuture =
                connect("Bearer invalid-token");

        assertThatThrownBy(() ->
                connectFuture.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(Exception.class);
    }

    @Test
    void 유효한_토큰과_ACTIVE_회원이면_CONNECT에_성공한다()
            throws ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
        insertActiveMember(ACTIVE_MEMBER_ID);
        String accessToken = jwtTokenProvider.issue(ACTIVE_MEMBER_ID).accessToken();

        CompletableFuture<StompSession> connectFuture = connect("Bearer " + accessToken);

        session = connectFuture.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(session.isConnected()).isTrue();
    }

    private CompletableFuture<StompSession> connect(String authorizationHeader) {
        StompHeaders connectHeaders = new StompHeaders();
        if (authorizationHeader != null) {
            connectHeaders.add("Authorization", authorizationHeader);
        }

        return stompClient.connectAsync(
                "ws://localhost:{port}/ws",
                (WebSocketHttpHeaders) null,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                },
                port
        );
    }

    private void insertActiveMember(Long memberId) {
        jdbcTemplate.update(
                "INSERT INTO member ("
                        + "id, email, nickname, selected_character_id, "
                        + "age_group_public_agreed, service_notification_agreed, "
                        + "ad_notification_agreed, status, created_at, updated_at"
                        + ") VALUES (?, ?, ?, 'PALBANG', FALSE, TRUE, FALSE, 'ACTIVE', "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                memberId, "chat-test-" + memberId + "@example.com",
                "chat-tester-" + memberId
        );
    }
}
