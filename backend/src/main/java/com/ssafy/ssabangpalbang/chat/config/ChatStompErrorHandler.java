package com.ssafy.ssabangpalbang.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.chat.dto.ChatErrorPayload;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * ChannelInterceptor(CONNECT·SUBSCRIBE·SEND 권한 검사)에서 던진 BusinessException을
 * {code, message} JSON 본문을 가진 STOMP ERROR 프레임으로 변환한다.
 *
 * <p>STOMP 규약상 ERROR 프레임 전송 후 연결은 종료된다. BusinessException이 아닌
 * 예외는 기본 동작(StompSubProtocolErrorHandler)에 위임한다.</p>
 *
 * <p>@MessageMapping·Service 내부 검증 오류는 이 핸들러의 대상이 아니다.
 * 그 오류는 연결을 끊지 않고 {@link com.ssafy.ssabangpalbang.chat.controller.ChatMessageExceptionHandler}가
 * /user/queue/errors로 해당 사용자에게만 전달한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatStompErrorHandler extends StompSubProtocolErrorHandler {

    private static final String ERROR_CODE_HEADER = "errorCode";

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage,
            Throwable ex
    ) {
        ErrorCode errorCode = resolveErrorCode(ex);
        if (errorCode == null) {
            return super.handleClientMessageProcessingError(clientMessage, ex);
        }

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(errorCode.getMessage());
        accessor.setNativeHeader(ERROR_CODE_HEADER, errorCode.getCode());
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setLeaveMutable(true);

        StompHeaderAccessor clientHeaderAccessor = null;
        if (clientMessage != null) {
            clientHeaderAccessor =
                    MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
            if (clientHeaderAccessor != null) {
                String receiptId = clientHeaderAccessor.getReceipt();
                if (receiptId != null) {
                    accessor.setReceiptId(receiptId);
                }
            }
        }

        return handleInternal(accessor, toPayload(errorCode), ex, clientHeaderAccessor);
    }

    private byte[] toPayload(ErrorCode errorCode) {
        try {
            return objectMapper.writeValueAsBytes(
                    new ChatErrorPayload(errorCode.getCode(), errorCode.getMessage())
            );
        } catch (Exception exception) {
            return new byte[0];
        }
    }

    private ErrorCode resolveErrorCode(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof BusinessException businessException) {
            return businessException.getErrorCode();
        }
        return null;
    }
}
