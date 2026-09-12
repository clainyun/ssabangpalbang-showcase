package com.ssafy.ssabangpalbang.chat.controller;

import com.ssafy.ssabangpalbang.chat.dto.ChatErrorPayload;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * @MessageMapping(ChatMessageController) 내부에서 발생한 예외를 잡아
 * 해당 요청을 보낸 WebSocket 세션에만(broadcast = false) /user/queue/errors로 전달한다.
 *
 * <p>ChannelInterceptor의 CONNECT·SUBSCRIBE·SEND 권한 오류는 이 핸들러의 대상이 아니며,
 * {@link com.ssafy.ssabangpalbang.chat.config.ChatStompErrorHandler}가 STOMP ERROR
 * 프레임으로 처리한다(연결이 끊긴다). 이 핸들러가 처리하는 오류는 연결을 끊지 않는다.</p>
 */
@ControllerAdvice
public class ChatMessageExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageExceptionHandler.class);

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ChatErrorPayload handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return new ChatErrorPayload(errorCode.getCode(), errorCode.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ChatErrorPayload handleUnexpected(Exception exception) {
        log.error("채팅 메시지 처리 중 처리되지 않은 오류가 발생했습니다.", exception);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return new ChatErrorPayload(errorCode.getCode(), errorCode.getMessage());
    }
}
