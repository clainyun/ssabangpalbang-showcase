package com.ssafy.ssabangpalbang.chat.domain;

/**
 * 채팅 메시지 유형이다.
 *
 * <p>{@code TEXT}, {@code IMAGE}는 클라이언트가 SEND로 발행할 수 있다.
 * {@code SYSTEM}은 서버 내부 이벤트로만 생성되며 클라이언트 SEND는 차단한다.</p>
 */
public enum MessageType {
    TEXT,
    IMAGE,
    SYSTEM
}
