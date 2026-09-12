package com.ssafy.ssabangpalbang.chat.event;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 채팅 메시지 저장 트랜잭션이 AFTER_COMMIT된 뒤에만 실시간 발행을 수행한다.
 *
 * <p>payload는 트랜잭션 안에서 이미 완성되었으므로 여기서는 DB 조회나
 * Lazy Loading 없이 {@link SimpMessagingTemplate#convertAndSend}만 호출한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatMessageBroadcastListener {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageBroadcastListener.class);

    private static final String DESTINATION_TEMPLATE = "/sub/studies/%d/chat";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageBroadcast(ChatMessageBroadcastEvent event) {
        String destination = DESTINATION_TEMPLATE.formatted(event.payload().studyId());
        try {
            messagingTemplate.convertAndSend(destination, event.payload());
            log.info(
                    "event=chat_message_broadcast messageId={} studyId={} destination={} messageType={}",
                    event.payload().messageId(),
                    event.payload().studyId(),
                    destination,
                    event.payload().messageType()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "event=chat_message_broadcast_failed messageId={} studyId={} destination={} exceptionClass={}",
                    event.payload().messageId(),
                    event.payload().studyId(),
                    destination,
                    exception.getClass().getName(),
                    exception
            );
            throw exception;
        }
    }
}
