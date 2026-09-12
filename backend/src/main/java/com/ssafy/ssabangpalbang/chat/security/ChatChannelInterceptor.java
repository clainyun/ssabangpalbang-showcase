package com.ssafy.ssabangpalbang.chat.security;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.chat.port.StudyMembershipPort;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * STOMP CONNECT 인증과 SUBSCRIBE·SEND 권한을 검사하는 ChannelInterceptor다.
 *
 * <p>여기서 던지는 예외는 클라이언트 인바운드 채널을 통해 전파되어
 * {@link com.ssafy.ssabangpalbang.chat.config.ChatStompErrorHandler}가
 * STOMP ERROR 프레임으로 변환한다(연결은 STOMP 규약에 따라 종료된다).</p>
 *
 * <p>@MessageMapping 내부에서 발생하는 검증 오류(예: TEXT 본문 검증)는
 * 이 인터셉터의 책임이 아니며, 이후 커밋에서 @MessageExceptionHandler로
 * /user/queue/errors로 별도 처리한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ChatChannelInterceptor.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_DESTINATION_PREFIX = "/user/";

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final StudyMembershipPort studyMembershipPort;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> handleConnect(accessor);
            case SUBSCRIBE -> handleSubscribe(accessor);
            case SEND -> handleSend(accessor);
            default -> {
                // STOMP CONNECT·SUBSCRIBE·SEND 외의 프레임은 이 커밋의 검사 대상이 아니다.
            }
        }
        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        try {
            String accessToken = extractAccessToken(
                    accessor.getFirstNativeHeader(AUTHORIZATION_HEADER)
            );
            Long memberId = jwtTokenProvider.parseAccessToken(accessToken);

            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.CHAT_CONNECTION_UNAUTHORIZED
                    ));
            if (member.getStatus() != MemberStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.CHAT_CONNECTION_UNAUTHORIZED);
            }

            accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(
                    new AuthenticatedMember(memberId),
                    null,
                    List.of()
            ));
            log.debug(
                    "event=chat_stomp_connect_accepted memberId={} sessionId={}",
                    memberId,
                    sessionId
            );
        } catch (BusinessException exception) {
            log.warn(
                    "event=chat_stomp_connect_rejected sessionId={} errorCode={}",
                    sessionId,
                    exception.getErrorCode().getCode()
            );
            // 토큰 만료·형식 오류 등 원인과 무관하게 CONNECT 인증 실패는 단일 계약으로 통일한다.
            throw new BusinessException(ErrorCode.CHAT_CONNECTION_UNAUTHORIZED);
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        if (destination == null) {
            log.warn(
                    "event=chat_stomp_subscribe_rejected sessionId={} destination=null errorCode={}",
                    sessionId,
                    ErrorCode.CHAT_FORBIDDEN.getCode()
            );
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        if (destination.startsWith(USER_DESTINATION_PREFIX)) {
            // 회원별 개인 큐(예: /user/queue/errors) 구독은 채팅 권한 검사 대상이 아니다.
            return;
        }

        Long studyId = ChatDestinationParser.parseChatSubscribeStudyId(destination);
        Long memberId = resolveMemberId(accessor);

        if (!studyMembershipPort.isStudyMember(studyId, memberId)) {
            log.warn(
                    "event=chat_stomp_subscribe_rejected studyId={} memberId={} sessionId={} destination={} errorCode={}",
                    studyId,
                    memberId,
                    sessionId,
                    destination,
                    ErrorCode.CHAT_FORBIDDEN.getCode()
            );
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        log.debug(
                "event=chat_stomp_subscribe_accepted studyId={} memberId={} sessionId={} destination={}",
                studyId,
                memberId,
                sessionId,
                destination
        );
    }

    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        Long studyId = ChatDestinationParser.parseChatSendStudyId(destination);
        Long memberId = resolveMemberId(accessor);

        if (!studyMembershipPort.isStudyMember(studyId, memberId)) {
            log.warn(
                    "event=chat_stomp_send_rejected studyId={} memberId={} sessionId={} destination={} errorCode={}",
                    studyId,
                    memberId,
                    sessionId,
                    destination,
                    ErrorCode.CHAT_FORBIDDEN.getCode()
            );
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        if (!studyMembershipPort.isStudyOpenForSend(studyId)) {
            log.warn(
                    "event=chat_stomp_send_rejected studyId={} memberId={} sessionId={} destination={} errorCode={}",
                    studyId,
                    memberId,
                    sessionId,
                    destination,
                    ErrorCode.CHAT_STUDY_COMPLETED.getCode()
            );
            throw new BusinessException(ErrorCode.CHAT_STUDY_COMPLETED);
        }
        log.debug(
                "event=chat_stomp_send_accepted studyId={} memberId={} sessionId={} destination={}",
                studyId,
                memberId,
                sessionId,
                destination
        );
    }

    private Long resolveMemberId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (!(user instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof AuthenticatedMember authenticatedMember)) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }
        return authenticatedMember.memberId();
    }

    private String extractAccessToken(String authorization) {
        if (authorization == null
                || authorization.length() < BEARER_PREFIX.length()
                || !authorization.regionMatches(
                        true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length()
                )) {
            throw new BusinessException(ErrorCode.CHAT_CONNECTION_UNAUTHORIZED);
        }
        return authorization.substring(BEARER_PREFIX.length());
    }
}
