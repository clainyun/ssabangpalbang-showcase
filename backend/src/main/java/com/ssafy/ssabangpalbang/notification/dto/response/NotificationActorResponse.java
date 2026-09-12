package com.ssafy.ssabangpalbang.notification.dto.response;

public record NotificationActorResponse(
        Long memberId,
        String nickname,
        String profileImageUrl,
        String selectedCharacterId
) {
}
