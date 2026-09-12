package com.ssafy.ssabangpalbang.chat.service;

import com.ssafy.ssabangpalbang.chat.domain.ChatMessage;
import com.ssafy.ssabangpalbang.chat.dto.ChatImageResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageBroadcastResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatSenderResponse;
import com.ssafy.ssabangpalbang.chat.event.ChatMessageBroadcastEvent;
import com.ssafy.ssabangpalbang.chat.event.ChatMessagePushRequestedEvent;
import com.ssafy.ssabangpalbang.chat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 메시지 저장 전용 Bean이다.
 *
 * <p>각 저장 메서드는 {@code REQUIRES_NEW}로 별도 트랜잭션에서 실행되며,
 * saveAndFlush로 (sender_id, client_message_id) partial unique index 위반을
 * 이 메서드 호출 시점에 즉시 드러낸다. 유일성 위반 예외는 이 트랜잭션 안에서
 * 잡지 않고 그대로 호출자(ChatMessageService)에게 전파해 롤백시킨다. 호출자는
 * 실패한 이 트랜잭션과 무관한 별도 트랜잭션(Repository 기본 동작)으로
 * 기존 메시지를 재조회해야 한다.</p>
 *
 * <p>저장이 성공하면 같은 트랜잭션 안에서 완성된 Broadcast Payload를 담아
 * {@link ChatMessageBroadcastEvent}를 발행한다. 이 트랜잭션이 커밋된 뒤에만
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}가 실시간 발행을 수행한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatMessageWriter {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageWriter.class);

    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessage saveText(
            Long studyId,
            Long senderId,
            String content,
            String clientMessageId,
            ChatSenderResponse sender
    ) {
        ChatMessage saved = chatMessageRepository.saveAndFlush(
                ChatMessage.createText(studyId, senderId, content, clientMessageId)
        );
        publish(saved, sender, null);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessage saveImage(
            Long studyId,
            Long senderId,
            Long imageFileId,
            String clientMessageId,
            ChatSenderResponse sender,
            ChatImageResponse image
    ) {
        ChatMessage saved = chatMessageRepository.saveAndFlush(
                ChatMessage.createImage(studyId, senderId, imageFileId, clientMessageId)
        );
        publish(saved, sender, image);
        return saved;
    }

    /** 서버 내부 SYSTEM 발행 진입점(일정 변경·멤버 변경 등)에서만 호출한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatMessage saveSystem(Long studyId, String content) {
        ChatMessage saved = chatMessageRepository.saveAndFlush(
                ChatMessage.createSystem(studyId, content)
        );
        publish(saved, null, null);
        return saved;
    }

    private void publish(ChatMessage saved, ChatSenderResponse sender, ChatImageResponse image) {
        log.info(
                "event=chat_message_saved messageId={} studyId={} senderId={} clientMessageId={} messageType={}",
                saved.getId(),
                saved.getStudyId(),
                saved.getSenderId(),
                saved.getClientMessageId(),
                saved.getMessageType()
        );
        ChatMessageBroadcastResponse payload = ChatMessageBroadcastResponse.from(
                saved,
                sender,
                image
        );
        eventPublisher.publishEvent(new ChatMessageBroadcastEvent(payload));
        if (sender != null) {
            eventPublisher.publishEvent(new ChatMessagePushRequestedEvent(
                    payload.messageId(),
                    payload.studyId(),
                    sender.memberId(),
                    sender.nickname(),
                    payload.messageType(),
                    payload.content()
            ));
        }
    }
}
