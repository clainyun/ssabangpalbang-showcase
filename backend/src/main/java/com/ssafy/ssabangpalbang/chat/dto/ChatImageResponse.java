package com.ssafy.ssabangpalbang.chat.dto;

/**
 * 채팅 IMAGE 메시지의 이미지 정보다. imageUrl은 Presigned GET URL이며
 * S3 기능이 비활성화된 상태에서 과거 데이터를 조회하는 경우에는 null일 수 있다.
 */
public record ChatImageResponse(
        Long fileId,
        String imageUrl,
        String uploadStatus
) {
}
