package com.ssafy.ssabangpalbang.chat.dto;

/**
 * PATCH /api/v1/studies/{studyId}/chat/read 요청 본문이다.
 * lastReadMessageId를 생략하면 현재 시각 기준으로 전체 읽음 처리한다.
 */
public record ChatReadRequest(Long lastReadMessageId) {
}
