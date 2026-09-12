package com.ssafy.ssabangpalbang.chat.config;

import com.ssafy.ssabangpalbang.chat.security.ChatChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 채팅 WebSocket·STOMP 연결 구성이다.
 *
 * <p>ChatChannelInterceptor가 CONNECT 인증과 SUBSCRIBE·SEND 권한을 검사하고,
 * ChatStompErrorHandler가 그 결과로 발생한 예외를 STOMP ERROR 프레임으로 변환한다.
 * @MessageMapping 내부 검증 오류는 /user/queue/errors로 해당 사용자에게만 전달되며,
 * 이를 위해 simple broker에 /queue 접두어도 함께 등록한다
 * (사용자별 큐 접두어는 Spring 기본값인 /user를 그대로 사용한다).</p>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String STOMP_ENDPOINT = "/ws";
    private static final String APPLICATION_DESTINATION_PREFIX = "/pub";
    private static final String BROKER_TOPIC_PREFIX = "/sub";
    private static final String BROKER_QUEUE_PREFIX = "/queue";

    private final ChatChannelInterceptor chatChannelInterceptor;
    private final ChatStompErrorHandler chatStompErrorHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(STOMP_ENDPOINT);
        registry.setErrorHandler(chatStompErrorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(APPLICATION_DESTINATION_PREFIX);
        registry.enableSimpleBroker(BROKER_TOPIC_PREFIX, BROKER_QUEUE_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatChannelInterceptor);
    }
}
