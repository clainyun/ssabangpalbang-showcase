package com.ssafy.ssabangpalbang.chat.dto;

/**
 * STOMP ERROR 프레임 본문과 /user/queue/errors 오류 응답이 공통으로 사용하는 형태다.
 */
public record ChatErrorPayload(String code, String message) {
}
