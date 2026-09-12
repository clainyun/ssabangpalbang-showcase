package com.ssafy.ssabangpalbang.chat.event;

import com.ssafy.ssabangpalbang.chat.dto.ChatMessageBroadcastResponse;

/**
 * 채팅 메시지 저장 트랜잭션이 커밋된 뒤에만 처리해야 하는 실시간 발행 이벤트다.
 * payload는 이미 완성된 응답이므로 Listener는 추가 DB 조회 없이 그대로 발행한다.
 */
public record ChatMessageBroadcastEvent(ChatMessageBroadcastResponse payload) {
}
